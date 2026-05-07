package com.repopilot.business.controller;

import com.repopilot.business.dto.CreateBuildTaskRequest;
import com.repopilot.business.dto.CreateDeployTaskRequest;
import com.repopilot.business.entity.BuildTask;
import com.repopilot.business.entity.DeployTask;
import com.repopilot.business.mapper.BuildTaskMapper;
import com.repopilot.business.mapper.DeployTaskMapper;
import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.business.service.gitlab.GitLabUserContext;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.util.BizAssert;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
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
    //一个集合，记录状态
    private static final Set<String> ALLOWED_RUN_STATUS = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");
    
    //这三个final字段(值不可修改的变量)就是通过@RequiredArgsConstructor这个注解自动生成构造函数的
    //操作数据库中的部署任务和构建任务表
    private final DeployTaskMapper deployTaskMapper;
    private final BuildTaskMapper buildTaskMapper;
    //从 HTTP 会话中获取当前 GitLab 用户上下文
    private final GitLabSessionContextService gitLabSessionContextService;

    @PostMapping("/trigger")
    public ApiResponse<String> triggerDeploy(@RequestParam String project,
            @RequestParam String branch,
            @RequestParam String environment,
            @RequestParam(required = false) String args,
            HttpSession session) {
        
        //通过 gitLabSessionContextService.requireContext(session) 强制获取当前用户上下文
        //若未登录或无有效会话，此方法通常会抛异常，由全局异常处理器处理
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Deploy trigger: username={}, project={}, branch={}, env={}",
                context.username(), project, branch, environment);
        // TODO: Implement deploy trigger logic
        return ApiResponse.success("Deploy triggered", "task-id-placeholder");
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

    @PostMapping("/build/task/create")
    public ApiResponse<BuildTask> createBuildTask(@RequestBody CreateBuildTaskRequest request,
                                                  HttpSession session) {
        String validationError = validateCreateBuildTaskRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        
        
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
    public ApiResponse<Object> getTask(@RequestParam String taskId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Get deploy task: username={}, taskId={}", context.username(), taskId);
        // TODO: Implement get task logic
        return ApiResponse.success(null);
    }

    @GetMapping("/log")
    public ApiResponse<Object> getLog(@RequestParam String taskId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Get deploy log: username={}, taskId={}", context.username(), taskId);
        // TODO: Implement get log logic
        return ApiResponse.success(null);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancelDeploy(@RequestParam String taskId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Cancel deploy: username={}, taskId={}", context.username(), taskId);
        // TODO: Implement cancel logic
        return ApiResponse.success("Deploy cancelled", null);
    }

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
