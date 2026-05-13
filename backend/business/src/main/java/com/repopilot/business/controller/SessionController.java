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

    //GitLab 会话上下文服务，负责把 token/username 写入或读出 HttpSession
    private final GitLabSessionContextService gitLabSessionContextService;

    //保存 GitLab Token 到当前浏览器 Session
    //
    //前端首次接入 GitLab 时调用这个接口：
    //  1. 后端用 token 调 GitLab /user 接口验证 token
    //  2. 解析出 username
    //  3. 把 token 和 username 都缓存到 Session
    @PostMapping("/setGitlabToken")
    public ApiResponse<String> setGitlabToken(@RequestParam String token, HttpSession session) {
        String username = gitLabSessionContextService.saveTokenAndResolveUsername(token, session);
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
