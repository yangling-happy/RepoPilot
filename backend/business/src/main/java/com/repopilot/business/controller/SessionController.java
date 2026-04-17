package com.repopilot.business.controller;

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

    @PostMapping("/setGitlabToken")
    public ApiResponse<Void> setGitlabToken(@RequestParam String token, HttpSession session) {
        session.setAttribute("gitlabToken", token);
        log.info("GitLab token set in session");
        return ApiResponse.success("Token saved successfully", null);
    }

    @GetMapping("/getGitlabToken")
    public ApiResponse<String> getGitlabToken(HttpSession session) {
        String token = (String) session.getAttribute("gitlabToken");
        return ApiResponse.success(token);
    }
}
