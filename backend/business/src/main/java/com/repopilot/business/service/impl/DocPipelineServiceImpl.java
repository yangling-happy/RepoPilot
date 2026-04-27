package com.repopilot.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repopilot.business.dto.DocLocalScanResult;
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
import com.repopilot.business.service.gitignore.GitIgnoreMatcher;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.gitlab.model.CommitFileChange;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private final UserWorkspaceResolver userWorkspaceResolver;

    @Override
    public DocRefreshResult refresh(String gitlabUsername, String project, String branch, String token) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);

        String headCommit = gitLabDocClient.getHeadCommit(token, project, branch);
        String baselineCommit = findBaselineCommit(gitlabUsername, project, branch);

        DocRefreshResult result = new DocRefreshResult();
        result.setGitlabUsername(gitlabUsername);
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
            if (alreadyHandled(gitlabUsername, project, branch, commitId)) {
                result.getSkippedCommitIds().add(commitId);
                continue;
            }

            String status = runExtractionTask(gitlabUsername, project, branch, commitId, token);
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
    public void rebuild(String gitlabUsername, String project, String branch, String commitId, String token) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);
        if (!StringUtils.hasText(commitId)) {
            throw new BusinessException(400, "commitId is required for rebuild");
        }

        String status = runExtractionTask(gitlabUsername, project, branch, commitId, token);
        if (STATUS_FAILED.equals(status)) {
            throw new BusinessException(500, "Rebuild failed for commit: " + commitId);
        }
    }

    @Override
    public DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);

        Path sourceRoot = resolveSourceRoot(gitlabUsername, project);
        if (sourceRoot == null) {
            throw new BusinessException(400, "Local repository not found for project: " + project);
        }

        String commitId = resolveLocalHeadCommit(sourceRoot);
        DocLocalScanResult result = new DocLocalScanResult();
        result.setGitlabUsername(gitlabUsername);
        result.setProject(project);
        result.setBranch(branch);
        result.setCommitId(commitId);
        result.setLocalRepoPath(sourceRoot.toString());

        DocTask task = new DocTask();
        task.setEventId(buildTaskEventId("doc-local-scan", commitId));
        task.setGitlabUsername(gitlabUsername);
        task.setProject(project);
        task.setBranch(branch);
        task.setCommitId(commitId);
        task.setStatus(STATUS_RUNNING);
        task.setDuration(0);
        docTaskMapper.insert(task);

        long start = System.currentTimeMillis();
        try {
            List<Path> files = listLocalRepoFiles(sourceRoot);
            result.setScannedFileCount(files.size());

            for (Path file : files) {
                String filePath = toRepoRelativePath(sourceRoot, file);
                if (!isSupportedDocFile(filePath)) {
                    result.setSkippedFileCount(result.getSkippedFileCount() + 1);
                    continue;
                }

                try {
                    generateLocalDoc(gitlabUsername, project, branch, commitId, task.getId(), sourceRoot, file, filePath);
                    result.setGeneratedFileCount(result.getGeneratedFileCount() + 1);
                    result.getGeneratedFilePaths().add(filePath);
                } catch (Exception ex) {
                    log.error("Local doc generation failed. project={}, branch={}, filePath={}",
                            project, branch, filePath, ex);
                    result.setFailedFileCount(result.getFailedFileCount() + 1);
                    result.getFailedFilePaths().add(filePath);
                    upsertDocFile(gitlabUsername, project, branch, filePath, commitId, task.getId(),
                            STATUS_FAILED, null, summarizeError(ex));
                }
            }

            String finalStatus = localScanStatus(result);
            task.setStatus(finalStatus);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            result.setMessage(String.format(
                    "Scanned %d file(s), generated %d doc(s), skipped %d file(s), failed %d file(s).",
                    result.getScannedFileCount(),
                    result.getGeneratedFileCount(),
                    result.getSkippedFileCount(),
                    result.getFailedFileCount()
            ));
            return result;
        } catch (Exception ex) {
            log.error("Local doc scan failed. project={}, branch={}, commitId={}", project, branch, commitId, ex);
            task.setStatus(STATUS_FAILED);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(500, "Local doc scan failed");
        }
    }

    @Override
    public List<DocQueryItem> query(String gitlabUsername, String project, String branch, String filePath, String commitId) {
        validateGitlabUsername(gitlabUsername);
        if (!StringUtils.hasText(project)) {
            throw new BusinessException(400, "project is required");
        }

        LambdaQueryWrapper<DocFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocFile::getGitlabUsername, gitlabUsername)
                .eq(DocFile::getProjectName, project);
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

    private String runExtractionTask(String gitlabUsername, String project, String branch, String commitId, String token) {
        DocTask task = new DocTask();
        task.setEventId(buildTaskEventId("doc-refresh", commitId));
        task.setGitlabUsername(gitlabUsername);
        task.setProject(project);
        task.setBranch(branch);
        task.setCommitId(commitId);
        task.setStatus(STATUS_RUNNING);
        task.setDuration(0);
        docTaskMapper.insert(task);

        long start = System.currentTimeMillis();
        try {
            List<CommitFileChange> changes = gitLabDocClient.listCommitFileChanges(token, project, commitId);
            int handledDocFiles = applyChanges(gitlabUsername, project, branch, commitId, task.getId(), token, changes);

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

    private int applyChanges(String gitlabUsername,
                             String project,
                             String branch,
                             String commitId,
                             Long taskId,
                             String token,
                             List<CommitFileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (CommitFileChange change : changes) {
            switch (change.getChangeType()) {
                case ADDED, MODIFIED -> {
                    if (upsertActiveDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId, token)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
                case DELETED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(gitlabUsername, project, branch, change.getOldPath(), commitId, taskId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                }
                case RENAMED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(gitlabUsername, project, branch, change.getOldPath(), commitId, taskId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                    if (upsertActiveDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId, token)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
            }
        }

        return handled;
    }

    private boolean upsertActiveDoc(String gitlabUsername,
                                    String project,
                                    String branch,
                                    String filePath,
                                    String commitId,
                                    Long taskId,
                                    String token) {
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
                .sourceRoot(resolveSourceRoot(gitlabUsername, project))
                .outputRoot(resolveDocOutputRoot(gitlabUsername))
                .build());

        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, result.getDocFilePath(), null);
        return true;
    }

    private void generateLocalDoc(String gitlabUsername,
                                  String project,
                                  String branch,
                                  String commitId,
                                  Long taskId,
                                  Path sourceRoot,
                                  Path sourceFile,
                                  String filePath) throws IOException {
        DocGenerator generator = docGeneratorRegistry.findGenerator(filePath).orElse(null);
        if (generator == null) {
            return;
        }

        String fileContent = Files.readString(sourceFile, StandardCharsets.UTF_8);
        DocGenerationResult result = generator.generate(DocGenerationContext.builder()
                .project(project)
                .branch(branch)
                .commitId(commitId)
                .filePath(filePath)
                .sourceContent(fileContent)
                .sourceRoot(sourceRoot)
                .outputRoot(resolveDocOutputRoot(gitlabUsername))
                .build());

        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, result.getDocFilePath(), null);
    }

    private void upsertDeletedDoc(String gitlabUsername, String project, String branch, String filePath, String commitId, Long taskId) {
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, null, "File deleted");
    }

    private void upsertDocFile(String gitlabUsername,
                               String project,
                               String branch,
                               String filePath,
                               String commitId,
                               Long taskId,
                               String docFilePath,
                               String parseErrorMsg) {
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, STATUS_SUCCESS, docFilePath, parseErrorMsg);
    }

    private void upsertDocFile(String gitlabUsername,
                               String project,
                               String branch,
                               String filePath,
                               String commitId,
                               Long taskId,
                               String parseStatus,
                               String docFilePath,
                               String parseErrorMsg) {
        LambdaQueryWrapper<DocFile> query = new LambdaQueryWrapper<>();
        query.eq(DocFile::getGitlabUsername, gitlabUsername)
                .eq(DocFile::getProjectName, project)
                .eq(DocFile::getBranchName, branch)
                .eq(DocFile::getFilePath, filePath)
                .eq(DocFile::getCommitId, commitId)
                .last("LIMIT 1");

        DocFile existing = docFileMapper.selectOne(query);
        if (existing == null) {
            DocFile item = new DocFile();
            item.setTaskId(taskId);
            item.setGitlabUsername(gitlabUsername);
            item.setProjectName(project);
            item.setBranchName(branch);
            item.setFilePath(filePath);
            item.setCommitId(commitId);
            item.setDocFilePath(docFilePath);
            item.setParseStatus(parseStatus);
            item.setParseErrorMsg(parseErrorMsg);
            docFileMapper.insert(item);
            return;
        }

        existing.setTaskId(taskId);
        existing.setDocFilePath(docFilePath);
        existing.setParseStatus(parseStatus);
        existing.setParseErrorMsg(parseErrorMsg);
        docFileMapper.updateById(existing);
    }

    private String buildTaskEventId(String prefix, String commitId) {
        String safeCommitId = StringUtils.hasText(commitId) ? commitId.trim() : "unknown";
        return prefix + "-" + safeCommitId + "-" + Long.toString(System.nanoTime(), 36);
    }

    private String findBaselineCommit(String gitlabUsername, String project, String branch) {
        LambdaQueryWrapper<DocTask> query = new LambdaQueryWrapper<>();
        query.eq(DocTask::getGitlabUsername, gitlabUsername)
                .eq(DocTask::getProject, project)
                .eq(DocTask::getBranch, branch)
                .in(DocTask::getStatus, Arrays.asList(STATUS_SUCCESS, STATUS_SKIPPED))
                .orderByDesc(DocTask::getCreateTime)
                .last("LIMIT 1");

        DocTask latestTask = docTaskMapper.selectOne(query);
        return latestTask == null ? null : latestTask.getCommitId();
    }

    private boolean alreadyHandled(String gitlabUsername, String project, String branch, String commitId) {
        LambdaQueryWrapper<DocTask> query = new LambdaQueryWrapper<>();
        query.eq(DocTask::getGitlabUsername, gitlabUsername)
                .eq(DocTask::getProject, project)
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

    private List<Path> listLocalRepoFiles(Path sourceRoot) throws IOException {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        Path gitDir = normalizedRoot.resolve(".git").normalize();
        GitIgnoreMatcher gitIgnoreMatcher = GitIgnoreMatcher.load(normalizedRoot);
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalizedDir = dir.toAbsolutePath().normalize();
                if (normalizedDir.equals(gitDir) || normalizedDir.startsWith(gitDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!normalizedDir.equals(normalizedRoot) && gitIgnoreMatcher.isIgnored(normalizedDir, true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path normalizedFile = file.toAbsolutePath().normalize();
                if (attrs.isRegularFile()
                        && !normalizedFile.equals(gitDir)
                        && !normalizedFile.startsWith(gitDir)
                        && !gitIgnoreMatcher.isIgnored(normalizedFile, false)) {
                    files.add(normalizedFile);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files.stream()
                .sorted(Comparator.comparing(path -> toRepoRelativePath(normalizedRoot, path)))
                .collect(Collectors.toList());
    }

    private String toRepoRelativePath(Path sourceRoot, Path file) {
        return sourceRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private String localScanStatus(DocLocalScanResult result) {
        if (result.getFailedFileCount() > 0) {
            return STATUS_FAILED;
        }
        return result.getGeneratedFileCount() == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
    }

    private String resolveLocalHeadCommit(Path sourceRoot) {
        try {
            Path gitDir = resolveGitDir(sourceRoot);
            Path headFile = gitDir.resolve("HEAD");
            if (!Files.isRegularFile(headFile)) {
                throw new BusinessException(400, "Local repository HEAD not found: " + sourceRoot);
            }

            String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            if (!head.startsWith("ref:")) {
                if (StringUtils.hasText(head)) {
                    return head;
                }
                throw new BusinessException(400, "Local repository HEAD is empty: " + sourceRoot);
            }

            String refName = head.substring("ref:".length()).trim();
            Path refFile = gitDir.resolve(refName).normalize();
            if (refFile.startsWith(gitDir) && Files.isRegularFile(refFile)) {
                String commitId = Files.readString(refFile, StandardCharsets.UTF_8).trim();
                if (StringUtils.hasText(commitId)) {
                    return commitId;
                }
            }

            String packedCommit = readPackedRef(gitDir, refName);
            if (StringUtils.hasText(packedCommit)) {
                return packedCommit;
            }
            throw new BusinessException(400, "Local repository ref not found: " + refName);
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read local repository HEAD");
        }
    }

    private Path resolveGitDir(Path sourceRoot) throws IOException {
        Path gitPath = sourceRoot.toAbsolutePath().normalize().resolve(".git");
        if (Files.isDirectory(gitPath)) {
            return gitPath;
        }
        if (Files.isRegularFile(gitPath)) {
            String gitFile = Files.readString(gitPath, StandardCharsets.UTF_8).trim();
            if (gitFile.startsWith("gitdir:")) {
                Path gitDir = Path.of(gitFile.substring("gitdir:".length()).trim());
                if (!gitDir.isAbsolute()) {
                    gitDir = sourceRoot.resolve(gitDir);
                }
                Path normalized = gitDir.toAbsolutePath().normalize();
                if (Files.isDirectory(normalized)) {
                    return normalized;
                }
            }
        }
        throw new BusinessException(400, "Local repository is not a Git repository: " + sourceRoot);
    }

    private String readPackedRef(Path gitDir, String refName) throws IOException {
        Path packedRefs = gitDir.resolve("packed-refs");
        if (!Files.isRegularFile(packedRefs)) {
            return null;
        }

        for (String line : Files.readAllLines(packedRefs, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#") || trimmed.startsWith("^")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2 && refName.equals(parts[1])) {
                return parts[0];
            }
        }
        return null;
    }

    private String summarizeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private Path resolveSourceRoot(String gitlabUsername, String project) {
        if (!StringUtils.hasText(project) || !project.trim().matches("\\d+")) {
            return null;
        }

        Path candidate = userWorkspaceResolver.repoPath(gitlabUsername, project);
        if (!Files.isDirectory(candidate)) {
            return null;
        }
        return candidate;
    }

    private Path resolveDocOutputRoot(String gitlabUsername) {
        return userWorkspaceResolver.docOutputRoot(gitlabUsername);
    }

    private DocQueryItem toQueryItem(DocFile row) {
        DocQueryItem item = new DocQueryItem();
        item.setGitlabUsername(row.getGitlabUsername());
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

    private void validateGitlabUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            throw new BusinessException(400, "GitLab username is required");
        }
    }
}
