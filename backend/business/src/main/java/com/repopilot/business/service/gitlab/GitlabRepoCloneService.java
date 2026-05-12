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
        String effectiveBranch = StringUtils.hasText(branch) ? branch.trim() : repoCloneProperties.getDefaultBranch();
        if (!StringUtils.hasText(effectiveBranch)) {
            effectiveBranch = "main";
        }

        Path cloneRoot = userWorkspaceResolver.repoRoot(gitlabUsername);
        Path targetPath = userWorkspaceResolver.repoPath(gitlabUsername, projectId);
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
                emitTerminal(terminalSessionId, "[clone] failed, branch not found: " + effectiveBranch);
                throw new BusinessException(400, "Branch not found: " + effectiveBranch);
            } catch (TransportException e) {
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
        String cloneUrl = json.path("http_url_to_repo").asText(null);
        String pathWithNamespace = json.path("path_with_namespace").asText(null);
        long id = json.path("id").asLong(projectId);

        if (!StringUtils.hasText(cloneUrl)) {
            throw new BusinessException(500, "GitLab project clone url is empty");
        }

        return new ProjectInfo(id, pathWithNamespace, cloneUrl);
    }

    private String toBranchRef(String branch) {
        if (branch.startsWith("refs/heads/")) {
            return branch;
        }
        return "refs/heads/" + branch;
    }

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
