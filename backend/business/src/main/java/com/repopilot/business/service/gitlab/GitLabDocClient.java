package com.repopilot.business.service.gitlab;

import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Branch;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.RepositoryFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// 文档流水线使用的 GitLab API 轻量客户端，负责 commit 与文件数据读取。
@Slf4j
@Component
public class GitLabDocClient {

    @Value("${gitlab.api-url}")
    private String apiUrl;

    // 获取分支 head commit id。
    public String getHeadCommit(String token, String project, String branch) {
        try {
            GitLabApi gitLabApi = buildApi(token);
            Branch branchInfo = gitLabApi.getRepositoryApi().getBranch(project, branch);
            if (branchInfo == null || branchInfo.getCommit() == null || !StringUtils.hasText(branchInfo.getCommit().getId())) {
                throw new BusinessException(500, "Unable to resolve head commit for project/branch");
            }
            return branchInfo.getCommit().getId();
        } catch (GitLabApiException e) {
            throw new BusinessException(500, "Failed to load branch head from GitLab: " + e.getMessage());
        }
    }

    // 按时间顺序列出 baseline 到 head 之间的 commit id。
    public List<String> listCommitIdsSince(String token, String project, String baselineCommit, String headCommit) {
        if (!StringUtils.hasText(baselineCommit)) {
            return List.of(headCommit);
        }
        if (Objects.equals(baselineCommit, headCommit)) {
            return List.of();
        }

        try {
            GitLabApi gitLabApi = buildApi(token);
            CompareResults compareResults = gitLabApi.getRepositoryApi().compare(project, baselineCommit, headCommit);
            List<Commit> commits = compareResults == null || compareResults.getCommits() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(compareResults.getCommits());

            commits.sort(Comparator.comparing(Commit::getCommittedDate, Comparator.nullsLast(Date::compareTo)));

            List<String> commitIds = commits.stream()
                    .map(Commit::getId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());

            if (commitIds.isEmpty()) {
                return List.of(headCommit);
            }
            return commitIds;
        } catch (GitLabApiException e) {
            throw new BusinessException(500, "Failed to compare commits from GitLab: " + e.getMessage());
        }
    }

    // 通过与首个 parent compare，获取某个 commit 的文件级变更。
    public List<CommitFileChange> listCommitFileChanges(String token, String project, String commitId) {
        try {
            GitLabApi gitLabApi = buildApi(token);
            Commit commit = gitLabApi.getCommitsApi().getCommit(project, commitId);
            if (commit == null || commit.getParentIds() == null || commit.getParentIds().isEmpty()) {
                return List.of();
            }

            String parentId = commit.getParentIds().get(0);
            CompareResults compareResults = gitLabApi.getRepositoryApi().compare(project, parentId, commitId);
            if (compareResults == null || compareResults.getDiffs() == null) {
                return List.of();
            }

            List<CommitFileChange> changes = new ArrayList<>();
            for (Diff diff : compareResults.getDiffs()) {
                changes.add(toFileChange(diff));
            }
            return changes;
        } catch (GitLabApiException e) {
            throw new BusinessException(500, "Failed to load commit diffs from GitLab: " + e.getMessage());
        }
    }

    // 读取指定 commit 的文件内容，并解码 GitLab base64 内容。
    public String readFileContent(String token, String project, String filePath, String commitId) {
        try {
            GitLabApi gitLabApi = buildApi(token);
            RepositoryFile repositoryFile = gitLabApi.getRepositoryFileApi().getFile(project, filePath, commitId);
            if (repositoryFile == null || !StringUtils.hasText(repositoryFile.getContent())) {
                return "";
            }

            String content = repositoryFile.getContent().replace("\n", "");
            byte[] decoded = Base64.getDecoder().decode(content);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (GitLabApiException e) {
            log.warn("Failed to read file from GitLab. project={}, filePath={}, commitId={}, message={}",
                    project, filePath, commitId, e.getMessage());
            throw new BusinessException(500, "Failed to read file content from GitLab: " + e.getMessage());
        }
    }

    // 将 GitLab diff 标记映射为内部变更类型。
    private CommitFileChange toFileChange(Diff diff) {
        if (Boolean.TRUE.equals(diff.getDeletedFile())) {
            return new CommitFileChange(diff.getOldPath(), diff.getNewPath(), CommitFileChange.ChangeType.DELETED);
        }
        if (Boolean.TRUE.equals(diff.getRenamedFile())) {
            return new CommitFileChange(diff.getOldPath(), diff.getNewPath(), CommitFileChange.ChangeType.RENAMED);
        }
        if (Boolean.TRUE.equals(diff.getNewFile())) {
            return new CommitFileChange(diff.getOldPath(), diff.getNewPath(), CommitFileChange.ChangeType.ADDED);
        }
        return new CommitFileChange(diff.getOldPath(), diff.getNewPath(), CommitFileChange.ChangeType.MODIFIED);
    }

    // 基础校验 token 后创建带认证的 GitLab 客户端。
    private GitLabApi buildApi(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }
        return new GitLabApi(apiUrl, token);
    }
}
