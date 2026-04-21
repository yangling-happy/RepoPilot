package com.repopilot.terminal.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repopilot.terminal.service.PtySession;
import com.repopilot.terminal.service.PtySessionManager;
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

    private final PtySessionManager ptySessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        session.getAttributes().put(SESSION_ID_ATTR, sessionId);
        ptySessionManager.create(sessionId, output -> sendStdout(session, sessionId, output));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = getSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        PtySession ptySession = ptySessionManager.get(sessionId);
        if (ptySession == null) {
            sendStdout(session, sessionId, "\r\n[terminal closed]\r\n");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            JsonNode data = root.get("data");

            switch (type) {
                case "stdin" -> handleStdin(ptySession, data, session, sessionId);
                case "resize" -> handleResize(ptySession, data, session, sessionId);
                default -> sendStdout(session, sessionId, "\r\n[unsupported message type]\r\n");
            }
        } catch (Exception e) {
            sendStdout(session, sessionId, "\r\n[invalid message] " + e.getMessage() + "\r\n");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            ptySessionManager.remove(sessionId);
        }
        closeSilently(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            ptySessionManager.remove(sessionId);
        }
    }

    private void handleStdin(PtySession ptySession, JsonNode data, WebSocketSession wsSession, String sessionId) {
        if (data == null || !data.isTextual()) {
            sendStdout(wsSession, sessionId, "\r\n[stdin must be string]\r\n");
            return;
        }
        try {
            ptySession.write(data.asText());
        } catch (IOException e) {
            sendStdout(wsSession, sessionId, "\r\n[stdin write failed] " + e.getMessage() + "\r\n");
        }
    }

    private void handleResize(PtySession ptySession, JsonNode data, WebSocketSession wsSession, String sessionId) {
        if (data == null || !data.isArray() || data.size() < 2) {
            sendStdout(wsSession, sessionId, "\r\n[resize data must be [cols, rows]]\r\n");
            return;
        }

        int cols = data.get(0).asInt(80);
        int rows = data.get(1).asInt(24);
        ptySession.resize(cols, rows);
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

    private void sendStdout(WebSocketSession session, String sessionId, String output) {
        if (!session.isOpen()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "stdout");
        payload.put("data", output);
        payload.put("sessionId", sessionId);

        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload.toString()));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to send stdout for session {}", sessionId, e);
        }
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