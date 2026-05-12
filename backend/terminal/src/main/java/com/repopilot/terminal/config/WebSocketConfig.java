package com.repopilot.terminal.config;

import com.repopilot.terminal.handler.TerminalWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

//Spring 配置类注解
@Configuration
//开启 WebSocket 支持
@EnableWebSocket
//Lombok: 为 final 字段生成构造函数
@RequiredArgsConstructor
//WebSocket 配置类：注册 WebSocket 处理器并指定访问路径
public class WebSocketConfig implements WebSocketConfigurer {

    //WebSocket 消息处理器（处理连接、消息、关闭等事件）
    private final TerminalWebSocketHandler terminalWebSocketHandler;

    //注册 WebSocket 处理器
    //路径 /ws/terminal/{sessionId} 映射到 terminalWebSocketHandler
    //setAllowedOrigins("*") 允许所有来源的跨域连接（开发阶段方便调试）
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal/*")
                .setAllowedOrigins("*");
    }
}
