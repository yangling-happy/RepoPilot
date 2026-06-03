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

//文档流水线专用的 GitLab API 轻量客户端
//职责：通过 GitLab REST API 读取 commit 信息、文件变更和文件内容
//不使用 GitLab 官方 SDK，而是直接用 Java HttpClient 调用 REST API，保持轻量
@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabDocClient {

    //JSON 解析工具
    private final ObjectMapper objectMapper;
    //Java HTTP 客户端
    private final HttpClient httpClient = HttpClient.newHttpClient();

    //GitLab API 基础地址（从配置文件读取）
    @Value("${gitlab.api-url}")
    private String apiUrl;

    //获取分支 head commit id
    //用于 refresh 时判断远程分支当前最新代码版本
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

    //按时间顺序列出 baseline 到 head 之间的 commit id
    //
    //baselineCommit 是上次处理到的 commit，headCommit 是这次分支最新 commit。
    //GitLab compare 接口返回两者之间的 commits，文档流水线再按时间顺序逐个处理。
    public List<String> listCommitIdsSince(String token, String project, String baselineCommit, String headCommit) {
        if (!StringUtils.hasText(baselineCommit)) {
            //没有基线说明是第一次处理，至少要处理 headCommit
            return List.of(headCommit);
        }
        if (Objects.equals(baselineCommit, headCommit)) {
            //基线和最新提交一致，说明没有新增 commit
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

    public List<CommitFileChange> listFileChangesBetween(String token, String project, String fromCommit,
            String toCommit) {
        JsonNode compareResults = getJson(token,
                "/projects/" + encodePathSegment(project)
                        + "/repository/compare?from=" + encodeQueryParam(fromCommit)
                        + "&to=" + encodeQueryParam(toCommit),
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

    //通过与首个 parent compare，获取某个 commit 的文件级变更
    //
    //GitLab commit 接口本身会返回 parent_ids。
    //这里取第一个 parent 和当前 commit 做 compare，就能得到这个 commit 引入的文件差异。
    public List<CommitFileChange> listCommitFileChanges(String token, String project, String commitId) {
        JsonNode commit = getJson(token,
                "/projects/" + encodePathSegment(project) + "/repository/commits/" + encodePathSegment(commitId),
                "load commit");
        JsonNode parentIds = commit.path("parent_ids");
        if (!parentIds.isArray() || parentIds.isEmpty()) {
            //没有 parent 通常是仓库初始提交，当前逻辑暂不按增量 diff 处理
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

    //读取指定 commit 的文件内容，并解码 GitLab base64 内容
    //
    //GitLab repository/files 接口返回的 content 字段是 base64，
    //所以文档生成前必须先解码成真正的 UTF-8 源码文本。
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

    //将 GitLab diff 标记映射为内部变更类型
    //
    //GitLab 原始字段是 deleted_file/renamed_file/new_file 这种布尔标记，
    //后续业务更适合用 ADDED/MODIFIED/RENAMED/DELETED 枚举来判断。
    private CommitFileChange toFileChange(JsonNode diff) {
        String oldPath = diff.path("old_path").asText(null);
        String newPath = diff.path("new_path").asText(null);
        //asBoolean是设置默认值的意思，不是“当xxx为False时执行以下语句”
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

        //所有 GitLab API 调用都走这个统一方法：
        //  - 统一拼接 apiBase
        //  - 统一使用 Authorization: Bearer 头（兼容 OAuth Token 和 PAT）
        //  - 统一把 HTTP 状态码翻译成业务异常
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + pathAndQuery))
                .header("Authorization", "Bearer " + token.trim())
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
        //配置里可能写成 https://gitlab.com/api/v4/，这里去掉末尾斜杠，
        //这样拼接 pathAndQuery 时不会出现双斜杠
        String trimmed = apiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encodePathSegment(String value) {
        //GitLab 的 project、branch、filePath 都可能包含 /、空格等特殊字符，
        //放进 URL 前必须编码，否则会被误认为路径分隔符或非法字符
        return encodeQueryParam(value);
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
        //URL编码字符, “%20”表示空格
                .replace("+", "%20");
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            //GitLab 常见时间格式带时区偏移，例如 2026-05-13T10:00:00.000+08:00
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            try {
                //有些接口或测试数据可能已经是标准 Instant 格式，例如 2026-05-13T02:00:00Z
                return Instant.parse(value);
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    //把 GitLab 错误响应压缩成一小段，放进异常消息
    //避免接口失败时把过长的 HTML/JSON body 整个塞进日志和前端响应
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
