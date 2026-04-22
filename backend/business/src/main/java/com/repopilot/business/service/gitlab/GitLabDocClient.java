package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

// 文档流水线使用的 GitLab API 轻量客户端，负责 commit 与文件数据读取。
@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabDocClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${gitlab.api-url}")
    private String apiUrl;

    // 获取分支 head commit id。
    public String getHeadCommit(String token, String project, String branch) {
        JsonNode branchInfo = getJson(token,
                "/projects/" + encodePathSegment(project) + "/repository/branches/" + encodePathSegment(branch),
                "load branch head");
        String commitId = branchInfo.path("commit").path("id").asText(null);
        if (!StringUtils.hasText(commitId)) {
            throw new BusinessException(500, "Unable to resolve head commit for project/branch");
        }
        return commitId;
    }

    // 按时间顺序列出 baseline 到 head 之间的 commit id。
    public List<String> listCommitIdsSince(String token, String project, String baselineCommit, String headCommit) {
        if (!StringUtils.hasText(baselineCommit)) {
            return List.of(headCommit);
        }
        if (Objects.equals(baselineCommit, headCommit)) {
            return List.of();
        }

        JsonNode compareResults = getJson(token,
                "/projects/" + encodePathSegment(project)
                        + "/repository/compare?from=" + encodeQueryParam(baselineCommit)
                        + "&to=" + encodeQueryParam(headCommit),
                "compare commits");
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

    // 通过与首个 parent compare，获取某个 commit 的文件级变更。
    public List<CommitFileChange> listCommitFileChanges(String token, String project, String commitId) {
        JsonNode commit = getJson(token,
                "/projects/" + encodePathSegment(project) + "/repository/commits/" + encodePathSegment(commitId),
                "load commit");
        JsonNode parentIds = commit.path("parent_ids");
        if (!parentIds.isArray() || parentIds.isEmpty()) {
            return List.of();
        }

        String parentId = parentIds.get(0).asText(null);
        if (!StringUtils.hasText(parentId)) {
            return List.of();
        }

        JsonNode compareResults = getJson(token,
                "/projects/" + encodePathSegment(project)
                        + "/repository/compare?from=" + encodeQueryParam(parentId)
                        + "&to=" + encodeQueryParam(commitId),
                "load commit diffs");
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

    // 读取指定 commit 的文件内容，并解码 GitLab base64 内容。
    public String readFileContent(String token, String project, String filePath, String commitId) {
        try {
            JsonNode repositoryFile = getJson(token,
                    "/projects/" + encodePathSegment(project)
                            + "/repository/files/" + encodePathSegment(filePath)
                            + "?ref=" + encodeQueryParam(commitId),
                    "read file content");
            String content = repositoryFile.path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                return "";
            }

            byte[] decoded = Base64.getMimeDecoder().decode(content);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            log.warn("Failed to read file from GitLab. project={}, filePath={}, commitId={}, message={}",
                    project, filePath, commitId, e.getMessage());
            throw e;
        }
    }

    // 将 GitLab diff 标记映射为内部变更类型。
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

    private JsonNode getJson(String token, String pathAndQuery, String action) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + pathAndQuery))
                .header("PRIVATE-TOKEN", token.trim())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new BusinessException(401, "GitLab token is invalid or lacks permission to " + action);
            }
            if (status == 404) {
                throw new BusinessException(404, "GitLab resource not found while trying to " + action);
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException(500, "GitLab API failed to " + action
                        + ", status=" + status + ", body=" + summarize(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to " + action + " from GitLab");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while trying to " + action + " from GitLab");
        }
    }

    private String apiBase() {
        if (!StringUtils.hasText(apiUrl)) {
            throw new BusinessException(500, "GitLab API URL is not configured");
        }
        String trimmed = apiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encodePathSegment(String value) {
        return encodeQueryParam(value);
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
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

    private String summarize(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private record CommitInfo(String id, Instant committedAt) {
    }
}
