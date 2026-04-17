package com.repopilot.business.controller;

import com.repopilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/doc")
@RequiredArgsConstructor
public class DocController {

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
}
