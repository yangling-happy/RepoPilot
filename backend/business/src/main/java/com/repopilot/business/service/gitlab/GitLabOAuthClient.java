package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GitLabOAuthClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

    @Value("${gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    /**
     * 生成 GitLab OAuth 授权 URL，前端跳转到此 URL 让用户授权
     */
    public String buildAuthorizeUrl() {
        return authorizeUrl
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(scopes);
    }

    /**
     * 用授权码换取 access_token
     */
    public String exchangeCodeForToken(String code) {
        String formBody = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + encode(redirectUri);

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(500, "GitLab token exchange failed, status=" + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText(null);
            if (!StringUtils.hasText(accessToken)) {
                throw new BusinessException(500, "GitLab did not return access_token");
            }
            return accessToken;
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to exchange code for token");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted during token exchange");
        }
    }

    /**
     * 用 access_token 获取 GitLab 用户信息
     * 返回 JsonNode，包含 id, username, name, avatar_url, email
     */
    public JsonNode getUserInfo(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/user"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new BusinessException(401, "GitLab access_token is invalid");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(500, "GitLab API failed, status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to get user info from GitLab");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while getting user info");
        }
    }

    private String apiBase() {
        String trimmed = gitlabApiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
