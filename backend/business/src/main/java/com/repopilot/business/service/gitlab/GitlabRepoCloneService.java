package com.repopilot.business.service.gitlab;

import com.repopilot.business.config.RepoCloneProperties;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.service.terminal.TerminalRelayClient;
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
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitlabRepoCloneService {

    private final RepoCloneProperties repoCloneProperties;
    private final ObjectMapper objectMapper;
    private final TerminalRelayClient terminalRelayClient;

    @Value("${gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    public CloneRepoResponse cloneByProjectId(Long projectId, String branch, String token, String terminalSessionId) {
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

        Path cloneRoot = Paths.get(repoCloneProperties.getRootDir()).toAbsolutePath().normalize();
        Path targetPath = cloneRoot.resolve("project-" + projectId).normalize();
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
                response.setProjectPath(project.pathWithNamespace);
                response.setBranch(effectiveBranch);
                response.setCloneUrl(cloneUrl);
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

    private record ProjectInfo(long id, String pathWithNamespace, String cloneUrl) {
    }

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