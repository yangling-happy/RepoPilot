package com.repopilot.business.service.gitlab;

import com.repopilot.business.config.RepoCloneProperties;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.service.terminal.TerminalRelayClient;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import com.repopilot.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

//Lombok: 自动生成日志对象 log
@Slf4j
//Spring: 标记为 Service 层 Bean
@Service
//Lombok: 为 final 字段生成构造函数
@RequiredArgsConstructor
public class GitlabRepoCloneService {

    //仓库克隆的配置属性（默认分支、超时时间等）
    private final RepoCloneProperties repoCloneProperties;
    //JSON 解析工具
    private final ObjectMapper objectMapper;
    //WebSocket 终端消息推送客户端，用于将克隆进度实时发送到前端
    private final TerminalRelayClient terminalRelayClient;
    //用户工作空间路径解析器，决定仓库克隆到哪个目录
    private final UserWorkspaceResolver userWorkspaceResolver;

    //GitLab API 地址（从配置文件读取，默认是 GitLab.com）
    @Value("${gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    //按 GitLab projectId 克隆仓库到当前用户的工作空间
    //
    //整体流程：
    //  1. 校验 projectId/token
    //  2. 决定实际使用的分支（请求分支 -> 配置默认分支 -> main）
    //  3. 计算本地目标目录，并做路径安全检查
    //  4. 调 GitLab API 读取项目 cloneUrl
    //  5. 用 JGit 执行 clone，并把进度推送到前端终端
    //  6. 返回本地路径和 HEAD commit
    public CloneRepoResponse cloneByProjectId(Long projectId,
                                              String branch,
                                              String token,
                                              String gitlabUsername,
                                              String terminalSessionId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(400, "projectId must be greater than 0");
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required in session");
        }

        String normalizedToken = token.trim();
        //如果前端没有指定分支，就使用配置里的默认分支；配置也没有时兜底 main
        String effectiveBranch = StringUtils.hasText(branch) ? branch.trim() : repoCloneProperties.getDefaultBranch();
        if (!StringUtils.hasText(effectiveBranch)) {
            effectiveBranch = "main";
        }

        //cloneRoot 是当前用户所有仓库的根目录
        //targetPath 是这个 GitLab 项目对应的具体本地目录
        Path cloneRoot = userWorkspaceResolver.repoRoot(gitlabUsername);
        Path targetPath = userWorkspaceResolver.repoPath(gitlabUsername, projectId);
        //路径安全检查：确保最终克隆目录一定落在用户自己的 repoRoot 下面
        if (!targetPath.startsWith(cloneRoot)) {
            throw new BusinessException(400, "Invalid clone target path");
        }

        emitTerminal(terminalSessionId, "[clone] request accepted, projectId=" + projectId + ", branch=" + effectiveBranch);

        try {
            Files.createDirectories(cloneRoot);
            if (Files.exists(targetPath)) {
                emitTerminal(terminalSessionId, "[clone] skipped, local directory already exists: " + targetPath);
                throw new BusinessException(409, "Local directory already exists: " + targetPath);
            }

            //先从 GitLab API 读取项目元信息，再用其中的 http_url_to_repo 执行 clone
            ProjectInfo project = fetchProject(projectId, normalizedToken);
            String cloneUrl = normalizeCloneUrl(project.cloneUrl);
            String branchRef = toBranchRef(effectiveBranch);

            emitTerminal(terminalSessionId, "[clone] start cloning " + project.pathWithNamespace + " -> " + targetPath);

            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(targetPath.toFile())
                    .setCloneAllBranches(false)
                    .setBranchesToClone(List.of(branchRef))
                    .setBranch(branchRef)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("git", normalizedToken))
                    .setProgressMonitor(new TerminalProgressMonitor(terminalSessionId))
                    .setTimeout(repoCloneProperties.getTimeoutSeconds());

            try (Git git = cloneCommand.call()) {
                String headCommit = git.getRepository().resolve("HEAD").name();

                //把这次克隆的关键信息返回给前端：
                //既包括 GitLab 侧项目路径，也包括本地工作空间路径和当前 commit
                CloneRepoResponse response = new CloneRepoResponse();
                response.setProjectId(project.id);
                response.setGitlabUsername(gitlabUsername);
                response.setProjectPath(project.pathWithNamespace);
                response.setBranch(effectiveBranch);
                response.setCloneUrl(cloneUrl);
                response.setWorkspacePath(userWorkspaceResolver.userWorkspace(gitlabUsername).toString());
                response.setLocalPath(targetPath.toString());
                response.setCommitId(headCommit);
                emitTerminal(terminalSessionId, "[clone] completed, HEAD=" + headCommit);
                return response;
            } catch (RefNotFoundException e) {
                //分支不存在属于用户输入问题，返回 400
                emitTerminal(terminalSessionId, "[clone] failed, branch not found: " + effectiveBranch);
                throw new BusinessException(400, "Branch not found: " + effectiveBranch);
            } catch (TransportException e) {
                //认证失败、权限不足、仓库不可访问通常都会落到 TransportException
                emitTerminal(terminalSessionId, "[clone] failed, invalid token or insufficient permission");
                throw new BusinessException(401, "Clone failed, please check token and repository permissions");
            }
        } catch (BusinessException e) {
            emitTerminal(terminalSessionId, "[clone] failed, code=" + e.getCode() + ", message=" + e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("Clone repo failed with IO error, projectId={}", projectId, e);
            emitTerminal(terminalSessionId, "[clone] failed, local file operation error");
            throw new BusinessException(500, "Local file operation failed during clone");
        } catch (Exception e) {
            log.error("Clone repo failed, projectId={}", projectId, e);
            emitTerminal(terminalSessionId, "[clone] failed, unexpected error: " + e.getMessage());
            throw new BusinessException(500, "Clone repository failed");
        }
    }

    //向 WebSocket 终端发送消息（如果 sessionId 不为空）
    private void emitTerminal(String sessionId, String line) {
        terminalRelayClient.emit(sessionId, line);
    }

    private ProjectInfo fetchProject(Long projectId, String token) throws IOException, InterruptedException {
        //配置中的 gitlab.api-url 可能以 / 结尾，这里统一去掉，避免拼接成双斜杠
        String apiBase = gitlabApiUrl.endsWith("/") ? gitlabApiUrl.substring(0, gitlabApiUrl.length() - 1)
                : gitlabApiUrl;
        URI uri = URI.create(apiBase + "/projects/" + projectId);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status == 404) {
            throw new BusinessException(404, "GitLab project not found: " + projectId);
        }
        if (status == 401 || status == 403) {
            throw new BusinessException(401, "Invalid token or insufficient permission");
        }
        if (status < 200 || status >= 300) {
            throw new BusinessException(500, "GitLab API error, status=" + status);
        }

        JsonNode json = objectMapper.readTree(response.body());
        //http_url_to_repo 是 GitLab 返回的 HTTP(S) 克隆地址
        //path_with_namespace 是 group/project 这种可读路径，用于返回前端展示
        String cloneUrl = json.path("http_url_to_repo").asText(null);
        String pathWithNamespace = json.path("path_with_namespace").asText(null);
        long id = json.path("id").asLong(projectId);

        if (!StringUtils.hasText(cloneUrl)) {
            throw new BusinessException(500, "GitLab project clone url is empty");
        }

        return new ProjectInfo(id, pathWithNamespace, cloneUrl);
    }

    private String toBranchRef(String branch) {
        //JGit clone 指定分支时需要完整 ref，例如 refs/heads/main
        //前端通常只传 main，所以这里统一补齐前缀
        if (branch.startsWith("refs/heads/")) {
            return branch;
        }
        return "refs/heads/" + branch;
    }

    //修正 GitLab 返回的 clone URL
    //
    //某些本地 GitLab/容器部署场景下，GitLab API 返回的 cloneUrl host 可能是 localhost，
    //而 business 服务所在进程无法通过这个 localhost 访问仓库。
    //如果 API 地址配置成了可访问的 host，这里把 cloneUrl 的 host 改成 api-url 的 host。
    private String normalizeCloneUrl(String cloneUrl) {
        URI cloneUri = URI.create(cloneUrl);
        URI apiUri = URI.create(gitlabApiUrl);

        if (!isLocalHost(cloneUri.getHost()) || isLocalHost(apiUri.getHost())) {
            return cloneUrl;
        }

        URI rewritten = URI.create(String.format("%s://%s%s%s",
                apiUri.getScheme(),
                apiUri.getAuthority(),
                cloneUri.getPath(),
                cloneUri.getQuery() == null ? "" : "?" + cloneUri.getQuery()));
        log.info("Rewrote clone url host from {} to {}", cloneUri.getAuthority(), apiUri.getAuthority());
        return rewritten.toString();
    }

    private boolean isLocalHost(String host) {
        //只把明确的本机地址当作 localhost，其他域名/IP 原样保留
        if (!StringUtils.hasText(host)) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    //内部 record，封装从 GitLab API 获取的项目基本信息
    private record ProjectInfo(long id, String pathWithNamespace, String cloneUrl) {
    }

    //JGit 的 ProgressMonitor 实现，将 Git 克隆进度推送到 WebSocket 终端
    //这样用户在前端可以实时看到克隆的进度（如 "Receiving objects 50%"）
    private class TerminalProgressMonitor implements ProgressMonitor {

        private final String sessionId;
        private String taskTitle = "";
        private int totalWork = UNKNOWN;
        private int completed;
        private int nextProgressMark = 10;

        private TerminalProgressMonitor(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void start(int totalTasks) {
            emitTerminal(sessionId, "[clone] progress started, tasks=" + totalTasks);
        }

        @Override
        public void beginTask(String title, int totalWork) {
            this.taskTitle = title;
            this.totalWork = totalWork;
            this.completed = 0;
            this.nextProgressMark = 10;
            emitTerminal(sessionId, "[clone] " + title + " started");
        }

        @Override
        public void update(int completed) {
            if (totalWork <= 0 || totalWork == UNKNOWN) {
                return;
            }
            this.completed += completed;
            //JGit 会频繁回调 update，如果每次都推送前端会刷屏
            //这里按 10% 为粒度推送，既能看到进度，又不会产生太多消息
            int progress = (int) Math.min(100, Math.round(this.completed * 100.0 / totalWork));
            while (progress >= nextProgressMark && nextProgressMark <= 100) {
                emitTerminal(sessionId, "[clone] " + taskTitle + " " + nextProgressMark + "%");
                nextProgressMark += 10;
            }
        }

        @Override
        public void endTask() {
            emitTerminal(sessionId, "[clone] " + taskTitle + " finished");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void showDuration(boolean enabled) {
            // no-op
        }
    }
}
