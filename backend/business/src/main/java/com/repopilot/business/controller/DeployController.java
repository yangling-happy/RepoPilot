package com.repopilot.business.controller;

import com.repopilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeployController {

    @PostMapping("/trigger")
    public ApiResponse<String> triggerDeploy(@RequestParam String project,
                                             @RequestParam String branch,
                                             @RequestParam String environment,
                                             @RequestParam(required = false) String args) {
        log.info("Deploy trigger: project={}, branch={}, env={}", project, branch, environment);
        // TODO: Implement deploy trigger logic
        return ApiResponse.success("Deploy triggered", "task-id-placeholder");
    }

    @GetMapping("/task")
    public ApiResponse<Object> getTask(@RequestParam String taskId) {
        log.info("Get deploy task: taskId={}", taskId);
        // TODO: Implement get task logic
        return ApiResponse.success(null);
    }

    @GetMapping("/log")
    public ApiResponse<Object> getLog(@RequestParam String taskId) {
        log.info("Get deploy log: taskId={}", taskId);
        // TODO: Implement get log logic
        return ApiResponse.success(null);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancelDeploy(@RequestParam String taskId) {
        log.info("Cancel deploy: taskId={}", taskId);
        // TODO: Implement cancel logic
        return ApiResponse.success("Deploy cancelled", null);
    }
}
