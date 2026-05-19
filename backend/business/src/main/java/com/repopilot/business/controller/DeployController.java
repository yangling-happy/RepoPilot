package com.repopilot.business.controller;

import com.repopilot.business.dto.CreateBuildTaskRequest;
import com.repopilot.business.dto.CreateDeployTaskRequest;
import com.repopilot.business.dto.DeployTriggerRequest;
import com.repopilot.business.dto.DeployTriggerResponse;
import com.repopilot.business.entity.BuildTask;
import com.repopilot.business.entity.DeployTask;
import com.repopilot.business.mapper.BuildTaskMapper;
import com.repopilot.business.mapper.DeployTaskMapper;
import com.repopilot.business.service.DeployPipelineService;
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
@RequestMapping("/deploy")
//Lombok 注解，自动生成包含 final 字段的构造函数，Spring 会通过这个构造函数注入私有成员
@RequiredArgsConstructor
public class DeployController {
    //部署/构建任务允许写入数据库的运行状态集合
    //Controller 在入库前先校验状态，避免把拼写错误或未知状态写进表里
    private static final Set<String> ALLOWED_RUN_STATUS = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");

    //这三个final字段(值不可修改的变量)就是通过@RequiredArgsConstructor这个注解自动生成构造函数的
    //操作数据库中的部署任务和构建任务表
    private final DeployTaskMapper deployTaskMapper;
    private final BuildTaskMapper buildTaskMapper;
    //从 HTTP 会话中获取当前 GitLab 用户上下文
    private final GitLabSessionContextService gitLabSessionContextService;
    //部署流水线服务，负责触发部署、查询任务、取消部署等
    private final DeployPipelineService deployPipelineService;

    //触发一次部署
    @PostMapping("/trigger")
    public ApiResponse<DeployTriggerResponse> triggerDeploy(@RequestBody DeployTriggerRequest request,
            HttpSession session) {
        //通过 gitLabSessionContextService.requireContext(session) 强制获取当前用户上下文
        //若未登录或无有效会话，此方法通常会抛异常，由全局异常处理器处理
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        DeployTriggerResponse response = deployPipelineService.trigger(request, context);
        log.info("Deploy trigger: username={}, deployTaskId={}", context.username(), response.getDeployTaskId());
        return ApiResponse.success("Deploy triggered", response);
    }

    @PostMapping("/task/create")
    public ApiResponse<DeployTask> createDeployTask(@RequestBody CreateDeployTaskRequest request,
                                                    HttpSession session) {
        //调用 validateCreateDeployTaskRequest，如存在错误返回400并携带提示信息
        String validationError = validateCreateDeployTaskRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        //获取用户上下文：从会话中取出 GitLabUserContext
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);

        //这里的deployTask是数据库实体，其作用是插入一条数据库数据，不能也不应该交给 Spring 容器管理
        //如果试图将其作为 Spring Bean 注入，那容器中只会有一个实例（默认单例），所有请求共享同一个对象，这会造成严重的数据混乱
        DeployTask deployTask = new DeployTask();
        deployTask.setGitlabUsername(context.username());
        deployTask.setDeployTaskId(request.getDeployTaskId().trim());
        deployTask.setProjectName(request.getProjectName().trim());
        deployTask.setBranchName(request.getBranchName().trim());
        deployTask.setCommitId(request.getCommitId().trim());
        deployTask.setDeployParams(trimToNull(request.getDeployParams()));
        deployTask.setRunStatus(request.getRunStatus().trim().toUpperCase());
        deployTask.setLogDirPath(trimToNull(request.getLogDirPath()));
        deployTask.setResultPath(trimToNull(request.getResultPath()));
        deployTask.setErrorMsg(trimToNull(request.getErrorMsg()));
        deployTask.setDuration(request.getDuration());

        BizAssert.affectedOne(deployTaskMapper.insert(deployTask), "Insert deploy task failed");

        log.info("Deploy task created successfully, username={}, id={}, deployTaskId={}",
                deployTask.getGitlabUsername(), deployTask.getId(), deployTask.getDeployTaskId());
        return ApiResponse.success("Deploy task created", deployTask);
    }

    //创建构建任务记录
    //
    //这个接口只负责"登记构建任务结果/状态"，不实际执行构建。
    //实际构建执行器可以在构建开始、结束或失败时调用它，把状态写入 build_task 表。
    @PostMapping("/build/task/create")
    public ApiResponse<BuildTask> createBuildTask(@RequestBody CreateBuildTaskRequest request,
                                                  HttpSession session) {
        String validationError = validateCreateBuildTaskRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);

        //BuildTask 是数据库实体对象，每次请求都创建一个新实例再写入数据库
        //不要把实体类设计成 Spring Bean，否则多个请求会共享同一个对象状态
        BuildTask buildTask = new BuildTask();
        buildTask.setGitlabUsername(context.username());
        buildTask.setBuildTaskId(request.getBuildTaskId().trim());
        buildTask.setDeployTaskId(trimToNull(request.getDeployTaskId()));
        buildTask.setProjectName(request.getProjectName().trim());
        buildTask.setBranchName(request.getBranchName().trim());
        buildTask.setCommitId(request.getCommitId().trim());
        buildTask.setScriptPath(trimToNull(request.getScriptPath()));
        buildTask.setArtifactPath(trimToNull(request.getArtifactPath()));
        buildTask.setLogDirPath(trimToNull(request.getLogDirPath()));
        buildTask.setRunStatus(request.getRunStatus().trim().toUpperCase());
        buildTask.setErrorMsg(trimToNull(request.getErrorMsg()));
        buildTask.setDuration(request.getDuration());

        BizAssert.affectedOne(buildTaskMapper.insert(buildTask), "Insert build task failed");

        log.info("Build task created successfully, username={}, id={}, buildTaskId={}",
                buildTask.getGitlabUsername(), buildTask.getId(), buildTask.getBuildTaskId());
        return ApiResponse.success("Build task created", buildTask);
    }

    @GetMapping("/task")
    public ApiResponse<DeployTask> getTask(@RequestParam String taskId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Get deploy task: username={}, taskId={}", context.username(), taskId);
        DeployTask task = deployPipelineService.getTask(taskId, context);
        return ApiResponse.success(task);
    }

    @GetMapping("/log")
    public ApiResponse<String> getLog(@RequestParam String taskId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Get deploy log: username={}, taskId={}", context.username(), taskId);
        DeployTask task = deployPipelineService.getTask(taskId, context);
        return ApiResponse.success(task.getLogDirPath());
    }

    @PostMapping("/cancel")
    public ApiResponse<DeployTask> cancelDeploy(@RequestParam String taskId,
            @RequestParam(required = false) String terminalSessionId,
            HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Cancel deploy: username={}, taskId={}", context.username(), taskId);
        DeployTask task = deployPipelineService.cancel(taskId, terminalSessionId, context);
        return ApiResponse.success("Deploy cancelled", task);
    }

    //校验创建部署任务的请求体
    //返回 null 表示校验通过；返回字符串表示具体错误信息
    //
    //这里选择返回错误字符串，而不是在每个分支直接抛异常，
    //是为了让 Controller 主流程保持"先校验，再执行业务"的结构。
    private String validateCreateDeployTaskRequest(CreateDeployTaskRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (!hasText(request.getDeployTaskId())) {
            return "deployTaskId is required";
        }
        if (!hasText(request.getProjectName())) {
            return "projectName is required";
        }
        if (!hasText(request.getBranchName())) {
            return "branchName is required";
        }
        if (!hasText(request.getCommitId())) {
            return "commitId is required";
        }
        if (!hasText(request.getRunStatus())) {
            return "runStatus is required";
        }
        String normalizedStatus = request.getRunStatus().trim().toUpperCase();
        if (!ALLOWED_RUN_STATUS.contains(normalizedStatus)) {
            return "runStatus must be one of: PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT";
        }
        if (request.getDuration() != null && request.getDuration() < 0) {
            return "duration must be greater than or equal to 0";
        }
        return null;
    }

    //校验创建构建任务的请求体
    //规则和部署任务类似：必填字段不能为空，状态必须在允许集合里，耗时不能为负数
    private String validateCreateBuildTaskRequest(CreateBuildTaskRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (!hasText(request.getBuildTaskId())) {
            return "buildTaskId is required";
        }
        if (!hasText(request.getProjectName())) {
            return "projectName is required";
        }
        if (!hasText(request.getBranchName())) {
            return "branchName is required";
        }
        if (!hasText(request.getCommitId())) {
            return "commitId is required";
        }
        if (!hasText(request.getRunStatus())) {
            return "runStatus is required";
        }
        String normalizedStatus = request.getRunStatus().trim().toUpperCase();
        if (!ALLOWED_RUN_STATUS.contains(normalizedStatus)) {
            return "runStatus must be one of: PENDING, RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT";
        }
        if (request.getDuration() != null && request.getDuration() < 0) {
            return "duration must be greater than or equal to 0";
        }
        return null;
    }

    //如果字符串有内容（非空且包含非空白字符），则返回去除首尾空格后的值；否则返回 null(保证有一个默认值null)
    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
