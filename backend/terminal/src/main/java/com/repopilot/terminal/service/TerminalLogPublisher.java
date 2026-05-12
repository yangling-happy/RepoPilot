package com.repopilot.terminal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalLogPublisher {

    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String sessionId, WebSocketSession session) {
        subscribers.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unsubscribe(String sessionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = subscribers.get(sessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            subscribers.remove(sessionId, sessions);
        }
    }

    public boolean publishStdout(String sessionId, String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        ObjectNode payload = basePayload("stdout", sessionId);
        payload.put("data", data);
        return publish(sessionId, payload);
    }

    public boolean publishError(String sessionId, String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        ObjectNode payload = basePayload("error", sessionId);
        payload.put("message", message);
        return publish(sessionId, payload);
    }

    public boolean publishExit(String sessionId, int exitCode) {
        ObjectNode payload = basePayload("exit", sessionId);
        payload.put("exitCode", exitCode);
        return publish(sessionId, payload);
    }

    public boolean publishResult(String sessionId, String taskType, JsonNode data) {
        if (data == null || data.isNull()) {
            return false;
        }
        ObjectNode payload = basePayload("result", sessionId);
        payload.put("taskType", taskType);
        payload.set("data", data);
        return publish(sessionId, payload);
    }

    private ObjectNode basePayload(String type, String sessionId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", type);
        payload.put("sessionId", sessionId);
        return payload;
    }

    private boolean publish(String sessionId, ObjectNode payload) {
        Set<WebSocketSession> sessions = subscribers.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }

        String json = payload.toString();
        boolean delivered = false;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        delivered = true;
                    }
                }
            } catch (Exception e) {
                sessions.remove(session);
                log.debug("Failed to publish terminal log, sessionId={}", sessionId, e);
            }
        }
        if (sessions.isEmpty()) {
            subscribers.remove(sessionId, sessions);
        }
        return delivered;
    }
}
