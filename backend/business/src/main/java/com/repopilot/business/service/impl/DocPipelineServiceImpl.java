package com.repopilot.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.business.service.DocPipelineService;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// 基于 commit 的文档流水线实现。
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPipelineServiceImpl implements DocPipelineService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final Pattern JAVADOC_PATTERN = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL);

    private final DocTaskMapper docTaskMapper;
    private final DocFileMapper docFileMapper;
    private final GitLabDocClient gitLabDocClient;
    private final ObjectMapper objectMapper;

    // 按 commit 顺序检测未处理记录并执行提取任务。
    @Override
    public DocRefreshResult refresh(String project, String branch, String token) {
        validateProjectAndBranch(project, branch);

        String headCommit = gitLabDocClient.getHeadCommit(token, project, branch);
        String baselineCommit = findBaselineCommit(project, branch);

        DocRefreshResult result = new DocRefreshResult();
        result.setProject(project);
        result.setBranch(branch);
        result.setBaselineCommit(baselineCommit);
        result.setHeadCommit(headCommit);

        if (Objects.equals(baselineCommit, headCommit)) {
            result.setMessage("No new commits.");
            return result;
        }

        List<String> detectedCommitIds = gitLabDocClient.listCommitIdsSince(token, project, baselineCommit, headCommit);
        result.setDetectedCommitIds(detectedCommitIds);
        result.setNewCommitCount(detectedCommitIds.size());

        for (String commitId : detectedCommitIds) {
            if (alreadyHandled(project, branch, commitId)) {
                result.getSkippedCommitIds().add(commitId);
                continue;
            }

            String status = runExtractionTask(project, branch, commitId, token);
            if (STATUS_FAILED.equals(status)) {
                result.getFailedTaskCommitIds().add(commitId);
            } else {
                result.getCreatedTaskCommitIds().add(commitId);
            }
        }

        result.setMessage(String.format(
                "Detected %d commit(s), created %d task(s), skipped %d task(s), failed %d task(s).",
                result.getNewCommitCount(),
                result.getCreatedTaskCommitIds().size(),
                result.getSkippedCommitIds().size(),
                result.getFailedTaskCommitIds().size()
        ));
        return result;
    }

    // 对指定 commit 强制提取，失败时向调用方抛错。
    @Override
    public void rebuild(String project, String branch, String commitId, String token) {
        validateProjectAndBranch(project, branch);
        if (!StringUtils.hasText(commitId)) {
            throw new BusinessException(400, "commitId is required for rebuild");
        }

        String status = runExtractionTask(project, branch, commitId, token);
        if (STATUS_FAILED.equals(status)) {
            throw new BusinessException(500, "Rebuild failed for commit: " + commitId);
        }
    }

    // 返回指定 commit 记录，或按文件维度返回最新未删除快照。
    @Override
    public List<DocQueryItem> query(String project, String branch, String filePath, String commitId) {
        if (!StringUtils.hasText(project)) {
            throw new BusinessException(400, "project is required");
        }

        LambdaQueryWrapper<DocFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocFile::getProjectName, project);
        if (StringUtils.hasText(branch)) {
            wrapper.eq(DocFile::getBranchName, branch);
        }
        if (StringUtils.hasText(filePath)) {
            wrapper.eq(DocFile::getFilePath, filePath);
        }
        if (StringUtils.hasText(commitId)) {
            wrapper.eq(DocFile::getCommitId, commitId);
        }
        wrapper.orderByDesc(DocFile::getId);

        List<DocFile> rows = docFileMapper.selectList(wrapper);
        if (StringUtils.hasText(commitId)) {
            return rows.stream().map(this::toQueryItem).collect(Collectors.toList());
        }

        Map<String, DocFile> latestByFile = new LinkedHashMap<>();
        for (DocFile row : rows) {
            String key = row.getBranchName() + "|" + row.getFilePath();
            latestByFile.putIfAbsent(key, row);
        }

        return latestByFile.values().stream()
                .filter(row -> !Boolean.TRUE.equals(row.getDeleted()))
                .map(this::toQueryItem)
                .collect(Collectors.toList());
    }

    // 创建任务日志并执行单个 commit 的文件变更处理。
    private String runExtractionTask(String project, String branch, String commitId, String token) {
        DocTask task = new DocTask();
        task.setProject(project);
        task.setBranch(branch);
        task.setCommitId(commitId);
        task.setStatus(STATUS_RUNNING);
        task.setDuration(0);
        docTaskMapper.insert(task);

        long start = System.currentTimeMillis();
        try {
            List<CommitFileChange> changes = gitLabDocClient.listCommitFileChanges(token, project, commitId);
            int handledJavaFiles = applyChanges(project, branch, commitId, token, changes);

            String finalStatus = handledJavaFiles == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
            task.setStatus(finalStatus);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return finalStatus;
        } catch (Exception ex) {
            log.error("Doc extraction failed. project={}, branch={}, commitId={}", project, branch, commitId, ex);
            task.setStatus(STATUS_FAILED);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return STATUS_FAILED;
        }
    }

    // 应用单个 commit 的文件级变更，并返回处理文件数量。
    private int applyChanges(String project,
                             String branch,
                             String commitId,
                             String token,
                             List<CommitFileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (CommitFileChange change : changes) {
            switch (change.getChangeType()) {
                case ADDED, MODIFIED -> {
                    if (isJavaFile(change.getNewPath())) {
                        upsertActiveDoc(project, branch, change.getNewPath(), commitId, token);
                        handled++;
                    }
                }
                case DELETED -> {
                    if (isJavaFile(change.getOldPath())) {
                        upsertDeletedDoc(project, branch, change.getOldPath(), commitId);
                        handled++;
                    }
                }
                case RENAMED -> {
                    if (isJavaFile(change.getOldPath())) {
                        upsertDeletedDoc(project, branch, change.getOldPath(), commitId);
                        handled++;
                    }
                    if (isJavaFile(change.getNewPath())) {
                        upsertActiveDoc(project, branch, change.getNewPath(), commitId, token);
                        handled++;
                    }
                }
            }
        }

        return handled;
    }

    // 解析当前文件内容并写入有效文档记录。
    private void upsertActiveDoc(String project, String branch, String filePath, String commitId, String token) {
        String fileContent = gitLabDocClient.readFileContent(token, project, filePath, commitId);
        List<String> javaDocBlocks = extractJavaDocBlocks(fileContent);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", project);
        payload.put("branch", branch);
        payload.put("filePath", filePath);
        payload.put("commitId", commitId);
        payload.put("deleted", false);
        payload.put("commentCount", javaDocBlocks.size());
        payload.put("comments", javaDocBlocks);

        String markdown = toMarkdown(filePath, javaDocBlocks);
        upsertDocFile(project, branch, filePath, commitId, toJson(payload), markdown, false);
    }

    // 为本次 commit 删除的文件写入删除标记记录。
    private void upsertDeletedDoc(String project, String branch, String filePath, String commitId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", project);
        payload.put("branch", branch);
        payload.put("filePath", filePath);
        payload.put("commitId", commitId);
        payload.put("deleted", true);

        upsertDocFile(project, branch, filePath, commitId, toJson(payload), null, true);
    }

    // 按 project/branch/file/commit upsert 一条 doc_file_dtl 记录。
    private void upsertDocFile(String project,
                               String branch,
                               String filePath,
                               String commitId,
                               String docJson,
                               String docMarkdown,
                               boolean deleted) {
        LambdaQueryWrapper<DocFile> query = new LambdaQueryWrapper<>();
        query.eq(DocFile::getProjectName, project)
            .eq(DocFile::getBranchName, branch)
                .eq(DocFile::getFilePath, filePath)
                .eq(DocFile::getCommitId, commitId)
                .last("LIMIT 1");

        DocFile existing = docFileMapper.selectOne(query);
        if (existing == null) {
            DocFile item = new DocFile();
            item.setProjectName(project);
            item.setBranchName(branch);
            item.setFilePath(filePath);
            item.setCommitId(commitId);
            item.setDocJson(docJson);
            item.setDocMarkdown(docMarkdown);
            item.setDeleted(deleted);
            docFileMapper.insert(item);
            return;
        }

        existing.setDocJson(docJson);
        existing.setDocMarkdown(docMarkdown);
        existing.setDeleted(deleted);
        docFileMapper.updateById(existing);
    }

    // 读取最近成功基线 commit，用于增量比对。
    private String findBaselineCommit(String project, String branch) {
        LambdaQueryWrapper<DocTask> query = new LambdaQueryWrapper<>();
        query.eq(DocTask::getProject, project)
                .eq(DocTask::getBranch, branch)
                .in(DocTask::getStatus, Arrays.asList(STATUS_SUCCESS, STATUS_SKIPPED))
                .orderByDesc(DocTask::getCreateTime)
                .last("LIMIT 1");

        DocTask latestTask = docTaskMapper.selectOne(query);
        return latestTask == null ? null : latestTask.getCommitId();
    }

    // 检查同一分支下该 commit 是否已处理。
    private boolean alreadyHandled(String project, String branch, String commitId) {
        LambdaQueryWrapper<DocTask> query = new LambdaQueryWrapper<>();
        query.eq(DocTask::getProject, project)
                .eq(DocTask::getBranch, branch)
                .eq(DocTask::getCommitId, commitId)
                .in(DocTask::getStatus, Arrays.asList(STATUS_RUNNING, STATUS_SUCCESS, STATUS_SKIPPED));

        return docTaskMapper.selectCount(query) > 0;
    }

    // 从源码中提取 JavaDoc 块注释。
    private List<String> extractJavaDocBlocks(String sourceCode) {
        if (!StringUtils.hasText(sourceCode)) {
            return List.of();
        }

        Matcher matcher = JAVADOC_PATTERN.matcher(sourceCode);
        List<String> blocks = new java.util.ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group());
        }
        return blocks;
    }

    // 将提取出的 JavaDoc 注释转换为简要 markdown 文档。
    private String toMarkdown(String filePath, List<String> javaDocBlocks) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(filePath).append("\n\n");

        if (javaDocBlocks.isEmpty()) {
            builder.append("No JavaDoc comments found.\n");
            return builder.toString();
        }

        for (int i = 0; i < javaDocBlocks.size(); i++) {
            builder.append("## JavaDoc ").append(i + 1).append("\n\n");
            builder.append("```java\n");
            builder.append(javaDocBlocks.get(i)).append("\n");
            builder.append("```\n\n");
        }
        return builder.toString();
    }

    // 将 payload 序列化为 JSON，存入 doc_json。
    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "Failed to serialize doc payload");
        }
    }

    // 当前 MVP 仅处理 Java 文件。
    private boolean isJavaFile(String path) {
        return StringUtils.hasText(path) && path.endsWith(".java");
    }

    // 将持久化模型映射为查询返回模型。
    private DocQueryItem toQueryItem(DocFile row) {
        DocQueryItem item = new DocQueryItem();
        item.setProject(row.getProjectName());
        item.setBranch(row.getBranchName());
        item.setFilePath(row.getFilePath());
        item.setCommitId(row.getCommitId());
        item.setDocJson(row.getDocJson());
        item.setDocMarkdown(row.getDocMarkdown());
        item.setDeleted(row.getDeleted());
        item.setUpdateTime(row.getUpdateTime());
        return item;
    }

    // 校验 refresh/rebuild 的必填参数。
    private void validateProjectAndBranch(String project, String branch) {
        if (!StringUtils.hasText(project) || !StringUtils.hasText(branch)) {
            throw new BusinessException(400, "project and branch are required");
        }
    }
}
