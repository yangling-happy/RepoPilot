package com.repopilot.business.controller;

import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final GitLabSessionContextService gitLabSessionContextService;

    @PostMapping("/setGitlabToken")
    public ApiResponse<String> setGitlabToken(@RequestParam String token, HttpSession session) {
        String username = gitLabSessionContextService.saveTokenAndResolveUsername(token, session);
        log.info("GitLab token set in session, username={}", username);
        return ApiResponse.success("Token saved successfully", username);
    }

    @GetMapping("/getGitlabToken")
    public ApiResponse<String> getGitlabToken(HttpSession session) {
        String token = gitLabSessionContextService.requireToken(session);
        return ApiResponse.success(token);
    }

    @GetMapping("/getGitlabUsername")
    public ApiResponse<String> getGitlabUsername(HttpSession session) {
        return ApiResponse.success(gitLabSessionContextService.requireContext(session).username());
    }
}
