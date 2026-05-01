package com.repopilot.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.terminal.service.TerminalLogPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_ID_ATTR = "terminalSessionId";

    private final TerminalLogPublisher terminalLogPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        session.getAttributes().put(SESSION_ID_ATTR, sessionId);
        terminalLogPublisher.subscribe(sessionId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = getSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");

            switch (type) {
                case "resize" -> {
                    // Log sessions are read-only; resize is accepted as a no-op for client compatibility.
                }
                case "stdin" -> terminalLogPublisher.publishError(sessionId,
                        "stdin is disabled for task log sessions");
                default -> terminalLogPublisher.publishError(sessionId, "unsupported message type: " + type);
            }
        } catch (Exception e) {
            terminalLogPublisher.publishError(sessionId, "invalid message: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            terminalLogPublisher.unsubscribe(sessionId, session);
        }
        closeSilently(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            terminalLogPublisher.unsubscribe(sessionId, session);
        }
    }

    private String getSessionId(WebSocketSession session) {
        Object value = session.getAttributes().get(SESSION_ID_ATTR);
        if (value instanceof String sid && StringUtils.hasText(sid)) {
            return sid;
        }
        return extractSessionId(session);
    }

    private String extractSessionId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getPath() == null) {
            return null;
        }

        String path = uri.getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        return URLDecoder.decode(path.substring(lastSlash + 1), StandardCharsets.UTF_8);
    }

    private void closeSilently(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Failed to close websocket session", e);
        }
    }
}
