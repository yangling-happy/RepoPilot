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

//WebSocket 消息处理器
//职责：处理前端 WebSocket 连接的生命周期事件（连接建立、消息接收、连接关闭）
//这是"只读终端"模式：前端只能接收日志输出，不能发送 stdin 输入
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    //WebSocket 会话属性中存储 sessionId 的 key
    private static final String SESSION_ID_ATTR = "terminalSessionId";

    //日志发布器，管理 WebSocket 订阅关系
    private final TerminalLogPublisher terminalLogPublisher;
    //JSON 解析工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    //WebSocket 连接建立后调用
    //从 URL 路径中提取 sessionId，注册到日志发布器
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        //将 sessionId 存入会话属性，后续消息处理时可以直接取用
        session.getAttributes().put(SESSION_ID_ATTR, sessionId);
        //订阅：将这个 WebSocket 会话加入 sessionId 对应的订阅者集合
        terminalLogPublisher.subscribe(sessionId, session);
    }

    //收到前端发来的文本消息时调用
    //这个终端是只读的，前端发来的消息只做简单响应
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
                    //终端窗口大小调整消息，这里接受但不做处理（no-op）
                    //接受是为了客户端兼容性，避免前端报错
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
