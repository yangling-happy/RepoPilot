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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
@RequiredArgsConstructor
public class GitLabUserClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    public String getCurrentUsername(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/user"))
                .header("PRIVATE-TOKEN", token.trim())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new BusinessException(401, "GitLab token is invalid or lacks permission to read current user");
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException(500, "GitLab API failed to read current user, status=" + status);
            }

            JsonNode json = objectMapper.readTree(response.body());
            String username = json.path("username").asText(null);
            if (!StringUtils.hasText(username)) {
                throw new BusinessException(500, "GitLab username is empty");
            }
            return username.trim();
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read current user from GitLab");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while reading current user from GitLab");
        }
    }

    private String apiBase() {
        if (!StringUtils.hasText(gitlabApiUrl)) {
            throw new BusinessException(500, "GitLab API URL is not configured");
        }
        String trimmed = gitlabApiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
