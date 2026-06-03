package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.business.entity.User;
import com.repopilot.business.mapper.UserMapper;
import com.repopilot.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GitLabSessionContextService {

    public static final String TOKEN_SESSION_KEY = "gitlabToken";
    public static final String USERNAME_SESSION_KEY = "gitlabUsername";
    public static final String USER_ID_SESSION_KEY = "userId";

    private final GitLabOAuthClient gitLabOAuthClient;
    private final UserMapper userMapper;

    public GitLabUserContext requireContext(HttpSession session) {
        String token = requireToken(session);
        String username = (String) session.getAttribute(USERNAME_SESSION_KEY);
        if (!StringUtils.hasText(username)) {
            JsonNode userInfo = gitLabOAuthClient.getUserInfo(token);
            username = userInfo.path("username").asText(null);
            if (!StringUtils.hasText(username)) {
                throw new BusinessException(500, "GitLab username is empty");
            }
            session.setAttribute(USERNAME_SESSION_KEY, username);
        }
        return new GitLabUserContext(token, username);
    }

    public String requireToken(HttpSession session) {
        Object value = session.getAttribute(TOKEN_SESSION_KEY);
        if (value instanceof String token && StringUtils.hasText(token)) {
            return token;
        }

        Object userId = session.getAttribute(USER_ID_SESSION_KEY);
        if (userId instanceof Long id) {
            User user = userMapper.selectById(id);
            if (user != null && StringUtils.hasText(user.getAccessToken())) {
                session.setAttribute(TOKEN_SESSION_KEY, user.getAccessToken());
                session.setAttribute(USERNAME_SESSION_KEY, user.getUsername());
                return user.getAccessToken();
            }
        }

        throw new BusinessException(400,
                "GitLab token not found in session. Call /api/session/setGitlabToken first.");
    }

    public void saveOAuthContext(String accessToken, JsonNode userInfo, HttpSession session) {
        String username = userInfo.path("username").asText("");
        session.setAttribute(TOKEN_SESSION_KEY, accessToken);
        session.setAttribute(USERNAME_SESSION_KEY, username);
    }
}
