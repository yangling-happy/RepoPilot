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

/**
 * 终端日志的 WebSocket 消息处理器。
 *
 * 这个类负责处理前端连接到 { /ws/terminal/{sessionId}} 之后的生命周期事件：
 * 连接建立、收到客户端消息、传输异常、连接关闭。真正的日志内容不是在这里生成的，
 * 而是由 { TerminalLogPublisher} 统一发布；本类只负责把当前 WebSocket 会话
 * 订阅到指定的 { sessionId} 上。
 *
 * 当前终端是“只读日志终端”：前端可以看到任务输出，但不能向后端进程发送 stdin。
 * 因此这里会接受少量客户端控制消息，例如 resize；对于 stdin 会明确返回错误提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    // Spring 的 WebSocketSession 自带 attributes Map；这里用固定 key 缓存解析出的 sessionId。
    // 缓存后，后续收到消息、异常或关闭事件时，不需要每次都重新解析 URL。
    private static final String SESSION_ID_ATTR = "terminalSessionId";

    // 负责管理 sessionId 与 WebSocketSession 的订阅关系，并向订阅者推送 stdout/error/exit 消息。
    private final TerminalLogPublisher terminalLogPublisher;

    // 用来解析前端发来的文本消息。这里只需要读取 type 字段，所以直接使用 JsonNode。
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 当前端成功建立 WebSocket 连接后，Spring 会调用这个方法。
     *
     * 连接地址由 WebSocketConfig 注册为 { /ws/terminal/*}，
     * 所以这里把 URL 最后一段当作 sessionId。拿到 sessionId 后，把当前连接
     * 注册到 TerminalLogPublisher 中。这样后续任务进程输出日志时，
     * publisher 就能根据同一个 sessionId 找到对应前端连接并推送消息。
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            // 没有 sessionId 就无法知道日志该发给哪个任务会话，直接关闭连接更清晰。
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        // 将 sessionId 存入会话属性，后续消息处理、异常处理、关闭处理都可以直接取用。
        session.getAttributes().put(SESSION_ID_ATTR, sessionId);

        // 订阅：把这个 WebSocket 会话加入 sessionId 对应的订阅者集合。
        // 一个 sessionId 可以有多个连接订阅，例如用户刷新页面或打开多个终端视图。
        terminalLogPublisher.subscribe(sessionId, session);
    }

    /**
     * 当前端通过 WebSocket 发送文本消息时，Spring 会调用这个方法。
     *
     * 这个处理器不会把前端输入转发给真实进程。它只识别少量协议消息：
     * { resize} 表示终端窗口大小变化，目前仅兼容接收；
     * { stdin} 表示用户试图输入命令，但只读日志终端不允许这种操作。
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = getSessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            // 理论上连接建立时已经缓存过 sessionId；这里再兜底一次，防止异常状态继续处理消息。
            closeSilently(session, CloseStatus.BAD_DATA.withReason("Missing sessionId"));
            return;
        }

        try {
            // 约定前端消息是 JSON，例如 {"type":"resize"} 或 {"type":"stdin","data":"..."}。
            JsonNode root = objectMapper.readTree(message.getPayload());

            // path("type").asText("") 可以避免 type 缺失时抛异常，缺失会进入 default 分支。
            String type = root.path("type").asText("");

            switch (type) {
                case "resize" -> {
                    // 终端窗口大小调整消息。当前后端只是日志流，不维护真实 PTY 尺寸，
                    // 所以这里接受但不处理（no-op），主要用于兼容前端终端组件的默认行为。
                }
                case "stdin" -> terminalLogPublisher.publishError(sessionId,
                        "stdin is disabled for task log sessions");
                default -> terminalLogPublisher.publishError(sessionId, "unsupported message type: " + type);
            }
        } catch (Exception e) {
            // JSON 格式错误或解析失败时，不关闭连接，只给当前日志会话推送一条错误消息。
            // 这样前端还能继续接收后续任务日志。
            terminalLogPublisher.publishError(sessionId, "invalid message: " + e.getMessage());
        }
    }

    /**
     * WebSocket 底层传输发生异常时调用，例如网络断开、客户端异常关闭等。
     *
     * 这里先取消订阅，再尝试关闭连接，避免 publisher 后续继续向一个异常连接推送日志。
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            terminalLogPublisher.unsubscribe(sessionId, session);
        }
        closeSilently(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * WebSocket 正常关闭或被动关闭后调用。
     *
     * 无论关闭原因是什么，都需要把当前 WebSocketSession 从订阅表里移除，
     * 否则后续任务日志还会尝试发给已经关闭的连接。
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        if (StringUtils.hasText(sessionId)) {
            terminalLogPublisher.unsubscribe(sessionId, session);
        }
    }

    /**
     * 获取当前 WebSocket 会话对应的 sessionId。
     *
     * 优先从 attributes 缓存中取，因为连接建立时已经解析并保存过；
     * 如果缓存不存在，再从 URL 中兜底解析。这个兜底能让异常路径上的清理逻辑更稳。
     */
    private String getSessionId(WebSocketSession session) {
        Object value = session.getAttributes().get(SESSION_ID_ATTR);
        if (value instanceof String sid && StringUtils.hasText(sid)) {
            return sid;
        }
        return extractSessionId(session);
    }

    /**
     * 从 WebSocket 请求 URL 的最后一段提取 sessionId。
     *
     * 例如连接路径是 {/ws/terminal/abc-123}，这里会返回 {abc-123}。
     * 最后使用 URLDecoder，是为了支持前端对特殊字符做 URL 编码后的 sessionId。
     */
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

    /**
     * 安静地关闭 WebSocket 连接。
     *
     * 关闭连接本身也可能失败，例如网络已经断开。这里捕获 IOException 并只打 debug 日志，
     * 避免清理阶段的异常影响主流程。
     */
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
