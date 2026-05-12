package com.repopilot.business.controller;

import com.repopilot.business.dto.CloneRepoRequest;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.dto.GitLabProjectInfoResponse;
import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.business.service.gitlab.GitLabUserContext;
import com.repopilot.business.service.gitlab.GitlabRepoCloneService;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.util.BizAssert;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

//Lombok 注解，编译后自动生成 private static final Logger log = ...，
//可直接用 log.info(...) 记录日志
@Slf4j
//组合了 @Controller 和 @ResponseBody，表示该类中所有方法的返回值都会直接序列化为 JSON 写入 HTTP 响应体
@RestController
@RequestMapping("/repo")
//Lombok 注解，自动生成包含 final 字段的构造函数，Spring 会通过这个构造函数注入私有成员
@RequiredArgsConstructor
public class RepoController {

    private final GitlabRepoCloneService gitlabRepoCloneService;
    private final GitLabSessionContextService gitLabSessionContextService;

    @GetMapping("/projects")
    public ApiResponse<List<GitLabProjectInfoResponse>> listProjects(HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        return ApiResponse.success(gitlabRepoCloneService.listProjects(context.token(), context.username()));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<GitLabProjectInfoResponse> getProject(@PathVariable Long projectId, HttpSession session) {
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        return ApiResponse.success(gitlabRepoCloneService.getProjectInfo(
                projectId,
                context.token(),
                context.username()));
    }

    @PostMapping("/clone")
    public ApiResponse<CloneRepoResponse> cloneRepo(@RequestBody CloneRepoRequest request, HttpSession session) {
        //请求非空校验
        BizAssert.notNull(request, 400, "Request body is required");
        BizAssert.isTrue(request.getProjectId() != null && request.getProjectId() > 0,
                400, "projectId must be greater than 0");

        //调用service层函数获取上下文、克隆仓库
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        CloneRepoResponse response = gitlabRepoCloneService.cloneByProjectId(
                request.getProjectId(),
                request.getBranch(),
                context.token(),
                context.username(),
                request.getTerminalSessionId());

        log.info("Repository cloned successfully, username={}, projectId={}, branch={}",
                response.getGitlabUsername(), response.getProjectId(), response.getBranch());
        return ApiResponse.success("Repository cloned", response);
    }
}
