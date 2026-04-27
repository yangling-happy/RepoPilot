package com.repopilot.business.service.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalRelayClient {

    private final ObjectMapper objectMapper;

    @Value("${terminal.relay.base-url:http://localhost:8081/internal/terminal/sessions}")
    private String relayBaseUrl;

    public void emit(String sessionId, String line) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(line)) {
            return;
        }

        String normalizedBaseUrl = relayBaseUrl.endsWith("/")
                ? relayBaseUrl.substring(0, relayBaseUrl.length() - 1)
                : relayBaseUrl;
        String url = normalizedBaseUrl + "/" + sessionId + "/stdout";

        try {
            String payload = objectMapper.writeValueAsString(Map.of("data", ensureLineEnding(line)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("Relay stdout failed, sessionId={}, status={}, body={}",
                        sessionId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.debug("Relay stdout failed silently, sessionId={}, message={}", sessionId, e.getMessage());
        }
    }

    private String ensureLineEnding(String line) {
        if (line.endsWith("\n") || line.endsWith("\r")) {
            return line;
        }
        return line + "\r\n";
    }
}
