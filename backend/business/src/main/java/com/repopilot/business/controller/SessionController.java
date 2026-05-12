package com.repopilot.business.controller;

import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

//Lombok 注解，编译后自动生成 private static final Logger log = ...，
//可直接用 log.info(...) 记录日志
@Slf4j
@RestController
@RequestMapping("/session")
//Lombok 注解，自动生成包含 final 字段的构造函数，Spring 会通过这个构造函数注入私有成员
@RequiredArgsConstructor
public class SessionController {

    private final GitLabSessionContextService gitLabSessionContextService;

    //路由函数，都是调用对应service层函数实现对应功能
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
