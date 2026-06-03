package com.repopilot.business.service.gitlab;

import com.repopilot.business.entity.User;
import com.repopilot.business.mapper.UserMapper;
import com.repopilot.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

//Spring 注解，标记这是一个 Service 层的 Bean
@Service
//Lombok 注解，为 final 字段自动生成构造函数，Spring 通过构造函数注入依赖
@RequiredArgsConstructor
public class GitLabSessionContextService {

    //HttpSession 中存储 GitLab Token 的 key
    public static final String TOKEN_SESSION_KEY = "gitlabToken";
    //HttpSession 中存储 GitLab 用户名的 key
    public static final String USERNAME_SESSION_KEY = "gitlabUsername";
    //HttpSession 中存储用户 ID 的 key（OAuth 登录后设置）
    public static final String USER_ID_SESSION_KEY = "userId";

    //GitLab 用户 API 客户端，用于通过 Token 获取用户名
    private final GitLabUserClient gitLabUserClient;
    //用户 Mapper，用于从数据库读取 Token
    private final UserMapper userMapper;

    //获取完整的用户上下文（Token + 用户名）
    //如果 Session 中没有用户名，会自动调用 GitLab API 获取并缓存
    public GitLabUserContext requireContext(HttpSession session) {
        String token = requireToken(session);
        //先尝试从 Session 缓存中取用户名
        String username = (String) session.getAttribute(USERNAME_SESSION_KEY);
        if (!StringUtils.hasText(username)) {
            //缓存中没有，调用 GitLab API 获取用户名
            username = gitLabUserClient.getCurrentUsername(token);
            //获取成功后缓存到 Session，避免重复调用 API
            session.setAttribute(USERNAME_SESSION_KEY, username);
        }
        return new GitLabUserContext(token, username);
    }

    //从 Session 中获取 GitLab Token
    //如果 Session 中没有，尝试从数据库 users 表读取（服务器重启后恢复场景）
    public String requireToken(HttpSession session) {
        Object value = session.getAttribute(TOKEN_SESSION_KEY);
        //instanceof 模式匹配：同时检查类型和值是否为有效字符串
        if (value instanceof String token && StringUtils.hasText(token)) {
            return token;
        }

        //Session 中没有 Token，尝试从数据库恢复（通过 userId）
        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (userId instanceof Long id) {
            User user = userMapper.selectById(id);
            if (user != null && StringUtils.hasText(user.getAccessToken())) {
                //从数据库恢复 Token 到 Session，后续请求不再查库
                session.setAttribute(TOKEN_SESSION_KEY, user.getAccessToken());
                session.setAttribute(USERNAME_SESSION_KEY, user.getUsername());
                return user.getAccessToken();
            }
        }

        throw new BusinessException(400,
                "GitLab token not found in session. Call /api/session/setGitlabToken first.");
    }

    //保存 Token 到 Session 并解析用户名
    //这个方法在用户首次设置 Token 时调用
    public String saveTokenAndResolveUsername(String token, HttpSession session) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }
        String normalizedToken = token.trim();
        //用 Token 调用 GitLab API 获取当前用户名（同时验证 Token 是否有效）
        String username = gitLabUserClient.getCurrentUsername(normalizedToken);
        //将 Token 和用户名都存入 Session
        session.setAttribute(TOKEN_SESSION_KEY, normalizedToken);
        session.setAttribute(USERNAME_SESSION_KEY, username);
        return username;
    }
}
