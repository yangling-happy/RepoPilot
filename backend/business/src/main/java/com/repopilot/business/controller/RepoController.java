package com.repopilot.business.controller;

import com.repopilot.business.dto.CloneRepoRequest;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.service.gitlab.GitlabRepoCloneService;
import com.repopilot.common.dto.ApiResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/repo")
@RequiredArgsConstructor
public class RepoController {

    private final GitlabRepoCloneService gitlabRepoCloneService;

    @PostMapping("/clone")
    public ApiResponse<CloneRepoResponse> cloneRepo(@RequestBody CloneRepoRequest request, HttpSession session) {
        if (request == null || request.getProjectId() == null || request.getProjectId() <= 0) {
            return ApiResponse.error(400, "projectId must be greater than 0");
        }

        String token = (String) session.getAttribute("gitlabToken");
        CloneRepoResponse response = gitlabRepoCloneService.cloneByProjectId(
            request.getProjectId(), request.getBranch(), token, request.getTerminalSessionId());

        log.info("Repository cloned successfully, projectId={}, branch={}", response.getProjectId(),
                response.getBranch());
        return ApiResponse.success("Repository cloned", response);
    }
}