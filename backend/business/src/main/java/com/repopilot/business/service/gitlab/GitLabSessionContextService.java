package com.repopilot.business.service.gitlab;

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

    private final GitLabUserClient gitLabUserClient;

    public GitLabUserContext requireContext(HttpSession session) {
        String token = requireToken(session);
        String username = (String) session.getAttribute(USERNAME_SESSION_KEY);
        if (!StringUtils.hasText(username)) {
            username = gitLabUserClient.getCurrentUsername(token);
            session.setAttribute(USERNAME_SESSION_KEY, username);
        }
        return new GitLabUserContext(token, username);
    }

    public String requireToken(HttpSession session) {
        Object value = session.getAttribute(TOKEN_SESSION_KEY);
        if (!(value instanceof String token) || !StringUtils.hasText(token)) {
            throw new BusinessException(400,
                    "GitLab token not found in session. Call /api/session/setGitlabToken first.");
        }
        return token;
    }

    public String saveTokenAndResolveUsername(String token, HttpSession session) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }
        String normalizedToken = token.trim();
        String username = gitLabUserClient.getCurrentUsername(normalizedToken);
        session.setAttribute(TOKEN_SESSION_KEY, normalizedToken);
        session.setAttribute(USERNAME_SESSION_KEY, username);
        return username;
    }
}
