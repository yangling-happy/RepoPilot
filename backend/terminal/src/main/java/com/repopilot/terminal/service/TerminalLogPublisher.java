package com.repopilot.terminal.service;

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

//终端日志发布器
//职责：管理 WebSocket 会话的订阅关系，并将消息广播给订阅了同一 sessionId 的所有前端连接
//使用 ConcurrentHashMap 保证线程安全（多个任务可能同时发布日志）
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalLogPublisher {

    //JSON 构建工具
    private final ObjectMapper objectMapper;
    //sessionId -> 订阅了该会话的 WebSocket 连接集合
    //ConcurrentHashMap.newKeySet() 创建线程安全的 Set
    private final ConcurrentMap<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    //订阅：将 WebSocket 会话加入指定 sessionId 的订阅者集合
    //computeIfAbsent：如果 sessionId 不存在则创建一个新的线程安全 Set
    public void subscribe(String sessionId, WebSocketSession session) {
        subscribers.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    //取消订阅：从指定 sessionId 的订阅者集合中移除 WebSocket 会话
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
