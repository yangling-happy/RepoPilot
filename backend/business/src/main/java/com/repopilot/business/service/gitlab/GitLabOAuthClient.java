package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GitLabOAuthClient {

    private final GitLabHttpClient gitLabHttpClient;

    @Value("${gitlab.oauth.client-id}")
    private String clientId;

    @Value("${gitlab.oauth.client-secret}")
    private String clientSecret;

    @Value("${gitlab.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${gitlab.oauth.authorize-url}")
    private String authorizeUrl;

    @Value("${gitlab.oauth.token-url}")
    private String tokenUrl;

    @Value("${gitlab.oauth.scopes:read_user api}")
    private String scopes;

    public String buildAuthorizeUrl() {
        return authorizeUrl
                + "?client_id=" + GitLabHttpClient.encode(clientId)
                + "&redirect_uri=" + GitLabHttpClient.encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + GitLabHttpClient.encode(scopes);
    }

    public String exchangeCodeForToken(String code) {
        String formBody = "client_id=" + GitLabHttpClient.encode(clientId)
                + "&client_secret=" + GitLabHttpClient.encode(clientSecret)
                + "&code=" + GitLabHttpClient.encode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + GitLabHttpClient.encode(redirectUri);

        JsonNode json = gitLabHttpClient.postForm(tokenUrl, formBody);

        if (json.has("error")) {
            throw new BusinessException(500,
                    "GitLab token exchange failed: " + json.path("error_description").asText("unknown error"));
        }

        String accessToken = json.path("access_token").asText(null);
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(500, "GitLab did not return access_token");
        }
        return accessToken;
    }

    public JsonNode getUserInfo(String accessToken) {
        return gitLabHttpClient.getJson(accessToken, "/user");
    }
}
