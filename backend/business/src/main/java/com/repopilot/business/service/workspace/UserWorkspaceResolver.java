package com.repopilot.business.service.workspace;

import com.repopilot.business.config.UserWorkspaceProperties;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

//用户工作空间路径解析器
//作用：根据 GitLab 用户名和项目 ID，计算出仓库在本地文件系统中的存放路径
//目录结构：{baseDir}/workspace/{username}/repos/project-{projectId}
@Component
@RequiredArgsConstructor
public class UserWorkspaceResolver {

    //工作空间基础目录配置（从 application.yml 读取）
    private final UserWorkspaceProperties properties;

    //获取用户的根工作空间目录：{baseDir}/workspace/{username}
    public Path userWorkspace(String gitlabUsername) {
        Path baseRoot = baseRoot();
        Path workspace = baseRoot.resolve("workspace").resolve(safeUsername(gitlabUsername)).normalize();
        //安全检查：防止路径穿越攻击（如 username 传入 "../../etc"）
        if (!workspace.startsWith(baseRoot)) {
            throw new BusinessException(400, "Invalid user workspace path");
        }
        return workspace;
    }

    //获取用户所有仓库的根目录：{baseDir}/workspace/{username}/repos
    public Path repoRoot(String gitlabUsername) {
        return userWorkspace(gitlabUsername).resolve("repos").normalize();
    }

    //根据项目 ID 获取具体仓库的路径：{baseDir}/workspace/{username}/repos/project-{projectId}
    public Path repoPath(String gitlabUsername, Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(400, "projectId must be greater than 0");
        }
        return resolveUnder(repoRoot(gitlabUsername), "project-" + projectId);
    }

    //重载方法：接受字符串类型的项目 ID（从 URL 参数传来的是字符串）
    public Path repoPath(String gitlabUsername, String project) {
        if (!StringUtils.hasText(project) || !project.trim().matches("\\d+")) {
            throw new BusinessException(400, "project must be a numeric GitLab project id");
        }
        return repoPath(gitlabUsername, Long.valueOf(project.trim()));
    }

    //获取文档输出根目录：{baseDir}/workspace/{username}/docs
    public Path docOutputRoot(String gitlabUsername) {
        return userWorkspace(gitlabUsername).resolve("docs").normalize();
    }

    //将用户名中的特殊字符替换为下划线，防止文件系统路径注入
    public String safeUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            throw new BusinessException(400, "GitLab username is required");
        }
        //只保留字母、数字、点、下划线、横线，其他字符全部替换为下划线
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
