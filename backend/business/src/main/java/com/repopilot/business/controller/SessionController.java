package com.repopilot.business.controller;

import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.business.service.gitlab.GitLabOAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final GitLabSessionContextService gitLabSessionContextService;
    private final GitLabOAuthClient gitLabOAuthClient;

    @PostMapping("/setGitlabToken")
    public ApiResponse<String> setGitlabToken(@RequestParam String token, HttpSession session) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }
        JsonNode userInfo = gitLabOAuthClient.getUserInfo(token.trim());
        String username = userInfo.path("username").asText("");
        gitLabSessionContextService.saveOAuthContext(token.trim(), userInfo, session);
        log.info("GitLab token set in session, username={}", username);
        return ApiResponse.success("Token saved successfully", username);
    }

    //读取当前 Session 里的 GitLab Token
    //如果 Session 里没有 token，requireToken 会抛业务异常，提醒前端先设置 token
    @GetMapping("/getGitlabToken")
    public ApiResponse<String> getGitlabToken(HttpSession session) {
        String token = gitLabSessionContextService.requireToken(session);
        return ApiResponse.success(token);
    }

    //读取当前 Session 对应的 GitLab 用户名
    //如果 username 没缓存，requireContext 会用 token 再调一次 GitLab /user 接口并回填缓存
    @GetMapping("/getGitlabUsername")
    public ApiResponse<String> getGitlabUsername(HttpSession session) {
        return ApiResponse.success(gitLabSessionContextService.requireContext(session).username());
    }
}
