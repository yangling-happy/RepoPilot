package com.repopilot.terminal.controller;

import com.repopilot.common.dto.ApiResponse;
import com.repopilot.terminal.dto.InternalTerminalStdoutRequest;
import com.repopilot.terminal.service.TerminalLogPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//内部接口 Controller，供 business 模块通过 HTTP 调用
//路径前缀 /internal 表示这是内部服务间调用的接口，不对外暴露
@RestController
@RequestMapping("/internal/terminal/sessions")
@RequiredArgsConstructor
public class InternalTerminalSessionController {

    //终端日志发布器，负责将消息推送到 WebSocket 连接的前端
    private final TerminalLogPublisher terminalLogPublisher;

    @PostMapping("/{sessionId}/stdout")
    public ApiResponse<Void> emitStdout(@PathVariable String sessionId,
                                        @RequestBody InternalTerminalStdoutRequest request) {
        if (!StringUtils.hasText(sessionId)) {
            return ApiResponse.error(400, "sessionId is required");
        }
        String data = request == null ? null : request.getData();
        if (!StringUtils.hasText(data)) {
            return ApiResponse.error(400, "data is required");
        }

        boolean delivered = terminalLogPublisher.publishStdout(sessionId, data);
        if (!delivered) {
            return ApiResponse.error(404, "terminal session not found");
        }
        return ApiResponse.success("stdout delivered", null);
    }
}
