package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabDocClient {

    private final GitLabHttpClient gitLabHttpClient;

    public String getHeadCommit(String token, String project, String branch) {
        JsonNode branchInfo = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/branches/" + GitLabHttpClient.encode(branch));
        String commitId = branchInfo.path("commit").path("id").asText(null);
        if (!StringUtils.hasText(commitId)) {
            throw new BusinessException(500, "Unable to resolve head commit for project/branch");
        }
        return commitId;
    }

    public List<String> listCommitIdsSince(String token, String project, String baselineCommit, String headCommit) {
        if (!StringUtils.hasText(baselineCommit)) {
            return List.of(headCommit);
        }
        if (Objects.equals(baselineCommit, headCommit)) {
            return List.of();
        }

        JsonNode compareResults = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/compare?from=" + GitLabHttpClient.encode(baselineCommit)
                        + "&to=" + GitLabHttpClient.encode(headCommit));
        JsonNode commits = compareResults.path("commits");
        if (!commits.isArray()) {
            return List.of(headCommit);
        }

        List<CommitInfo> commitInfos = new ArrayList<>();
        for (JsonNode commit : commits) {
            String commitId = commit.path("id").asText(null);
            if (StringUtils.hasText(commitId)) {
                commitInfos.add(new CommitInfo(commitId, parseInstant(commit.path("committed_date").asText(null))));
            }
        }
        commitInfos.sort(Comparator.comparing(CommitInfo::committedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        List<String> commitIds = new ArrayList<>();
        for (CommitInfo commitInfo : commitInfos) {
            if (!commitIds.contains(commitInfo.id())) {
                commitIds.add(commitInfo.id());
            }
        }
        return commitIds.isEmpty() ? List.of(headCommit) : commitIds;
    }

    public List<CommitFileChange> listFileChangesBetween(String token, String project, String fromCommit,
            String toCommit) {
        JsonNode compareResults = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/compare?from=" + GitLabHttpClient.encode(fromCommit)
                        + "&to=" + GitLabHttpClient.encode(toCommit));
        JsonNode diffs = compareResults.path("diffs");
        if (!diffs.isArray()) {
            return List.of();
        }

        List<CommitFileChange> changes = new ArrayList<>();
        for (JsonNode diff : diffs) {
            changes.add(toFileChange(diff));
        }
        return changes;
    }

    public List<CommitFileChange> listCommitFileChanges(String token, String project, String commitId) {
        JsonNode commit = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/commits/" + GitLabHttpClient.encode(commitId));
        JsonNode parentIds = commit.path("parent_ids");
        if (!parentIds.isArray() || parentIds.isEmpty()) {
            return List.of();
        }

        String parentId = parentIds.get(0).asText(null);
        if (!StringUtils.hasText(parentId)) {
            return List.of();
        }

        JsonNode compareResults = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/compare?from=" + GitLabHttpClient.encode(parentId)
                        + "&to=" + GitLabHttpClient.encode(commitId));
        JsonNode diffs = compareResults.path("diffs");
        if (!diffs.isArray()) {
            return List.of();
        }

        List<CommitFileChange> changes = new ArrayList<>();
        for (JsonNode diff : diffs) {
            changes.add(toFileChange(diff));
        }
        return changes;
    }

    public String readFileContent(String token, String project, String filePath, String commitId) {
        JsonNode repositoryFile = gitLabHttpClient.getJson(token,
                "/projects/" + GitLabHttpClient.encode(project)
                        + "/repository/files/" + GitLabHttpClient.encode(filePath)
                        + "?ref=" + GitLabHttpClient.encode(commitId));
        String content = repositoryFile.path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            return "";
        }

        byte[] decoded = Base64.getMimeDecoder().decode(content);
        return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
    }

    private CommitFileChange toFileChange(JsonNode diff) {
        String oldPath = diff.path("old_path").asText(null);
        String newPath = diff.path("new_path").asText(null);
        if (diff.path("deleted_file").asBoolean(false)) {
            return new CommitFileChange(oldPath, newPath, CommitFileChange.ChangeType.DELETED);
        }
        if (diff.path("renamed_file").asBoolean(false)) {
            return new CommitFileChange(oldPath, newPath, CommitFileChange.ChangeType.RENAMED);
        }
        if (diff.path("new_file").asBoolean(false)) {
            return new CommitFileChange(oldPath, newPath, CommitFileChange.ChangeType.ADDED);
        }
        return new CommitFileChange(oldPath, newPath, CommitFileChange.ChangeType.MODIFIED);
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private record CommitInfo(String id, Instant committedAt) {
    }
}
