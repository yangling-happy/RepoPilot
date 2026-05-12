package com.repopilot.terminal.controller;

import com.repopilot.common.dto.ApiResponse;
import com.repopilot.terminal.dto.TerminalTaskStartRequest;
import com.repopilot.terminal.dto.TerminalTaskStartResponse;
import com.repopilot.terminal.exception.TerminalTaskException;
import com.repopilot.terminal.service.TerminalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//终端任务 Controller，提供对外的 REST API
@RestController
@RequestMapping("/api/terminal/tasks")
@RequiredArgsConstructor
public class TerminalTaskController {

    //终端任务服务，负责启动和管理终端任务
    private final TerminalTaskService terminalTaskService;

    //启动一个终端任务（如克隆仓库、刷新文档等）
    //请求体中包含任务类型、参数和 WebSocket 会话 ID
    @PostMapping("/start")
    public ApiResponse<TerminalTaskStartResponse> start(@RequestBody TerminalTaskStartRequest request) {
        try {
            TerminalTaskStartResponse response = terminalTaskService.start(request);
            return ApiResponse.success("terminal task started", response);
        } catch (TerminalTaskException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }
}
