package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.config.RepoCloneProperties;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.service.terminal.TerminalRelayClient;
import com.repopilot.business.service.terminal.TerminalScriptTaskClient;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import com.repopilot.common.exception.BusinessException;
import com.repopilot.common.terminal.ScriptTaskRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitlabRepoCloneService {

    private final RepoCloneProperties repoCloneProperties;
    private final ObjectMapper objectMapper;
    private final TerminalRelayClient terminalRelayClient;
    private final TerminalScriptTaskClient terminalScriptTaskClient;
    private final UserWorkspaceResolver userWorkspaceResolver;

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

        emitTerminal(terminalSessionId,
                "[clone] request accepted, projectId=" + projectId + ", branch=" + effectiveBranch);

        try {
            Files.createDirectories(cloneRoot);
            ProjectInfo project = fetchProject(projectId, normalizedToken);
            String cloneUrl = normalizeCloneUrl(project.cloneUrl);

            emitTerminal(terminalSessionId,
                    "[clone] start cloning " + project.pathWithNamespace + " -> " + targetPath);

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("projectId", String.valueOf(projectId));
            args.put("branch", effectiveBranch);
            args.put("username", gitlabUsername);
            args.put("repoUrl", cloneUrl);
            args.put("targetDir", toScriptPath(targetPath));

            ScriptTaskRunResult scriptResult = terminalScriptTaskClient.run(
                    "CLONE_REPO",
                    terminalSessionId,
                    args,
                    Map.of("GITLAB_TOKEN", normalizedToken),
                    repoCloneProperties.getTimeoutSeconds());
            if (scriptResult.getExitCode() != 0) {
                throw toCloneScriptException(scriptResult);
            }

            String headCommit = terminalScriptTaskClient.requireResult(
                    scriptResult,
                    "HEAD",
                    "Clone script did not report HEAD");

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
        } catch (BusinessException e) {
            emitTerminal(terminalSessionId, "[clone] failed, code=" + e.getCode() + ", message=" + e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("Clone repo failed with IO error, projectId={}", projectId, e);
            emitTerminal(terminalSessionId, "[clone] failed, local file operation error");
            throw new BusinessException(500, "Local file operation failed during clone");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitTerminal(terminalSessionId, "[clone] failed, interrupted");
            throw new BusinessException(500, "Clone repository interrupted");
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

    private String toScriptPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private BusinessException toCloneScriptException(ScriptTaskRunResult result) {
        if (result.isTimedOut()) {
            return new BusinessException(500, "Clone repository timed out");
        }
        String output = summarizeScriptOutput(result);
        String lower = output.toLowerCase();
        if (lower.contains("authentication") || lower.contains("permission denied")
                || lower.contains("could not read username") || lower.contains("access denied")) {
            return new BusinessException(401, "Clone failed, please check token and repository permissions");
        }
        if (lower.contains("remote branch") && lower.contains("not found")) {
            return new BusinessException(400, "Branch not found");
        }
        return new BusinessException(500, "Clone repository failed: " + output);
    }

    private String summarizeScriptOutput(ScriptTaskRunResult result) {
        String text = ((result.getStderr() == null ? "" : result.getStderr()) + "\n"
                + (result.getStdout() == null ? "" : result.getStdout()))
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(text)) {
            return "exitCode=" + result.getExitCode();
        }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private record ProjectInfo(long id, String pathWithNamespace, String cloneUrl) {
    }
}
