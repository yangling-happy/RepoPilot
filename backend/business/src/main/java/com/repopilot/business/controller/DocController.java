package com.repopilot.business.controller;

import com.repopilot.business.dto.CreateDocFileRequest;
import com.repopilot.business.dto.CreateDocTaskRequest;
import com.repopilot.business.dto.DocLocalScanRequest;
import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocRefreshRequest;
import com.repopilot.business.dto.DocRefreshResult;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.business.service.DocPipelineService;
import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.business.service.gitlab.GitLabUserContext;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.util.BizAssert;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

//Lombok 注解，编译后自动生成 private static final Logger log = ...，
//可直接用 log.info(...) 记录日志
@Slf4j
@RestController
@RequestMapping("/doc")
//Lombok 注解，自动生成包含 final 字段的构造函数，Spring 会通过这个构造函数注入私有成员
@RequiredArgsConstructor
public class DocController {

    //文档任务允许写入数据库的状态集合
    //手动创建任务时会校验这个集合，避免出现拼写错误的状态值
    private static final Set<String> ALLOWED_TASK_STATUS = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "SKIPPED");
    //单个文档文件解析结果允许的状态集合
    //DocFile 记录的是“某个源文件对应的文档产物是否生成/解析成功”
    private static final Set<String> ALLOWED_PARSE_STATUS = Set.of(
            "PENDING", "SUCCESS", "FAILED");
            
    //这四个final字段(值不可修改的变量)就是通过@RequiredArgsConstructor这个注解自动生成构造函数的
    //操作数据库中的文档任务和生成产物表
    private final DocTaskMapper docTaskMapper;
    private final DocFileMapper docFileMapper;
    //文档流水线服务，封装了实际的文档刷新、扫描、重建、查询等业务逻辑，是 Service 层的抽象
    private final DocPipelineService docPipelineService;
    //从 HttpSession 中提取 GitLab 用户上下文，确保请求已认证
    private final GitLabSessionContextService gitLabSessionContextService;

    @PostMapping("/refresh")
    public ApiResponse<DocRefreshResult> refreshDoc(@RequestBody DocRefreshRequest request,
            HttpSession session) {
        // 检验请求体非空
        BizAssert.notNull(request, 400, "Request body is required");
    
        // 从会话获取用户上下文
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
    
        log.info("Refresh doc request: username={}, project={}, branch={}",
                context.username(), request.getProject(), request.getBranch());

        // 调用 service 层执行刷新：先同步远程仓库，再扫描文档变更
        DocRefreshResult result = docPipelineService.refresh(
                context.username(), request.getProject(), request.getBranch(), context.token());

        return ApiResponse.success("Refresh completed", result);
    }

    //本地全量扫描接口
    //
    //它不会访问 GitLab 远程 API，而是直接读取当前用户工作空间中的本地仓库：
    //  1. 遍历仓库文件
    //  2. 跳过 .git 和 .gitignore 忽略的内容
    //  3. 为支持的文件类型生成结构化文档
    @PostMapping("/scan-local")
    public ApiResponse<DocLocalScanResult> scanLocalDoc(@RequestBody DocLocalScanRequest request,
            HttpSession session) {
        // 检验请求体非空
        BizAssert.notNull(request, 400, "Request body is required");
    
        // 从会话获取用户上下文
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
    
        log.info("Local doc scan request: username={}, project={}, branch={}",
                context.username(), request.getProject(), request.getBranch());
    
        // 扫描本地文件，不需要 GitLab token；
        // terminalSessionId 用于把扫描过程日志推送到前端终端面板
        DocLocalScanResult result = docPipelineService.scanLocal(
                context.username(),
                request.getProject(),
                request.getBranch(),
                request.getTerminalSessionId());
    
        return ApiResponse.success("Local scan completed", result);
    }

    //接收项目、分支、提交 ID，调用 docPipelineService.rebuild 重新生成文档
    //需要 Token，要从远程仓库重新拉取内容
    @PostMapping("/rebuild")
    public ApiResponse<Void> rebuildDoc(@RequestParam String project,
            @RequestParam String branch,
            @RequestParam String commitId,
            HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Rebuild doc request: username={}, project={}, branch={}, commitId={}",
                context.username(), project, branch, commitId);
        docPipelineService.rebuild(context.username(), project, branch, commitId, context.token());
        return ApiResponse.success("Rebuild triggered", null);
    }

    @GetMapping("/query")
    public ApiResponse<Object> queryDoc(@RequestParam String project,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String commitId,
            HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Query doc: username={}, project={}, branch={}, filePath={}, commitId={}",
                context.username(), project, branch, filePath, commitId);
        //返回结果直接包装到 ApiResponse 中
        return ApiResponse.success(docPipelineService.query(context.username(), project, branch, filePath, commitId));
    }

    //手动创建文档任务记录
    //
    //这类接口通常用于调试、外部系统回调或未来接入异步任务调度。
    //真正的文档流水线会自动创建任务；手动接口只负责把可信参数落库。
    @PostMapping("/task/create")
    public ApiResponse<DocTask> createDocTask(@RequestBody CreateDocTaskRequest request,
            HttpSession session) {
        //验证请求非空，统一进行错误处理
        String validationError = validateCreateDocTaskRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        
        //这里的DocTask是数据库实体，其作用是插入一条数据库数据，不能也不应该交给 Spring 容器管理
        //如果试图将其作为 Spring Bean 注入，那容器中只会有一个实例（默认单例），所有请求共享同一个对象，这会造成严重的数据混乱
        DocTask task = new DocTask();
        task.setGitlabUsername(context.username());
        task.setEventId(request.getEventId().trim());
        task.setProject(request.getProject().trim());
        task.setBranch(request.getBranch().trim());
        task.setCommitId(request.getCommitId().trim());
        task.setStatus(request.getStatus().trim().toUpperCase());
        task.setDuration(request.getDuration());

        BizAssert.affectedOne(docTaskMapper.insert(task), "Insert doc task failed");

        log.info("Doc task created successfully, username={}, id={}, eventId={}",
                task.getGitlabUsername(), task.getId(), task.getEventId());
        return ApiResponse.success("Doc task created", task);
    }

    @PostMapping("/file/create")
    public ApiResponse<DocFile> createDocFile(@RequestBody CreateDocFileRequest request,
            HttpSession session) {
        String validationError = validateCreateDocFileRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);

        //查询数据库确保 taskId 对应一条真实的任务记录，防止孤儿文件
        DocTask linkedTask = docTaskMapper.selectById(request.getTaskId());
        BizAssert.isTrue(linkedTask != null, 400, "taskId does not exist");
        //保证当前请求的用户只能给自己的任务创建文件，杜绝越权操作
        BizAssert.isTrue(context.username().equals(linkedTask.getGitlabUsername()), 400,
                "taskId does not belong to the current user");
        //确保传入的 projectName、branchName、commitId 与关联任务记录的对应字段完全一致
        BizAssert.isTrue(request.getProjectName().trim().equals(linkedTask.getProject())
                && request.getBranchName().trim().equals(linkedTask.getBranch())
                && request.getCommitId().trim().equals(linkedTask.getCommitId()),
                400, "taskId does not match project, branch, or commitId");

        DocFile docFile = new DocFile();
        docFile.setTaskId(request.getTaskId());
        docFile.setGitlabUsername(context.username());
        docFile.setProjectName(request.getProjectName().trim());
        docFile.setBranchName(request.getBranchName().trim());
        docFile.setFilePath(request.getFilePath().trim());
        docFile.setCommitId(request.getCommitId().trim());
        docFile.setDocFilePath(hasText(request.getDocFilePath()) ? request.getDocFilePath().trim() : null);
        docFile.setParseStatus(request.getParseStatus().trim().toUpperCase());
        docFile.setParseErrorMsg(hasText(request.getParseErrorMsg()) ? request.getParseErrorMsg().trim() : null);

        BizAssert.affectedOne(docFileMapper.insert(docFile), "Insert doc file failed");

        log.info("Doc file created successfully, username={}, id={}, filePath={}",
                docFile.getGitlabUsername(), docFile.getId(), docFile.getFilePath());
        return ApiResponse.success("Doc file created", docFile);
    }

    //校验手动创建文档任务的请求体
    //返回 null 表示通过；返回具体字符串表示错误原因
    private String validateCreateDocTaskRequest(CreateDocTaskRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (!hasText(request.getEventId())) {
            return "eventId is required";
        }
        if (!hasText(request.getProject())) {
            return "project is required";
        }
        if (!hasText(request.getBranch())) {
            return "branch is required";
        }
        if (!hasText(request.getCommitId())) {
            return "commitId is required";
        }
        if (!hasText(request.getStatus())) {
            return "status is required";
        }
        String normalizedStatus = request.getStatus().trim().toUpperCase();
        if (!ALLOWED_TASK_STATUS.contains(normalizedStatus)) {
            return "status must be one of: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED";
        }
        if (request.getDuration() != null && request.getDuration() < 0) {
            return "duration must be greater than or equal to 0";
        }
        return null;
    }

    //校验手动创建文档文件记录的请求体
    //
    //这里只做“字段格式”校验；taskId 是否存在、是否属于当前用户，
    //会在 createDocFile 主流程里查数据库后再校验。
    private String validateCreateDocFileRequest(CreateDocFileRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (request.getTaskId() == null) {
            return "taskId is required";
        }
        if (request.getTaskId() <= 0) {
            return "taskId must be greater than 0";
        }
        if (!hasText(request.getProjectName())) {
            return "projectName is required";
        }
        if (!hasText(request.getBranchName())) {
            return "branchName is required";
        }
        if (!hasText(request.getFilePath())) {
            return "filePath is required";
        }
        if (!hasText(request.getCommitId())) {
            return "commitId is required";
        }
        if (!hasText(request.getParseStatus())) {
            return "parseStatus is required";
        }
        String normalizedStatus = request.getParseStatus().trim().toUpperCase();
        if (!ALLOWED_PARSE_STATUS.contains(normalizedStatus)) {
            return "parseStatus must be one of: PENDING, SUCCESS, FAILED";
        }
        return null;
    }
}
