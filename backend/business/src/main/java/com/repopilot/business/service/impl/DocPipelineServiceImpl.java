package com.repopilot.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.business.service.DocPipelineService;
import com.repopilot.business.service.docgen.DocGenerationContext;
import com.repopilot.business.service.docgen.DocGenerationResult;
import com.repopilot.business.service.docgen.DocGenerator;
import com.repopilot.business.service.docgen.DocGeneratorRegistry;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocPipelineServiceImpl implements DocPipelineService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";

    private final DocTaskMapper docTaskMapper;
    private final DocFileMapper docFileMapper;
    private final GitLabDocClient gitLabDocClient;
    private final DocGeneratorRegistry docGeneratorRegistry;

    @Value("${repo.clone.root-dir:./workspace/repos}")
    private String repoCloneRoot;

    @Value("${doc.output.root-dir:./workspace/docs}")
    private String docOutputRoot;

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
                .filter(row -> StringUtils.hasText(row.getDocFilePath()))
                .map(this::toQueryItem)
                .collect(Collectors.toList());
    }

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
            int handledDocFiles = applyChanges(project, branch, commitId, token, changes);

            String finalStatus = handledDocFiles == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
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
                    if (upsertActiveDoc(project, branch, change.getNewPath(), commitId, token)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
                case DELETED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(project, branch, change.getOldPath(), commitId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                }
                case RENAMED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(project, branch, change.getOldPath(), commitId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                    if (upsertActiveDoc(project, branch, change.getNewPath(), commitId, token)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
            }
        }

        return handled;
    }

    private boolean upsertActiveDoc(String project, String branch, String filePath, String commitId, String token) {
        DocGenerator generator = docGeneratorRegistry.findGenerator(filePath).orElse(null);
        if (generator == null) {
            return false;
        }

        String fileContent = gitLabDocClient.readFileContent(token, project, filePath, commitId);
        DocGenerationResult result = generator.generate(DocGenerationContext.builder()
                .project(project)
                .branch(branch)
                .commitId(commitId)
                .filePath(filePath)
                .sourceContent(fileContent)
                .sourceRoot(resolveSourceRoot(project))
                .outputRoot(resolveDocOutputRoot())
                .build());

        upsertDocFile(project, branch, filePath, commitId, result.getDocFilePath(), null);
        return true;
    }

    private void upsertDeletedDoc(String project, String branch, String filePath, String commitId) {
        upsertDocFile(project, branch, filePath, commitId, null, "File deleted");
    }

    private void upsertDocFile(String project,
                               String branch,
                               String filePath,
                               String commitId,
                               String docFilePath,
                               String parseErrorMsg) {
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
            item.setDocFilePath(docFilePath);
            item.setParseStatus(STATUS_SUCCESS);
            item.setParseErrorMsg(parseErrorMsg);
            docFileMapper.insert(item);
            return;
        }

        existing.setDocFilePath(docFilePath);
        existing.setParseStatus(STATUS_SUCCESS);
        existing.setParseErrorMsg(parseErrorMsg);
        docFileMapper.updateById(existing);
    }

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

    private boolean alreadyHandled(String project, String branch, String commitId) {
        LambdaQueryWrapper<DocTask> query = new LambdaQueryWrapper<>();
        query.eq(DocTask::getProject, project)
                .eq(DocTask::getBranch, branch)
                .eq(DocTask::getCommitId, commitId)
                .in(DocTask::getStatus, Arrays.asList(STATUS_RUNNING, STATUS_SUCCESS, STATUS_SKIPPED));

        return docTaskMapper.selectCount(query) > 0;
    }

    private boolean isSupportedDocFile(String path) {
        return docGeneratorRegistry.supports(path);
    }

    private void logUnsupportedFile(String project, String commitId, String filePath) {
        if (StringUtils.hasText(filePath)) {
            log.warn("Skip unsupported doc file. project={}, commitId={}, filePath={}", project, commitId, filePath);
        }
    }

    private Path resolveSourceRoot(String project) {
        if (!StringUtils.hasText(project) || !project.trim().matches("\\d+")) {
            return null;
        }

        String rootDir = StringUtils.hasText(repoCloneRoot) ? repoCloneRoot : "./workspace/repos";
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        Path candidate = root.resolve("project-" + project.trim()).normalize();
        if (!candidate.startsWith(root) || !Files.isDirectory(candidate)) {
            return null;
        }
        return candidate;
    }

    private Path resolveDocOutputRoot() {
        String rootDir = StringUtils.hasText(docOutputRoot) ? docOutputRoot : "./workspace/docs";
        return Paths.get(rootDir).toAbsolutePath().normalize();
    }

    private DocQueryItem toQueryItem(DocFile row) {
        DocQueryItem item = new DocQueryItem();
        item.setProject(row.getProjectName());
        item.setBranch(row.getBranchName());
        item.setFilePath(row.getFilePath());
        item.setCommitId(row.getCommitId());
        item.setDocFilePath(row.getDocFilePath());
        item.setParseStatus(row.getParseStatus());
        item.setParseErrorMsg(row.getParseErrorMsg());
        item.setUpdateTime(row.getUpdateTime());
        return item;
    }

    private void validateProjectAndBranch(String project, String branch) {
        if (!StringUtils.hasText(project) || !StringUtils.hasText(branch)) {
            throw new BusinessException(400, "project and branch are required");
        }
    }
}
