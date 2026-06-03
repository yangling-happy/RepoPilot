package com.repopilot.business.service.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TerminalTaskClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${terminal.tasks.base-url:http://localhost:8081/api/terminal/tasks}")
    private String tasksBaseUrl;

    public TerminalTaskStartResult startTask(String sessionId, String taskType, Map<String, Object> args) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(400, "terminalSessionId is required");
        }
        if (!StringUtils.hasText(taskType)) {
            throw new BusinessException(400, "taskType is required");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId.trim());
        payload.put("taskType", taskType.trim());
        payload.put("args", args == null ? Map.of() : args);

        String url = baseUrl() + "/start";
        return post(url, payload, TerminalTaskStartResult.class);
    }

    public TerminalTaskStatusResult getStatus(String sessionId) {
        String normalized = requireSessionId(sessionId);
        String url = baseUrl() + "/status?sessionId=" + encodeQuery(normalized);
        return get(url, TerminalTaskStatusResult.class);
    }

    public TerminalTaskStatusResult stopTask(String sessionId) {
        String normalized = requireSessionId(sessionId);
        String url = baseUrl() + "/stop?sessionId=" + encodeQuery(normalized);
        return post(url, null, TerminalTaskStatusResult.class);
    }

    private String requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(400, "terminalSessionId is required");
        }
        return sessionId.trim();
    }

    private <T> T get(String url, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();
        return execute(request, type);
    }

    private <T> T post(String url, Object payload, Class<T> type) {
        HttpRequest.BodyPublisher bodyPublisher = payload == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(bodyPublisher)
                .build();
        return execute(request, type);
    }

    private <T> T execute(HttpRequest request, Class<T> type) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(response.statusCode(), "Terminal service request failed");
            }

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(500);
            if (code != 200) {
                String message = root.path("message").asText("Terminal service error");
                throw new BusinessException(code, message);
            }
            JsonNode data = root.path("data");
            if (type == Void.class || data.isMissingNode() || data.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(data, type);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to call terminal service");
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to serialize terminal request");
        }
    }

    private String baseUrl() {
        if (!StringUtils.hasText(tasksBaseUrl)) {
            throw new BusinessException(500, "terminal.tasks.base-url is not configured");
        }
        String trimmed = tasksBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TerminalTaskStartResult {
        private String sessionId;
        private String taskType;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TerminalTaskStatusResult {
        private String sessionId;
        private String taskType;
        private String status;
        private Integer exitCode;
    }
}
