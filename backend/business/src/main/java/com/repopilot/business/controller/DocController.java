package com.repopilot.business.controller;

import com.repopilot.business.dto.CreateDocFileRequest;
import com.repopilot.business.dto.CreateDocTaskRequest;
import com.repopilot.business.entity.DocFile;
import com.repopilot.business.entity.DocTask;
import com.repopilot.business.mapper.DocFileMapper;
import com.repopilot.business.mapper.DocTaskMapper;
import com.repopilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocController {

    private static final Set<String> ALLOWED_TASK_STATUS = Set.of(
            "PENDING", "RUNNING", "SUCCESS", "FAILED", "SKIPPED");
    private static final Set<String> ALLOWED_PARSE_STATUS = Set.of(
            "PENDING", "SUCCESS", "FAILED");

    private final DocTaskMapper docTaskMapper;
    private final DocFileMapper docFileMapper;

    @PostMapping("/webhook/gitlab")
    public ApiResponse<Void> handleGitlabWebhook(@RequestBody String payload) {
        log.info("Received GitLab webhook: {}", payload);
        // TODO: Implement webhook handling logic
        return ApiResponse.success("Webhook received", null);
    }

    @PostMapping("/rebuild")
    public ApiResponse<Void> rebuildDoc(@RequestParam String project,
            @RequestParam String branch,
            @RequestParam String commitId) {
        log.info("Rebuild doc request: project={}, branch={}, commitId={}", project, branch, commitId);
        // TODO: Implement rebuild logic
        return ApiResponse.success("Rebuild triggered", null);
    }

    @GetMapping("/query")
    public ApiResponse<Object> queryDoc(@RequestParam String project,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String filePath) {
        log.info("Query doc: project={}, branch={}, filePath={}", project, branch, filePath);
        // TODO: Implement query logic
        return ApiResponse.success(null);
    }

    @PostMapping("/task/create")
    public ApiResponse<DocTask> createDocTask(@RequestBody CreateDocTaskRequest request) {
        String validationError = validateCreateDocTaskRequest(request);
        if (validationError != null) {
            return ApiResponse.error(400, validationError);
        }

        DocTask task = new DocTask();
        task.setEventId(request.getEventId().trim());
        task.setProject(request.getProject().trim());
        task.setBranch(request.getBranch().trim());
        task.setCommitId(request.getCommitId().trim());
        task.setStatus(request.getStatus().trim().toUpperCase());
        task.setDuration(request.getDuration());

        int affectedRows = docTaskMapper.insert(task);
        if (affectedRows != 1) {
            return ApiResponse.error("Insert doc task failed");
        }

        log.info("Doc task created successfully, id={}, eventId={}", task.getId(), task.getEventId());
        return ApiResponse.success("Doc task created", task);
    }

    @PostMapping("/file/create")
    public ApiResponse<DocFile> createDocFile(@RequestBody CreateDocFileRequest request) {
        String validationError = validateCreateDocFileRequest(request);
        if (validationError != null) {
            return ApiResponse.error(400, validationError);
        }

        DocFile docFile = new DocFile();
        docFile.setTaskId(request.getTaskId());
        docFile.setProjectName(request.getProjectName().trim());
        docFile.setBranchName(request.getBranchName().trim());
        docFile.setFilePath(request.getFilePath().trim());
        docFile.setCommitId(request.getCommitId().trim());
        docFile.setDocFilePath(hasText(request.getDocFilePath()) ? request.getDocFilePath().trim() : null);
        docFile.setParseStatus(request.getParseStatus().trim().toUpperCase());
        docFile.setParseErrorMsg(hasText(request.getParseErrorMsg()) ? request.getParseErrorMsg().trim() : null);

        int affectedRows = docFileMapper.insert(docFile);
        if (affectedRows != 1) {
            return ApiResponse.error("Insert doc file failed");
        }

        log.info("Doc file created successfully, id={}, filePath={}", docFile.getId(), docFile.getFilePath());
        return ApiResponse.success("Doc file created", docFile);
    }

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

    private String validateCreateDocFileRequest(CreateDocFileRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (request.getTaskId() != null && request.getTaskId() <= 0) {
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
