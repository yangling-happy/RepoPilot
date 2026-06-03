package com.repopilot.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.config.DeployProperties;
import com.repopilot.business.dto.DeployTriggerRequest;
import com.repopilot.business.dto.DeployTriggerResponse;
import com.repopilot.business.entity.BuildTask;
import com.repopilot.business.entity.DeployTask;
import com.repopilot.business.mapper.BuildTaskMapper;
import com.repopilot.business.mapper.DeployTaskMapper;
import com.repopilot.business.service.DeployPipelineService;
import com.repopilot.business.service.gitlab.GitLabDocClient;
import com.repopilot.business.service.gitlab.GitLabUserContext;
import com.repopilot.business.service.terminal.TerminalRelayClient;
import com.repopilot.business.service.terminal.TerminalTaskClient;
import com.repopilot.business.service.workspace.UserWorkspaceResolver;
import com.repopilot.common.exception.BusinessException;
import com.repopilot.common.util.BizAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployPipelineServiceImpl implements DeployPipelineService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String TASK_BUILD = "BUILD_PROJECT";
    private static final String TASK_DEPLOY = "DEPLOY_PROJECT";
    private static final String TASK_CUSTOM_DEPLOY = "CUSTOM_DEPLOY";
    private static final int POLL_INTERVAL_MILLIS = 1500;

    private final DeployTaskMapper deployTaskMapper;
    private final BuildTaskMapper buildTaskMapper;
    private final GitLabDocClient gitLabDocClient;
    private final TerminalTaskClient terminalTaskClient;
    private final TerminalRelayClient terminalRelayClient;
    private final UserWorkspaceResolver userWorkspaceResolver;
    private final DeployProperties deployProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService deployExecutor = Executors.newCachedThreadPool();

    @Override
    public DeployTriggerResponse trigger(DeployTriggerRequest request, GitLabUserContext context) {
        BizAssert.notNull(request, 400, "Request body is required");
        String project = normalizeProject(request.getProject());
        String branch = normalizeBranch(request.getBranch());
        String sessionId = trimToNull(request.getTerminalSessionId());
        boolean buildEnabled = request.getBuild() == null || request.getBuild();
        String artifactPath = trimToNull(request.getArtifactPath());

        String commitId = trimToNull(request.getCommitId());
        if (!StringUtils.hasText(commitId)) {
            commitId = gitLabDocClient.getHeadCommit(context.token(), project, branch);
        }

        String deployHost = trimToNull(request.getDeployHost());
        Integer deployPort = request.getDeployPort();
        String deployUser = trimToNull(request.getDeployUser());
        String deployTargetDir = trimToNull(request.getDeployTargetDir());

        DeployTask existing = findRunningTask(context.username(), project, branch, commitId);
        if (existing != null) {
            return new DeployTriggerResponse(existing.getDeployTaskId(), existing.getRunStatus(), sessionId, commitId);
        }

        String repoDir = resolveRepoDir(context.username(), project, buildEnabled, artifactPath);
        DeployTask deployTask = new DeployTask();
        deployTask.setGitlabUsername(context.username());
        deployTask.setDeployTaskId(generateDeployTaskId());
        deployTask.setProjectName(project);
        deployTask.setBranchName(branch);
        deployTask.setCommitId(commitId);
        deployTask.setDeployParams(
                serializeDeployParams(new DeployParams(sessionId, buildEnabled, artifactPath)));
        deployTask.setRunStatus(STATUS_RUNNING);
        deployTask.setStartTime(LocalDateTime.now());
        deployTask.setDuration(0);
        BizAssert.affectedOne(deployTaskMapper.insert(deployTask), "Insert deploy task failed");

        terminalRelayClient.emit(sessionId, "[deploy] task created, id=" + deployTask.getDeployTaskId());

        DeployExecutionPlan plan = new DeployExecutionPlan(
                deployTask.getId(),
                deployTask.getDeployTaskId(),
                project,
                branch,
                commitId,
                sessionId,
                buildEnabled,
                artifactPath,
                repoDir,
                context.username(),
                deployHost,
                deployPort,
                deployUser,
                deployTargetDir);
        deployExecutor.execute(() -> runPipeline(plan));

        return new DeployTriggerResponse(deployTask.getDeployTaskId(), deployTask.getRunStatus(), sessionId, commitId);
    }

    @Override
    public DeployTask getTask(String taskId, GitLabUserContext context) {
        DeployTask task = findTaskById(taskId, context.username());
        if (task == null) {
            throw new BusinessException(404, "Deploy task not found");
        }
        if (STATUS_RUNNING.equals(task.getRunStatus())) {
            refreshTaskStatus(task);
        }
        return task;
    }

    @Override
    public DeployTask cancel(String taskId, String terminalSessionId, GitLabUserContext context) {
        DeployTask task = findTaskById(taskId, context.username());
        if (task == null) {
            throw new BusinessException(404, "Deploy task not found");
        }
        if (!STATUS_RUNNING.equals(task.getRunStatus())) {
            return task;
        }

        String sessionId = trimToNull(terminalSessionId);
        if (!StringUtils.hasText(sessionId)) {
            DeployParams params = parseDeployParams(task.getDeployParams());
            sessionId = params == null ? null : params.terminalSessionId();
        }
        if (StringUtils.hasText(sessionId)) {
            terminalTaskClient.stopTask(sessionId);
        }

        updateDeployTaskStatus(task.getId(), STATUS_CANCELLED, "Cancelled by user",
                durationSeconds(task.getStartTime()));
        task.setRunStatus(STATUS_CANCELLED);
        task.setErrorMsg("Cancelled by user");
        return task;
    }

    private void runPipeline(DeployExecutionPlan plan) {
        long start = System.currentTimeMillis();
        try {
            if (plan.buildEnabled()) {
                BuildTask buildTask = createBuildTask(plan);
                TaskResult buildResult = runTerminalTask(plan, TASK_BUILD, buildArgs(plan));
                updateBuildTask(buildTask.getId(), buildResult, (int) ((System.currentTimeMillis() - start) / 1000));
                if (isCancelled(plan.deployId())) {
                    return;
                }
                if (!STATUS_SUCCESS.equals(buildResult.status())) {
                    updateDeployTaskStatus(plan.deployId(), buildResult.status(), buildResult.message(),
                            (int) ((System.currentTimeMillis() - start) / 1000));
                    return;
                }
            }

            String deployTaskType = TASK_DEPLOY;
            Map<String, Object> deployTaskArgs = deployArgs(plan);
            if (hasCustomDeployScript(plan.repoDir())) {
                deployTaskType = TASK_CUSTOM_DEPLOY;
                deployTaskArgs = customDeployArgs(plan);
                terminalRelayClient.emit(plan.sessionId(),
                        "[deploy] detected deploy.sh in repository, using custom deploy script");
            }
            TaskResult deployResult = runTerminalTask(plan, deployTaskType, deployTaskArgs);
            if (isCancelled(plan.deployId())) {
                return;
            }
            updateDeployTaskStatus(plan.deployId(), deployResult.status(), deployResult.message(),
                    (int) ((System.currentTimeMillis() - start) / 1000));
        } catch (Exception e) {
            log.error("Deploy pipeline failed, deployTaskId={}", plan.deployTaskId(), e);
            updateDeployTaskStatus(plan.deployId(), STATUS_FAILED, "Deploy pipeline failed",
                    (int) ((System.currentTimeMillis() - start) / 1000));
        }
    }

    private TaskResult runTerminalTask(DeployExecutionPlan plan, String taskType, Map<String, Object> args) {
        if (!StringUtils.hasText(plan.sessionId())) {
            return new TaskResult(STATUS_FAILED, null, "terminalSessionId is required for task execution");
        }
        TerminalTaskClient.TerminalTaskStatusResult status;
        try {
            terminalTaskClient.startTask(plan.sessionId(), taskType, args);
            status = awaitTerminalExit(plan.sessionId());
        } catch (BusinessException e) {
            return new TaskResult(STATUS_FAILED, null, e.getMessage());
        }

        if (STATUS_RUNNING.equals(status.getStatus())) {
            return new TaskResult(STATUS_FAILED, status.getExitCode(), "terminal task did not exit");
        }
        if (STATUS_SUCCESS.equals(status.getStatus())) {
            return new TaskResult(STATUS_SUCCESS, status.getExitCode(), null);
        }
        if (STATUS_CANCELLED.equals(status.getStatus())) {
            return new TaskResult(STATUS_CANCELLED, status.getExitCode(), "terminal task cancelled");
        }
        if (STATUS_TIMEOUT.equals(status.getStatus())) {
            return new TaskResult(STATUS_TIMEOUT, status.getExitCode(), "terminal task timed out");
        }
        return new TaskResult(STATUS_FAILED, status.getExitCode(), "terminal task failed");
    }

    private TerminalTaskClient.TerminalTaskStatusResult awaitTerminalExit(String sessionId) {
        long timeoutMillis = Duration.ofMinutes(deployProperties.getTimeoutMinutes()).toMillis();
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            TerminalTaskClient.TerminalTaskStatusResult status = terminalTaskClient.getStatus(sessionId);
            if (!STATUS_RUNNING.equals(status.getStatus())) {
                return status;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }

        try {
            terminalTaskClient.stopTask(sessionId);
        } catch (BusinessException e) {
            log.warn("Failed to stop timed-out terminal task, sessionId={}, message={}", sessionId, e.getMessage());
        }
        return new TerminalTaskClient.TerminalTaskStatusResult(sessionId, null, STATUS_TIMEOUT, null);
    }

    private void refreshTaskStatus(DeployTask task) {
        DeployParams params = parseDeployParams(task.getDeployParams());
        if (params == null || params.build() || !StringUtils.hasText(params.terminalSessionId())) {
            return;
        }
        TerminalTaskClient.TerminalTaskStatusResult status;
        try {
            status = terminalTaskClient.getStatus(params.terminalSessionId());
        } catch (BusinessException e) {
            log.debug("Failed to refresh terminal status for deploy task {}: {}",
                    task.getDeployTaskId(), e.getMessage());
            return;
        }
        if (STATUS_RUNNING.equals(status.getStatus())) {
            return;
        }
        if (STATUS_SUCCESS.equals(status.getStatus()) || STATUS_FAILED.equals(status.getStatus())
                || STATUS_CANCELLED.equals(status.getStatus())) {
            updateDeployTaskStatus(task.getId(), status.getStatus(), null, durationSeconds(task.getStartTime()));
            task.setRunStatus(status.getStatus());
        }
    }

    private BuildTask createBuildTask(DeployExecutionPlan plan) {
        BuildTask buildTask = new BuildTask();
        buildTask.setGitlabUsername(plan.username());
        buildTask.setBuildTaskId("build-" + UUID.randomUUID());
        buildTask.setDeployTaskId(plan.deployTaskId());
        buildTask.setProjectName(plan.project());
        buildTask.setBranchName(plan.branch());
        buildTask.setCommitId(plan.commitId());
        buildTask.setRunStatus(STATUS_RUNNING);
        buildTask.setStartTime(LocalDateTime.now());
        buildTask.setDuration(0);
        BizAssert.affectedOne(buildTaskMapper.insert(buildTask), "Insert build task failed");
        return buildTask;
    }

    private void updateBuildTask(Long taskId, TaskResult result, int durationSeconds) {
        BuildTask update = new BuildTask();
        update.setId(taskId);
        update.setRunStatus(result.status());
        update.setErrorMsg(result.message());
        update.setDuration(durationSeconds);
        buildTaskMapper.updateById(update);
    }

    private void updateDeployTaskStatus(Long id, String status, String errorMsg, int durationSeconds) {
        DeployTask update = new DeployTask();
        update.setId(id);
        update.setRunStatus(status);
        update.setErrorMsg(errorMsg);
        update.setDuration(durationSeconds);
        deployTaskMapper.updateById(update);
    }

    private boolean isCancelled(Long deployId) {
        DeployTask task = deployTaskMapper.selectById(deployId);
        return task != null && STATUS_CANCELLED.equals(task.getRunStatus());
    }

    private Map<String, Object> buildArgs(DeployExecutionPlan plan) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("project", plan.project());
        args.put("branch", plan.branch());
        args.put("username", plan.username());
        args.put("repoDir", plan.repoDir());
        return args;
    }

    private Map<String, Object> deployArgs(DeployExecutionPlan plan) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("project", plan.project());
        args.put("branch", plan.branch());
        args.put("username", plan.username());
        if (StringUtils.hasText(plan.artifactPath())) {
            args.put("artifactPath", plan.artifactPath());
        }
        args.put("repoDir", plan.repoDir());
        if (StringUtils.hasText(plan.deployTargetDir())) {
            args.put("deployTargetDir", plan.deployTargetDir());
        }
        if (StringUtils.hasText(plan.deployHost())) {
            args.put("deployHost", plan.deployHost());
        }
        if (plan.deployPort() != null) {
            args.put("deployPort", String.valueOf(plan.deployPort()));
        }
        if (StringUtils.hasText(plan.deployUser())) {
            args.put("deployUser", plan.deployUser());
        }
        return args;
    }

    private boolean hasCustomDeployScript(String repoDir) {
        if (!StringUtils.hasText(repoDir)) {
            return false;
        }
        return Files.exists(Path.of(repoDir, "deploy.sh"));
    }

    private Map<String, Object> customDeployArgs(DeployExecutionPlan plan) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("repoDir", plan.repoDir());
        return args;
    }

    private DeployTask findRunningTask(String username, String project, String branch, String commitId) {
        LambdaQueryWrapper<DeployTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeployTask::getGitlabUsername, username)
                .eq(DeployTask::getProjectName, project)
                .eq(DeployTask::getBranchName, branch)
                .eq(DeployTask::getCommitId, commitId)
                .eq(DeployTask::getRunStatus, STATUS_RUNNING)
                .last("limit 1");
        return deployTaskMapper.selectOne(wrapper);
    }

    private DeployTask findTaskById(String taskId, String username) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException(400, "taskId is required");
        }
        LambdaQueryWrapper<DeployTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeployTask::getDeployTaskId, taskId.trim())
                .eq(DeployTask::getGitlabUsername, username)
                .last("limit 1");
        return deployTaskMapper.selectOne(wrapper);
    }

    private String resolveRepoDir(String username, String project, boolean buildEnabled, String artifactPath) {
        Path repoPath = userWorkspaceResolver.repoPath(username, project);
        if (buildEnabled || !StringUtils.hasText(artifactPath)) {
            if (!Files.exists(repoPath)) {
                throw new BusinessException(400, "Local repository not found for project: " + project);
            }
        }
        return repoPath.toString();
    }

    private String normalizeProject(String project) {
        BizAssert.hasText(project, 400, "project is required");
        String normalized = project.trim();
        if (!normalized.matches("\\d+")) {
            throw new BusinessException(400, "project must be a numeric GitLab project id");
        }
        return normalized;
    }

    private String normalizeBranch(String branch) {
        if (!StringUtils.hasText(branch)) {
            return "main";
        }
        return branch.trim();
    }

    private String generateDeployTaskId() {
        return "deploy-" + UUID.randomUUID();
    }

    private String serializeDeployParams(DeployParams params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    private DeployParams parseDeployParams(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, DeployParams.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int durationSeconds(LocalDateTime startTime) {
        if (startTime == null) {
            return 0;
        }
        long duration = Duration.between(startTime, LocalDateTime.now()).getSeconds();
        return (int) Math.max(0, duration);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record DeployExecutionPlan(
            Long deployId,
            String deployTaskId,
            String project,
            String branch,
            String commitId,
            String sessionId,
            boolean buildEnabled,
            String artifactPath,
            String repoDir,
            String username,
            String deployHost,
            Integer deployPort,
            String deployUser,
            String deployTargetDir) {
    }

    private record DeployParams(
            String terminalSessionId,
            boolean build,
            String artifactPath) {
    }

    private record TaskResult(String status, Integer exitCode, String message) {
    }
}
