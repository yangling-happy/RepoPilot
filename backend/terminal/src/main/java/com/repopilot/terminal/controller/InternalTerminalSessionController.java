package com.repopilot.terminal.controller;

import com.repopilot.common.dto.ApiResponse;
import com.repopilot.terminal.dto.InternalTerminalStdoutRequest;
import com.repopilot.terminal.service.PtySessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/terminal/sessions")
@RequiredArgsConstructor
public class InternalTerminalSessionController {

    private final PtySessionManager ptySessionManager;

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

        boolean delivered = ptySessionManager.emitStdout(sessionId, data);
        if (!delivered) {
            return ApiResponse.error(404, "terminal session not found");
        }
        return ApiResponse.success("stdout delivered", null);
    }
}
