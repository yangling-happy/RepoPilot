package com.repopilot.business.controller;

import com.repopilot.business.dto.CloneRepoRequest;
import com.repopilot.business.dto.CloneRepoResponse;
import com.repopilot.business.service.gitlab.GitLabSessionContextService;
import com.repopilot.business.service.gitlab.GitLabUserContext;
import com.repopilot.business.service.gitlab.GitlabRepoCloneService;
import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.util.BizAssert;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//Lombok 注解，编译后自动生成 private static final Logger log = ...，
//可直接用 log.info(...) 记录日志
@Slf4j
//组合了 @Controller 和 @ResponseBody，表示该类中所有方法的返回值都会直接序列化为 JSON 写入 HTTP 响应体
@RestController
@RequestMapping("/repo")
//Lombok 注解，自动生成包含 final 字段的构造函数，Spring 会通过这个构造函数注入私有成员
@RequiredArgsConstructor
public class RepoController {

    //仓库克隆服务，真正负责调用 GitLab API、执行 JGit clone、写入本地工作空间
    private final GitlabRepoCloneService gitlabRepoCloneService;
    //会话上下文服务，用来从 HttpSession 里拿到 GitLab token 和用户名
    private final GitLabSessionContextService gitLabSessionContextService;

    //克隆 GitLab 仓库到当前用户的本地工作空间
    //
    //整体流程：
    //  1. 校验请求体和 projectId
    //  2. 从 Session 读取 GitLab token/username
    //  3. 调用 Service 执行克隆
    //  4. 返回本地路径、commitId 等结果给前端
    @PostMapping("/clone")
    public ApiResponse<CloneRepoResponse> cloneRepo(@RequestBody CloneRepoRequest request, HttpSession session) {
        //请求非空校验
        BizAssert.notNull(request, 400, "Request body is required");
        BizAssert.isTrue(request.getProjectId() != null && request.getProjectId() > 0,
                400, "projectId must be greater than 0");

        //Session 里保存的是当前浏览器会话的 GitLab 认证信息
        //后端不会要求前端每次都把 token 明文放在业务请求体里
        GitLabUserContext context = gitLabSessionContextService.requireContext(session);
        //真正的克隆逻辑放在 Service 层，这里只负责 HTTP 入参/出参
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
