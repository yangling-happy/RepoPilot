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
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeployController {

    private static final Set<String> ALLOWED_RUN_STATUS = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED", "TIMEOUT");

    private final DeployTaskMapper deployTaskMapper;
    private final BuildTaskMapper buildTaskMapper;
    private final GitLabSessionContextService gitLabSessionContextService;

    @PostMapping("/trigger")
    public ApiResponse<String> triggerDeploy(@RequestParam String project,
            @RequestParam String branch,
            @RequestParam String environment,
            @RequestParam(required = false) String args,
            HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        log.info("Deploy trigger: username={}, project={}, branch={}, env={}",
                context.username(), project, branch, environment);
        // TODO: Implement deploy trigger logic
        return ApiResponse.success("Deploy triggered", "task-id-placeholder");
    }

    @PostMapping("/task/create")
    public ApiResponse<DeployTask> createDeployTask(@RequestBody CreateDeployTaskRequest request,
                                                    HttpSession session) {
        String validationError = validateCreateDeployTaskRequest(request);
        BizAssert.isTrue(validationError == null, 400, validationError);
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);

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

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
