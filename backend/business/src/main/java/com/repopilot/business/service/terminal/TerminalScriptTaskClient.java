package com.repopilot.business.service.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.common.exception.BusinessException;
import com.repopilot.common.terminal.ScriptTaskRunRequest;
import com.repopilot.common.terminal.ScriptTaskRunResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalScriptTaskClient {

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${terminal.tasks.run-url:http://localhost:8081/internal/terminal/tasks/run}")
    private String runUrl;

    public ScriptTaskRunResult run(String taskType,
                                   String sessionId,
                                   Map<String, Object> args,
                                   Map<String, String> secretEnvironment,
                                   long timeoutSeconds) {
        ScriptTaskRunRequest request = new ScriptTaskRunRequest();
        request.setTaskType(taskType);
        request.setSessionId(sessionId);
        request.setArgs(args == null ? Map.of() : new LinkedHashMap<>(args));
        request.setSecretEnvironment(secretEnvironment == null ? Map.of() : new LinkedHashMap<>(secretEnvironment));
        request.setTimeoutSeconds(timeoutSeconds);
        return send(request);
    }

    private ScriptTaskRunResult send(ScriptTaskRunRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(runUrl))
                    .timeout(Duration.ofSeconds(resolveHttpTimeout(request.getTimeoutSeconds())))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(response.body());
            int apiCode = root.path("code").asInt(response.statusCode());
            String message = root.path("message").asText("terminal task failed");
            if (response.statusCode() < 200 || response.statusCode() >= 300 || apiCode != 200) {
                throw new BusinessException(apiCode, message);
            }

            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                throw new BusinessException(500, "terminal task response is empty");
            }
            return objectMapper.treeToValue(data, ScriptTaskRunResult.class);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Terminal script task request failed, taskType={}", request.getTaskType(), e);
            throw new BusinessException(500, "Failed to run terminal script task");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while running terminal script task");
        }
    }

    private long resolveHttpTimeout(Long taskTimeoutSeconds) {
        long taskTimeout = taskTimeoutSeconds != null && taskTimeoutSeconds > 0 ? taskTimeoutSeconds : 600;
        return taskTimeout + 10;
    }

    public String requireResult(ScriptTaskRunResult result, String key, String message) {
        if (result == null || result.getResults() == null) {
            throw new BusinessException(500, message);
        }
        String value = result.getResults().get(key);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(500, message);
        }
        return value;
    }
}
