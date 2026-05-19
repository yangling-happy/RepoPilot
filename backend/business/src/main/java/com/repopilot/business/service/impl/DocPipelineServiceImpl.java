package com.repopilot.business.service.impl;

//MyBatis-Plus 提供的 Lambda 查询条件构造器
//用法：new LambdaQueryWrapper<DocFile>().eq(DocFile::getProjectName, "xxx")
//好处：用方法引用（DocFile::getProjectName）代替字符串字段名，避免拼写错误，编译期就能检查
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.dto.DocStructuredContent;
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
import com.repopilot.business.service.terminal.TerminalRelayClient;
import com.repopilot.business.service.terminal.TerminalScriptTaskClient;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import com.repopilot.common.exception.BusinessException;
import com.repopilot.common.terminal.ScriptTaskRunResult;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

//文档流水线服务的核心实现类
//职责：协调 GitLab API、文档生成器、数据库，完成文档的增量刷新、重建、本地扫描和查询
//这是整个文档生成功能的中枢，负责串联各个组件完成完整的业务流程
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPipelineServiceImpl implements DocPipelineService {

    //任务状态常量
    private static final String STATUS_RUNNING = "RUNNING";   //任务正在执行
    private static final String STATUS_SUCCESS = "SUCCESS";   //任务成功完成
    private static final String STATUS_FAILED = "FAILED";     //任务执行失败
    private static final String STATUS_SKIPPED = "SKIPPED";   //任务被跳过（如没有 Java 文件变更）
    private static final String STRUCTURED_DOC_SUFFIX = ".json"; //结构化文档文件后缀

    //数据库操作 Mapper
    private final DocTaskMapper docTaskMapper;    //文档任务表操作
    private final DocFileMapper docFileMapper;    //文档文件明细表操作
    //GitLab API 客户端，用于读取远程仓库的 commit 和文件内容
    private final GitLabDocClient gitLabDocClient;
    //文档生成器注册表，根据文件后缀找到对应的生成器
    private final DocGeneratorRegistry docGeneratorRegistry;
    //用户工作空间路径解析器
    private final UserWorkspaceResolver userWorkspaceResolver;
    //WebSocket 终端消息推送客户端
    private final TerminalRelayClient terminalRelayClient;
    private final TerminalScriptTaskClient terminalScriptTaskClient;
    //JSON 序列化工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    //增量刷新文档：对比本地 HEAD 和远程 HEAD，找出新增 commit，为变更的 Java 文件生成文档
    //
    //整体流程：
    //  1. 打开本地 Git 仓库，切到目标分支
    //  2. 记录当前本地 HEAD（oldHead）
    //  3. 从远程拉取最新代码（git fetch + git pull）
    //  4. 记录拉取后的 HEAD（newHead）
    //  5. 如果 oldHead != newHead，说明有新 commit，计算两次 HEAD 之间的 diff
    //  6. 根据 diff 中变更的文件，调用文档生成器生成文档
    @Override
    public DocRefreshResult refresh(String gitlabUsername, String project, String branch, String token) {
        return refresh(gitlabUsername, project, branch, token, null);
    }

    @Override
    public DocRefreshResult refresh(String gitlabUsername, String project, String branch, String token,
            String terminalSessionId) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);
        validateToken(token);

        Path sourceRoot = resolveSourceRoot(gitlabUsername, project);
        if (sourceRoot == null) {
            throw new BusinessException(400, "Local repository not found for project: " + project);
        }

        String normalizedBranch = normalizeBranchName(branch);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("project", project);
        args.put("branch", normalizedBranch);
        args.put("username", gitlabUsername);
        args.put("repoDir", toScriptPath(sourceRoot));

        ScriptTaskRunResult scriptResult = terminalScriptTaskClient.run(
                "REFRESH_DOC",
                terminalSessionId,
                args,
                Map.of("GITLAB_TOKEN", token.trim()),
                600);
        if (scriptResult.getExitCode() != 0) {
            throw toRefreshScriptException(scriptResult);
        }

        String oldHead = terminalScriptTaskClient.requireResult(
                scriptResult,
                "OLD_HEAD",
                "Refresh script did not report OLD_HEAD");
        String newHead = terminalScriptTaskClient.requireResult(
                scriptResult,
                "NEW_HEAD",
                "Refresh script did not report NEW_HEAD");

        DocRefreshResult result = new DocRefreshResult();
        result.setGitlabUsername(gitlabUsername);
        result.setProject(project);
        result.setBranch(normalizedBranch);
        result.setBaselineCommit(oldHead);
        result.setHeadCommit(newHead);

        if (Objects.equals(oldHead, newHead)) {
            result.setMessage("No new commits.");
            return result;
        }

        List<String> detectedCommitIds = gitLabDocClient.listCommitIdsSince(token, project, oldHead, newHead);
        if (detectedCommitIds.isEmpty()) {
            detectedCommitIds = List.of(newHead);
        }
        List<CommitFileChange> changes = gitLabDocClient.listFileChangesBetween(token, project, oldHead, newHead);

        result.setDetectedCommitIds(detectedCommitIds);
        result.setNewCommitCount(detectedCommitIds.size());

        String status = runLocalExtractionTask(gitlabUsername, project, normalizedBranch, newHead, sourceRoot, changes);
        if (STATUS_FAILED.equals(status)) {
            result.getFailedTaskCommitIds().add(newHead);
        } else {
            result.getCreatedTaskCommitIds().add(newHead);
        }

        result.setMessage(String.format(
                "Detected %d commit(s), created %d task(s), skipped %d task(s), failed %d task(s).",
                result.getNewCommitCount(),
                result.getCreatedTaskCommitIds().size(),
                result.getSkippedCommitIds().size(),
                result.getFailedTaskCommitIds().size()));
        return result;
    }

    @Override
    public void rebuild(String gitlabUsername, String project, String branch, String commitId, String token) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);
        if (!StringUtils.hasText(commitId)) {
            throw new BusinessException(400, "commitId is required for rebuild");
        }

        //通过 GitLab API 获取该 commit 的文件变更，然后为每个变更的文件重新生成文档
        String status = runExtractionTask(gitlabUsername, project, branch, commitId, token);
        if (STATUS_FAILED.equals(status)) {
            throw new BusinessException(500, "Rebuild failed for commit: " + commitId);
        }
    }

    //无终端输出的重载版本，直接调用带终端参数的版本（sessionId 传 null）
    @Override
    public DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch) {
        return scanLocal(gitlabUsername, project, branch, null);
    }

    //本地全量扫描并生成文档
    //与 refresh 的区别：
    //  - refresh: 增量，只处理新 commit 的变更文件，通过 git pull 拉取最新代码
    //  - scanLocal: 全量，扫描本地仓库中所有文件（不拉取远程），为每个支持的文件生成文档
    //适用场景：首次克隆仓库后，需要一次性为所有 Java 文件生成文档
    //
    //整体流程：
    //  1. 遍历本地仓库中的所有文件（跳过 .git 目录和 .gitignore 中的文件）
    //  2. 过滤出支持的文件类型（如 .java）
    //  3. 对每个文件调用文档生成器（如 JavaDocGenerator）
    //  4. 将生成结果存入数据库
    @Override
    public DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch,
            String terminalSessionId) {
        validateGitlabUsername(gitlabUsername);
        validateProjectAndBranch(project, branch);

        //向前端终端发送进度消息（如果 sessionId 不为空）
        emitTerminal(terminalSessionId, "[doc] local scan accepted, project=" + project + ", branch=" + branch);
        //找到本地仓库目录
        Path sourceRoot = resolveSourceRoot(gitlabUsername, project);
        if (sourceRoot == null) {
            emitTerminal(terminalSessionId, "[doc] local scan failed, repository not found: " + project);
            throw new BusinessException(400, "Local repository not found for project: " + project);
        }

        //获取当前 HEAD 的 commit hash（不拉取远程，直接读本地 .git/HEAD）
        String commitId = resolveLocalHeadCommit(sourceRoot);
        emitTerminal(terminalSessionId, "[doc] local scan repository=" + sourceRoot + ", HEAD=" + commitId);

        //组装扫描结果对象（最终返回给调用方）
        DocLocalScanResult result = new DocLocalScanResult();
        result.setGitlabUsername(gitlabUsername);
        result.setProject(project);
        result.setBranch(branch);
        result.setCommitId(commitId);
        result.setLocalRepoPath(sourceRoot.toString());

        //在数据库中创建一条文档任务记录（状态为 RUNNING）
        //这个任务记录用于追踪本次扫描的状态和耗时
        DocTask task = new DocTask();
        task.setEventId(buildTaskEventId("doc-local-scan", commitId));
        task.setGitlabUsername(gitlabUsername);
        task.setProject(project);
        task.setBranch(branch);
        task.setCommitId(commitId);
        task.setStatus(STATUS_RUNNING);
        task.setDuration(0);
        docTaskMapper.insert(task);

        //记录开始时间，用于计算耗时
        long start = System.currentTimeMillis();
        try {
            //遍历本地仓库中的所有文件（排除 .git 目录和 .gitignore 中的文件）
            List<Path> files = listLocalRepoFiles(sourceRoot);
            result.setScannedFileCount(files.size());
            emitTerminal(terminalSessionId, "[doc] local scan found " + files.size() + " file(s)");

            //逐个文件处理
            for (Path file : files) {
                //将绝对路径转为相对于仓库根的路径（如 src/main/java/Demo.java）
                String filePath = toRepoRelativePath(sourceRoot, file);
                //检查是否有对应的文档生成器（如 .java 文件有 JavaDocGenerator）
                if (!isSupportedDocFile(filePath)) {
                    //不支持的文件类型跳过（如 .xml、.yml、图片等）
                    result.setSkippedFileCount(result.getSkippedFileCount() + 1);
                    continue;
                }

                try {
                    //调用文档生成器生成文档（执行 javadoc 命令 -> 解析 HTML -> 输出 JSON）
                    generateLocalDoc(gitlabUsername, project, branch, commitId, task.getId(), sourceRoot, file,
                            filePath);
                    result.setGeneratedFileCount(result.getGeneratedFileCount() + 1);
                    result.getGeneratedFilePaths().add(filePath);
                    emitTerminal(terminalSessionId, "[doc] generated " + filePath);
                } catch (Exception ex) {
                    //单个文件生成失败不影响其他文件，记录错误继续处理下一个
                    log.error("Local doc generation failed. project={}, branch={}, filePath={}",
                            project, branch, filePath, ex);
                    emitTerminal(terminalSessionId, "[doc] failed " + filePath + ": " + summarizeError(ex));
                    result.setFailedFileCount(result.getFailedFileCount() + 1);
                    result.getFailedFilePaths().add(filePath);
                    //将失败记录也写入数据库（状态为 FAILED，记录错误信息）
                    upsertDocFile(gitlabUsername, project, branch, filePath, commitId, task.getId(),
                            STATUS_FAILED, null, summarizeError(ex));
                }
            }

            //根据扫描结果确定最终状态：有失败 -> FAILED，有生成 -> SUCCESS，否则 -> SKIPPED
            String finalStatus = localScanStatus(result);
            task.setStatus(finalStatus);
            //计算耗时（毫秒转秒）
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            //更新数据库中的任务状态
            docTaskMapper.updateById(task);
            //组装汇总消息
            result.setMessage(String.format(
                    "Scanned %d file(s), generated %d doc(s), skipped %d file(s), failed %d file(s).",
                    result.getScannedFileCount(),
                    result.getGeneratedFileCount(),
                    result.getSkippedFileCount(),
                    result.getFailedFileCount()));
            emitTerminal(terminalSessionId, "[doc] local scan completed, scanned="
                    + result.getScannedFileCount() + ", generated=" + result.getGeneratedFileCount()
                    + ", skipped=" + result.getSkippedFileCount() + ", failed=" + result.getFailedFileCount());
            return result;
        } catch (Exception ex) {
            //整体扫描失败（如仓库目录损坏等严重错误）
            log.error("Local doc scan failed. project={}, branch={}, commitId={}", project, branch, commitId, ex);
            emitTerminal(terminalSessionId, "[doc] local scan failed: " + summarizeError(ex));
            task.setStatus(STATUS_FAILED);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            //如果是运行时异常直接抛出，否则包装成 BusinessException
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException(500, "Local doc scan failed");
        }
    }

    //查询已生成的文档记录
    //支持按 gitlabUsername（必填）、project（必填）、branch、filePath、commitId 过滤
    //
    //查询逻辑的两种模式：
    //  1. 指定了 commitId：返回该 commit 下的所有文档记录（精确查询）
    //  2. 未指定 commitId：对每个文件只返回最新的文档记录（去重查询）
    //     因为同一个文件可能有多个 commit 的文档，用户通常只关心最新的
    @Override
    public List<DocQueryItem> query(String gitlabUsername, String project, String branch, String filePath,
            String commitId) {
        validateGitlabUsername(gitlabUsername);
        if (!StringUtils.hasText(project)) {
            throw new BusinessException(400, "project is required");
        }

        //使用 LambdaQueryWrapper 构建 SQL 查询条件
        //等价于 SQL: SELECT * FROM doc_file_dtl WHERE gitlab_username = ? AND project_name = ? [AND ...]
        LambdaQueryWrapper<DocFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocFile::getGitlabUsername, gitlabUsername)
                .eq(DocFile::getProjectName, project);
        //以下条件都是可选的，有值才加上
        if (StringUtils.hasText(branch)) {
            wrapper.eq(DocFile::getBranchName, branch);
        }
        if (StringUtils.hasText(filePath)) {
            wrapper.eq(DocFile::getFilePath, filePath);
        }
        if (StringUtils.hasText(commitId)) {
            wrapper.eq(DocFile::getCommitId, commitId);
        }
        //按 ID 降序排列（ID 越大越新）
        wrapper.orderByDesc(DocFile::getId);

        //执行查询
        List<DocFile> rows = docFileMapper.selectList(wrapper);

        //模式1：指定了 commitId，直接返回所有结果
        if (StringUtils.hasText(commitId)) {
            return rows.stream().map(this::toQueryItem).collect(Collectors.toList());
        }

        //模式2：未指定 commitId，对每个文件只保留最新的记录
        //使用 LinkedHashMap 保持插入顺序（ID 降序），putIfAbsent 确保只保留第一条（即最新的）
        Map<String, DocFile> latestByFile = new LinkedHashMap<>();
        for (DocFile row : rows) {
            String key = row.getBranchName() + "|" + row.getFilePath();
            latestByFile.putIfAbsent(key, row);
        }

        //过滤掉没有文档文件路径的记录（说明文档生成失败了），转换为查询结果 DTO
        return latestByFile.values().stream()
                .filter(row -> StringUtils.hasText(row.getDocFilePath()))
                .map(this::toQueryItem)
                .collect(Collectors.toList());
    }

    //远程文档提取任务：通过 GitLab API 获取某个 commit 的文件变更，然后生成文档
    //被 rebuild() 方法调用，处理流程：
    //  1. 在数据库中创建一条任务记录（状态 RUNNING）
    //  2. 调用 GitLab API 获取该 commit 的文件变更列表
    //  3. 对每个变更的文件调用文档生成器
    //  4. 更新任务状态（SUCCESS/SKIPPED/FAILED）
    //
    //与 runLocalExtractionTask 的区别：这个方法通过 GitLab API 读取文件内容，不依赖本地仓库
    private String runExtractionTask(String gitlabUsername, String project, String branch, String commitId,
            String token) {
        //在数据库创建任务记录，用于追踪执行状态
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
            //通过 GitLab API 获取该 commit 相比其父 commit 的文件变更
            List<CommitFileChange> changes = gitLabDocClient.listCommitFileChanges(token, project, commitId);
            //遍历变更列表，为每个支持的文件生成文档
            int handledDocFiles = applyChanges(gitlabUsername, project, branch, commitId, task.getId(), token, changes);

            //没有处理任何文件 -> SKIPPED，否则 -> SUCCESS
            String finalStatus = handledDocFiles == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
            task.setStatus(finalStatus);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return finalStatus;
        } catch (Exception ex) {
            //异常时标记任务为 FAILED，但不向上抛异常（返回状态让调用方判断）
            log.error("Doc extraction failed. project={}, branch={}, commitId={}", project, branch, commitId, ex);
            task.setStatus(STATUS_FAILED);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return STATUS_FAILED;
        }
    }

    //本地文档提取任务：从本地 Git 仓库读取文件内容并生成文档
    //被 refresh() 方法调用，处理流程与 runExtractionTask 类似，但文件来源是本地仓库而非 GitLab API
    //
    //与 runExtractionTask 的区别：
    //  - runExtractionTask: 通过 GitLab API 读取文件内容（适合 rebuild 场景，不需要本地仓库）
    //  - runLocalExtractionTask: 从本地磁盘读取文件内容（适合 refresh 场景，本地已有最新代码）
    private String runLocalExtractionTask(String gitlabUsername,
            String project,
            String branch,
            String commitId,
            Path sourceRoot,
            List<CommitFileChange> changes) {
        //创建数据库任务记录
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
            //遍历变更列表，从本地仓库读取文件并生成文档
            int handledDocFiles = applyLocalChanges(gitlabUsername, project, branch, commitId, task.getId(), sourceRoot,
                    changes);

            String finalStatus = handledDocFiles == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
            task.setStatus(finalStatus);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return finalStatus;
        } catch (Exception ex) {
            log.error("Local doc extraction failed. project={}, branch={}, commitId={}", project, branch, commitId, ex);
            task.setStatus(STATUS_FAILED);
            task.setDuration((int) ((System.currentTimeMillis() - start) / 1000));
            docTaskMapper.updateById(task);
            return STATUS_FAILED;
        }
    }

    //处理本地文件变更列表：根据每个文件的变更类型（新增/修改/删除/重命名），调用对应的文档处理方法
    //
    //变更类型处理逻辑：
    //  - ADDED/MODIFIED: 文件是新增或修改的 -> 从本地仓库读取内容 -> 生成文档 -> 存入数据库
    //  - DELETED: 文件被删除 -> 在数据库中标记该文件的文档为"已删除"
    //  - RENAMED: 文件被重命名 -> 旧路径标记删除 + 新路径生成文档（相当于两个操作）
    //
    //返回值：成功处理的文件数量（用于判断最终状态是 SUCCESS 还是 SKIPPED）
    private int applyLocalChanges(String gitlabUsername,
            String project,
            String branch,
            String commitId,
            Long taskId,
            Path sourceRoot,
            List<CommitFileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (CommitFileChange change : changes) {
            switch (change.getChangeType()) {
                //新增或修改的文件：从本地读取内容并生成文档
                case ADDED, MODIFIED -> {
                    if (upsertActiveLocalDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId,
                            sourceRoot)) {
                        handled++;
                    } else {
                        //不支持的文件类型（如 .xml），记录日志跳过
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
                //删除的文件：在数据库标记文档为已删除
                case DELETED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(gitlabUsername, project, branch, change.getOldPath(), commitId, taskId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                }
                //重命名的文件：旧路径标记删除 + 新路径生成文档
                case RENAMED -> {
                    if (isSupportedDocFile(change.getOldPath())) {
                        upsertDeletedDoc(gitlabUsername, project, branch, change.getOldPath(), commitId, taskId);
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getOldPath());
                    }
                    if (upsertActiveLocalDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId,
                            sourceRoot)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
            }
        }

        return handled;
    }

    //处理远程文件变更列表：通过 GitLab API 读取文件内容并生成文档
    //逻辑与 applyLocalChanges 完全一致，唯一区别是文件内容来源：
    //  - applyLocalChanges: 从本地磁盘读取（Path sourceRoot）
    //  - applyChanges: 通过 GitLab API 读取（String token）
    //
    //这两个方法是"策略模式"的体现：同样的变更处理逻辑，不同的文件读取方式
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
                    if (upsertActiveDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId,
                            token)) {
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
                    if (upsertActiveDoc(gitlabUsername, project, branch, change.getNewPath(), commitId, taskId,
                            token)) {
                        handled++;
                    } else {
                        logUnsupportedFile(project, commitId, change.getNewPath());
                    }
                }
            }
        }

        return handled;
    }

    //为远程仓库中的活跃文件（新增或修改）生成文档
    //处理流程：
    //  1. 根据文件后缀查找对应的文档生成器（如 .java -> JavaDocGenerator）
    //  2. 如果没有匹配的生成器，返回 false（调用方会记录"不支持"日志）
    //  3. 通过 GitLab API 读取文件的源代码内容
    //  4. 调用文档生成器生成结构化文档（如执行 javadoc -> 解析 HTML -> 输出 JSON）
    //  5. 将文档记录写入数据库（upsert：存在则更新，不存在则插入）
    //
    //返回 true 表示成功处理，false 表示文件类型不支持
    private boolean upsertActiveDoc(String gitlabUsername,
            String project,
            String branch,
            String filePath,
            String commitId,
            Long taskId,
            String token) {
        //步骤1：查找文档生成器
        DocGenerator generator = docGeneratorRegistry.findGenerator(filePath).orElse(null);
        if (generator == null) {
            return false; //没有对应的生成器（如 .xml 文件），跳过
        }

        //步骤2：通过 GitLab API 读取文件的源代码内容
        String fileContent = gitLabDocClient.readFileContent(token, project, filePath, commitId);
        //步骤3：调用文档生成器（如 JavaDocGenerator）生成结构化文档
        DocGenerationResult result = generator.generate(DocGenerationContext.builder()
                .project(project)
                .branch(branch)
                .commitId(commitId)
                .filePath(filePath)
                .sourceContent(fileContent)
                .sourceRoot(resolveSourceRoot(gitlabUsername, project))
                .outputRoot(resolveDocOutputRoot(gitlabUsername))
                .build());

        //步骤4：将生成结果写入数据库
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, result.getDocFilePath(), null);
        return true;
    }

    //为本地仓库中的活跃文件（新增或修改）生成文档
    //与 upsertActiveDoc 的区别：文件内容从本地磁盘读取，而非通过 GitLab API
    //
    //处理流程：
    //  1. 查找文档生成器
    //  2. 拼接本地文件的绝对路径（sourceRoot + filePath）
    //  3. 检查文件是否存在（git pull 后文件应该在本地）
    //  4. 从本地磁盘读取文件内容
    //  5. 调用文档生成器生成结构化文档
    //  6. 将文档记录写入数据库
    private boolean upsertActiveLocalDoc(String gitlabUsername,
            String project,
            String branch,
            String filePath,
            String commitId,
            Long taskId,
            Path sourceRoot) {
        //步骤1：查找文档生成器
        DocGenerator generator = docGeneratorRegistry.findGenerator(filePath).orElse(null);
        if (generator == null) {
            return false;
        }

        //步骤2：拼接本地文件路径并检查文件是否存在
        Path sourceFile = resolveRepoFile(sourceRoot, filePath);
        if (!Files.isRegularFile(sourceFile)) {
            throw new BusinessException(400, "Local source file not found after pull: " + filePath);
        }

        //步骤3：从本地磁盘读取文件内容
        String fileContent;
        try {
            fileContent = Files.readString(sourceFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(500, "Failed to read local source file: " + filePath);
        }
        //步骤4：调用文档生成器
        DocGenerationResult result = generator.generate(DocGenerationContext.builder()
                .project(project)
                .branch(branch)
                .commitId(commitId)
                .filePath(filePath)
                .sourceContent(fileContent)
                .sourceRoot(sourceRoot)
                .outputRoot(resolveDocOutputRoot(gitlabUsername))
                .build());

        //步骤5：将生成结果写入数据库
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, result.getDocFilePath(), null);
        return true;
    }

    //为单个本地文件生成文档（被 scanLocal 方法调用）
    //与 upsertActiveLocalDoc 类似，但有两个区别：
    //  1. 不返回 boolean（不支持的文件直接 return，不抛异常）
    //  2. 接收的是已经解析好的 sourceFile 绝对路径（Path），不需要再 resolve
    //  3. 方法签名声明了 throws IOException，由调用方处理
    private void generateLocalDoc(String gitlabUsername,
            String project,
            String branch,
            String commitId,
            Long taskId,
            Path sourceRoot,
            Path sourceFile,
            String filePath) throws IOException {
        //查找文档生成器，不支持的文件类型直接跳过
        DocGenerator generator = docGeneratorRegistry.findGenerator(filePath).orElse(null);
        if (generator == null) {
            return;
        }

        //从本地磁盘读取文件内容并生成文档
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

        //将生成结果写入数据库
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, result.getDocFilePath(), null);
    }

    //标记已删除文件的文档状态
    //当文件被删除时，不会删除数据库记录（保留历史），而是将 docFilePath 设为 null，parseErrorMsg 设为 "File deleted"
    private void upsertDeletedDoc(String gitlabUsername, String project, String branch, String filePath,
            String commitId, Long taskId) {
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, null, "File deleted");
    }

    //upsertDocFile 的简化版本：默认状态为 SUCCESS
    //被 upsertActiveDoc、upsertActiveLocalDoc、generateLocalDoc 调用
    private void upsertDocFile(String gitlabUsername,
            String project,
            String branch,
            String filePath,
            String commitId,
            Long taskId,
            String docFilePath,
            String parseErrorMsg) {
        upsertDocFile(gitlabUsername, project, branch, filePath, commitId, taskId, STATUS_SUCCESS, docFilePath,
                parseErrorMsg);
    }

    //文档文件记录的核心 upsert 方法（insert or update）
    //
    //upsert 逻辑：先按 (username, project, branch, filePath, commitId) 查询数据库
    //  - 记录不存在 -> INSERT 新记录
    //  - 记录已存在 -> UPDATE 已有记录（更新 taskId、docFilePath、parseStatus、parseErrorMsg）
    //
    //为什么用 upsert 而不是先 delete 再 insert？
    //  因为同一条记录可能被多次刷新，upsert 可以避免产生重复记录，也保留了记录的自增 ID（便于追踪）
    //
    //参数说明：
    //  - parseStatus: 文档解析状态（SUCCESS/FAILED）
    //  - docFilePath: 生成的结构化文档文件路径（如 doc.json 的绝对路径），删除时为 null
    //  - parseErrorMsg: 解析错误信息，成功时为 null
    private void upsertDocFile(String gitlabUsername,
            String project,
            String branch,
            String filePath,
            String commitId,
            Long taskId,
            String parseStatus,
            String docFilePath,
            String parseErrorMsg) {
        //按唯一键组合查询是否已存在记录
        LambdaQueryWrapper<DocFile> query = new LambdaQueryWrapper<>();
        query.eq(DocFile::getGitlabUsername, gitlabUsername)
                .eq(DocFile::getProjectName, project)
                .eq(DocFile::getBranchName, branch)
                .eq(DocFile::getFilePath, filePath)
                .eq(DocFile::getCommitId, commitId)
                .last("LIMIT 1"); //只取一条，提高性能

        DocFile existing = docFileMapper.selectOne(query);
        if (existing == null) {
            //记录不存在，插入新记录
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

        //记录已存在，更新字段
        existing.setTaskId(taskId);
        existing.setDocFilePath(docFilePath);
        existing.setParseStatus(parseStatus);
        existing.setParseErrorMsg(parseErrorMsg);
        docFileMapper.updateById(existing);
    }

    //生成任务的唯一事件 ID，格式：{prefix}-{commitId}-{nanoTime的36进制}
    //例如："doc-refresh-a1b2c3d4-k5m6n7p8"
    //使用 System.nanoTime() 确保同一 commit 的多次刷新也有不同的 eventId
    //36 进制（0-9 + a-z）让 ID 更短更易读
    private String buildTaskEventId(String prefix, String commitId) {
        String safeCommitId = StringUtils.hasText(commitId) ? commitId.trim() : "unknown";
        return prefix + "-" + safeCommitId + "-" + Long.toString(System.nanoTime(), 36);
    }

    // Resolve a repository-relative file path without allowing traversal outside the repo.
    private Path resolveRepoFile(Path sourceRoot, String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new BusinessException(400, "Invalid file path from local diff");
        }

        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(filePath).normalize();
        //安全检查：确保解析后的路径仍在仓库目录内
        if (!resolved.startsWith(normalizedRoot)) {
            throw new BusinessException(400, "Invalid file path from local diff: " + filePath);
        }
        return resolved;
    }

    // Normalize branch names so downstream code sees "main" rather than "refs/heads/main".
    private String normalizeBranchName(String branch) {
        String normalized = branch.trim();
        if (normalized.startsWith("refs/heads/")) {
            return normalized.substring("refs/heads/".length());
        }
        return normalized;
    }

    //检查文件是否有对应的文档生成器（即是否是支持的文件类型）
    //委托给 DocGeneratorRegistry，它会根据文件后缀查找匹配的 DocGenerator
    //例如：.java 文件返回 true（有 JavaDocGenerator），.xml 文件返回 false
    private boolean isSupportedDocFile(String path) {
        return docGeneratorRegistry.supports(path);
    }

    //记录不支持文件类型的日志（warn 级别）
    //当遇到不支持的文件类型时，跳过处理并记录日志，方便排查问题
    private void logUnsupportedFile(String project, String commitId, String filePath) {
        if (StringUtils.hasText(filePath)) {
            log.warn("Skip unsupported doc file. project={}, commitId={}, filePath={}", project, commitId, filePath);
        }
    }

    //遍历本地仓库中的所有文件（用于 scanLocal 的全量扫描）
    //
    //过滤规则（按优先级）：
    //  1. 跳过 .git 目录（Git 内部数据，不是源代码）
    //  2. 跳过 .gitignore 中忽略的文件（如 node_modules、target 等）
    //  3. 只保留普通文件（排除目录、符号链接等）
    //
    //使用 Java NIO 的 Files.walkFileTree 遍历目录树
    //通过 SimpleFileVisitor 回调控制遍历行为：
    //  - preVisitDirectory: 进入目录前决定是否跳过（可以跳过整个子树）
    //  - visitFile: 访问每个文件时决定是否加入结果列表
    private List<Path> listLocalRepoFiles(Path sourceRoot) throws IOException {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        Path gitDir = normalizedRoot.resolve(".git").normalize();
        //加载 .gitignore 规则，用于判断文件是否被忽略
        GitIgnoreMatcher gitIgnoreMatcher = GitIgnoreMatcher.load(normalizedRoot);
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalizedDir = dir.toAbsolutePath().normalize();
                //跳过 .git 目录及其所有子目录
                if (normalizedDir.equals(gitDir) || normalizedDir.startsWith(gitDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                //跳过 .gitignore 中忽略的目录（如 target/、node_modules/）
                if (!normalizedDir.equals(normalizedRoot) && gitIgnoreMatcher.isIgnored(normalizedDir, true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path normalizedFile = file.toAbsolutePath().normalize();
                //只保留普通文件，排除 .git 目录下的文件和 .gitignore 中忽略的文件
                if (attrs.isRegularFile()
                        && !normalizedFile.equals(gitDir)
                        && !normalizedFile.startsWith(gitDir)
                        && !gitIgnoreMatcher.isIgnored(normalizedFile, false)) {
                    files.add(normalizedFile);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        //按相对路径排序，保证处理顺序一致（方便日志追踪和调试）
        return files.stream()
                .sorted(Comparator.comparing(path -> toRepoRelativePath(normalizedRoot, path)))
                .collect(Collectors.toList());
    }

    //将绝对路径转为相对于仓库根目录的路径
    //例如：sourceRoot="/repo"，file="/repo/src/main/java/Demo.java" -> "src/main/java/Demo.java"
    //同时将 Windows 的反斜杠 (\) 统一替换为正斜杠 (/)，保证跨平台一致性
    private String toRepoRelativePath(Path sourceRoot, Path file) {
        return sourceRoot.toAbsolutePath().normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    //根据本地扫描结果确定最终状态
    //规则：有任何失败 -> FAILED，有生成成功 -> SUCCESS，否则 -> SKIPPED
    private String localScanStatus(DocLocalScanResult result) {
        if (result.getFailedFileCount() > 0) {
            return STATUS_FAILED;
        }
        return result.getGeneratedFileCount() == 0 ? STATUS_SKIPPED : STATUS_SUCCESS;
    }

    //读取本地 Git 仓库当前 HEAD 的 commit hash（不使用 JGit，直接读 .git 目录下的文件）
    //
    //Git 的 HEAD 文件有两种内容：
    //  1. "ref: refs/heads/main" —— 表示当前在 main 分支上（符号引用）
    //  2. "a1b2c3d4..." —— 表示当前处于 detached HEAD 状态（直接指向某个 commit）
    //
    //对于情况1（最常见），需要进一步读取引用文件获取 commit hash：
    //  - 先尝试读取 .git/refs/heads/main 文件
    //  - 如果文件不存在（Git 会打包引用到 packed-refs 文件），再读取 .git/packed-refs
    private String resolveLocalHeadCommit(Path sourceRoot) {
        try {
            //找到 .git 目录（可能是目录，也可能是文件——worktree 场景下 .git 是文件）
            Path gitDir = resolveGitDir(sourceRoot);
            Path headFile = gitDir.resolve("HEAD");
            if (!Files.isRegularFile(headFile)) {
                throw new BusinessException(400, "Local repository HEAD not found: " + sourceRoot);
            }

            //读取 .git/HEAD 的内容
            String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            //如果不是符号引用（detached HEAD），直接返回 commit hash
            if (!head.startsWith("ref:")) {
                if (StringUtils.hasText(head)) {
                    return head;
                }
                throw new BusinessException(400, "Local repository HEAD is empty: " + sourceRoot);
            }

            //是符号引用，解析引用名（如 "refs/heads/main"）
            String refName = head.substring("ref:".length()).trim();
            //尝试读取引用文件（如 .git/refs/heads/main）
            Path refFile = gitDir.resolve(refName).normalize();
            if (refFile.startsWith(gitDir) && Files.isRegularFile(refFile)) {
                String commitId = Files.readString(refFile, StandardCharsets.UTF_8).trim();
                if (StringUtils.hasText(commitId)) {
                    return commitId;
                }
            }

            //引用文件不存在，尝试从 packed-refs 文件中查找
            //Git 会定期将散落的引用文件打包到 packed-refs 文件中以节省空间
            String packedCommit = readPackedRef(gitDir, refName);
            if (StringUtils.hasText(packedCommit)) {
                return packedCommit;
            }
            throw new BusinessException(400, "Local repository ref not found: " + refName);
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read local repository HEAD");
        }
    }

    //解析 .git 目录的实际路径
    //
    //Git 的 .git 有两种形式：
    //  1. 普通仓库：.git 是一个目录，包含所有 Git 数据
    //  2. Git worktree（工作树）：.git 是一个文件，内容格式为 "gitdir: /path/to/.git/worktrees/xxx"
    //     worktree 是 Git 的一个功能，允许在多个目录中同时检出同一个仓库的不同分支
    //
    //这个方法统一处理两种情况，返回实际的 .git 数据目录
    private Path resolveGitDir(Path sourceRoot) throws IOException {
        Path gitPath = sourceRoot.toAbsolutePath().normalize().resolve(".git");
        //情况1：.git 是目录（普通仓库）
        if (Files.isDirectory(gitPath)) {
            return gitPath;
        }
        //情况2：.git 是文件（worktree）
        if (Files.isRegularFile(gitPath)) {
            String gitFile = Files.readString(gitPath, StandardCharsets.UTF_8).trim();
            if (gitFile.startsWith("gitdir:")) {
                //提取实际的 git 目录路径
                Path gitDir = Path.of(gitFile.substring("gitdir:".length()).trim());
                //如果是相对路径，相对于仓库根目录解析
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

    //从 Git 的 packed-refs 文件中查找指定引用对应的 commit hash
    //
    //什么是 packed-refs？
    //  Git 会定期执行 "git pack-refs" 命令，将散落在 refs/ 目录下的引用文件
    //  合并到一个 packed-refs 文件中，以节省磁盘空间和提高性能
    //  文件格式：每行一个引用，格式为 "{commitHash} {refName}"
    //  例如：a1b2c3d4e5f6 refs/heads/main
    //
    //  以 # 开头的是注释行，以 ^ 开头的是 peeled tag（剥离的标签），都跳过
    private String readPackedRef(Path gitDir, String refName) throws IOException {
        Path packedRefs = gitDir.resolve("packed-refs");
        if (!Files.isRegularFile(packedRefs)) {
            return null;
        }

        for (String line : Files.readAllLines(packedRefs, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            //跳过空行、注释行、peeled tag
            if (!StringUtils.hasText(trimmed) || trimmed.startsWith("#") || trimmed.startsWith("^")) {
                continue;
            }
            //按空白字符分割为两部分：commit hash 和引用名
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2 && refName.equals(parts[1])) {
                return parts[0]; //返回 commit hash
            }
        }
        return null; //没找到
    }

    //截取异常信息的前 500 个字符作为错误摘要
    //用于日志记录和终端输出，避免过长的堆栈信息
    //如果没有异常消息，使用异常类名作为摘要
    private String summarizeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    //解析本地仓库的根目录路径
    //路径格式：{baseDir}/workspace/{username}/repos/project-{projectId}
    //如果目录不存在（仓库未克隆），返回 null
    //
    //注意：project 必须是纯数字（GitLab 项目 ID），否则返回 null
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

    //获取文档输出的根目录路径
    //路径格式：{baseDir}/workspace/{username}/docs
    //所有生成的结构化文档都存放在这个目录下
    private Path resolveDocOutputRoot(String gitlabUsername) {
        return userWorkspaceResolver.docOutputRoot(gitlabUsername);
    }

    //将数据库记录（DocFile）转换为查询结果 DTO（DocQueryItem）
    //除了复制基本字段外，还会读取结构化文档的 JSON 内容（readStructuredDoc）
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
        //读取结构化文档的 JSON 文件并反序列化为 DocStructuredContent 对象
        item.setStructuredDoc(readStructuredDoc(row));
        item.setUpdateTime(row.getUpdateTime());
        return item;
    }

    //读取结构化文档的 JSON 文件并反序列化为 DocStructuredContent 对象
    //这个 JSON 文件是文档生成器（如 JavaDocGenerator）生成的最终产物
    //
    //安全检查：
    //  1. 只有状态为 SUCCESS 且 docFilePath 有值的记录才尝试读取
    //  2. docFilePath 必须在用户的文档输出目录内（防止路径穿越读取其他用户的文件）
    //  3. 文件必须是 .json 后缀（防止读取非文档文件）
    //  4. 文件必须存在（可能被手动删除）
    private DocStructuredContent readStructuredDoc(DocFile row) {
        if (!STATUS_SUCCESS.equals(row.getParseStatus()) || !StringUtils.hasText(row.getDocFilePath())) {
            return null;
        }

        //安全检查：确保 docFilePath 在用户的文档输出目录内
        Path docPath = Path.of(row.getDocFilePath()).toAbsolutePath().normalize();
        Path outputRoot = resolveDocOutputRoot(row.getGitlabUsername()).toAbsolutePath().normalize();
        if (!docPath.startsWith(outputRoot)
                || !docPath.getFileName().toString().endsWith(STRUCTURED_DOC_SUFFIX)
                || !Files.isRegularFile(docPath)) {
            log.warn("Skip unsafe or missing structured doc path. username={}, project={}, filePath={}, docPath={}",
                    row.getGitlabUsername(), row.getProjectName(), row.getFilePath(), row.getDocFilePath());
            return null;
        }

        //反序列化 JSON 文件为 Java 对象
        try {
            return objectMapper.readValue(docPath.toFile(), DocStructuredContent.class);
        } catch (IOException ex) {
            log.warn("Failed to read structured doc JSON. username={}, project={}, filePath={}, docPath={}",
                    row.getGitlabUsername(), row.getProjectName(), row.getFilePath(), row.getDocFilePath(), ex);
            return null;
        }
    }

    //向 WebSocket 终端发送进度消息
    //如果 sessionId 为空（没有连接终端），则不发送
    //这些消息会在前端的终端界面实时显示，让用户看到文档生成的进度
    private void emitTerminal(String sessionId, String line) {
        terminalRelayClient.emit(sessionId, line);
    }

    private String toScriptPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private BusinessException toRefreshScriptException(ScriptTaskRunResult result) {
        if (result.isTimedOut()) {
            return new BusinessException(500, "Local repository refresh timed out");
        }
        String output = summarizeScriptOutput(result);
        String lower = output.toLowerCase();
        if (lower.contains("authentication") || lower.contains("permission denied")
                || lower.contains("could not read username") || lower.contains("access denied")) {
            return new BusinessException(401, "Git fetch/pull failed, please check token and repository permissions");
        }
        return new BusinessException(500, "Local repository refresh failed: " + output);
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

    //参数校验：project 和 branch 都不能为空
    private void validateProjectAndBranch(String project, String branch) {
        if (!StringUtils.hasText(project) || !StringUtils.hasText(branch)) {
            throw new BusinessException(400, "project and branch are required");
        }
    }

    //参数校验：GitLab Personal Access Token 不能为空
    private void validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "token is required");
        }
    }

    //参数校验：GitLab 用户名不能为空
    private void validateGitlabUsername(String gitlabUsername) {
        if (!StringUtils.hasText(gitlabUsername)) {
            throw new BusinessException(400, "GitLab username is required");
        }
    }
}
