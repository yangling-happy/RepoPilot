package com.repopilot.business.service.workspace;

import com.repopilot.business.config.UserWorkspaceProperties;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
public class UserWorkspaceResolver {

    private final UserWorkspaceProperties properties;

    public Path userWorkspace(String gitlabUsername) {
        Path baseRoot = baseRoot();
        Path workspace = baseRoot.resolve("workspace").resolve(safeUsername(gitlabUsername)).normalize();
        if (!workspace.startsWith(baseRoot)) {
            throw new BusinessException(400, "Invalid user workspace path");
        }
        return workspace;
    }

    public Path repoRoot(String gitlabUsername) {
        return userWorkspace(gitlabUsername).resolve("repos").normalize();
    }

    public Path repoPath(String gitlabUsername, Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(400, "projectId must be greater than 0");
        }
        return resolveUnder(repoRoot(gitlabUsername), "project-" + projectId);
    }

    public Path repoPath(String gitlabUsername, String project) {
        if (!StringUtils.hasText(project) || !project.trim().matches("\\d+")) {
            throw new BusinessException(400, "project must be a numeric GitLab project id");
        }
        return repoPath(gitlabUsername, Long.valueOf(project.trim()));
    }

    public Path docOutputRoot(String gitlabUsername) {
        return userWorkspace(gitlabUsername).resolve("docs").normalize();
    }

    public String safeUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            throw new BusinessException(400, "GitLab username is required");
        }
        String safe = gitlabUsername.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!StringUtils.hasText(safe)) {
            throw new BusinessException(400, "GitLab username is invalid");
        }
        return safe;
    }

    private Path baseRoot() {
        String baseDir = StringUtils.hasText(properties.getBaseDir()) ? properties.getBaseDir() : ".";
        return Paths.get(baseDir).toAbsolutePath().normalize();
    }

    private Path resolveUnder(Path root, String segment) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(segment).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new BusinessException(400, "Invalid workspace path");
        }
        return resolved;
    }
}
