package com.repopilot.business.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.business.entity.User;
import com.repopilot.business.mapper.UserMapper;
import com.repopilot.business.service.gitlab.GitLabOAuthClient;
import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final GitLabOAuthClient gitLabOAuthClient;
    private final UserMapper userMapper;

    @Value("${frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 返回 GitLab OAuth 授权 URL，前端跳转到此 URL 完成授权
     */
    @GetMapping("/login")
    public ApiResponse<String> login() {
        String url = gitLabOAuthClient.buildAuthorizeUrl();
        return ApiResponse.success(url);
    }

    /**
     * GitLab OAuth 回调：用 code 换 token，获取用户信息，存入 DB 和 Session
     */
    @GetMapping("/callback")
    public void callback(@RequestParam String code, HttpSession session,
                         jakarta.servlet.http.HttpServletResponse response) {
        // 1. 用 code 换 access_token
        String accessToken = gitLabOAuthClient.exchangeCodeForToken(code);

        // 2. 用 token 获取用户信息
        JsonNode userInfo = gitLabOAuthClient.getUserInfo(accessToken);
        int gitlabId = userInfo.path("id").asInt();
        String username = userInfo.path("username").asText("");
        String name = userInfo.path("name").asText("");
        String avatarUrl = userInfo.path("avatar_url").asText("");
        String email = userInfo.path("email").asText("");

        // 3. 存入 users 表（按 gitlab_id upsert）
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getGitlabId, gitlabId));
        if (existing != null) {
            existing.setAccessToken(accessToken);
            existing.setName(name);
            existing.setAvatarUrl(avatarUrl);
            existing.setEmail(email);
            existing.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(existing);
            session.setAttribute("userId", existing.getId());
        } else {
            User user = new User();
            user.setGitlabId(gitlabId);
            user.setUsername(username);
            user.setName(name);
            user.setAvatarUrl(avatarUrl);
            user.setEmail(email);
            user.setAccessToken(accessToken);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);
            session.setAttribute("userId", user.getId());
        }

        // 4. 写入 Session（兼容现有业务代码）
        session.setAttribute(GitLabSessionContextService.TOKEN_SESSION_KEY, accessToken);
        session.setAttribute(GitLabSessionContextService.USERNAME_SESSION_KEY, username);

        log.info("OAuth login successful: username={}, gitlabId={}", username, gitlabId);

        // 5. 重定向到前端 dashboard
        try {
            response.sendRedirect(frontendBaseUrl + "/dashboard");
        } catch (Exception e) {
            throw new BusinessException("Failed to redirect after OAuth callback");
        }
    }

    /**
     * 返回当前登录用户信息，未登录返回 401
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "Not logged in");
        }
        User user = userMapper.selectById((Long) userId);
        if (user == null) {
            throw new BusinessException(401, "User not found");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("gitlabId", user.getGitlabId());
        info.put("username", user.getUsername());
        info.put("name", user.getName());
        info.put("avatarUrl", user.getAvatarUrl());
        info.put("email", user.getEmail());
        return ApiResponse.success(info);
    }

    /**
     * 登出：清除 Session
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success("Logged out");
    }
}
