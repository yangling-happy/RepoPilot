package com.repopilot.terminal.controller;

import com.repopilot.common.dto.ApiResponse;
import com.repopilot.terminal.dto.TerminalTaskStartRequest;
import com.repopilot.terminal.dto.TerminalTaskStartResponse;
import com.repopilot.terminal.dto.TerminalTaskStatusResponse;
import com.repopilot.terminal.exception.TerminalTaskException;
import com.repopilot.terminal.service.TerminalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/terminal/tasks")
@RequiredArgsConstructor
public class TerminalTaskController {

    private final TerminalTaskService terminalTaskService;

    @PostMapping("/start")
    public ApiResponse<TerminalTaskStartResponse> start(@RequestBody TerminalTaskStartRequest request) {
        try {
            TerminalTaskStartResponse response = terminalTaskService.start(request);
            return ApiResponse.success("terminal task started", response);
        } catch (TerminalTaskException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<TerminalTaskStatusResponse> status(@RequestParam String sessionId) {
        try {
            TerminalTaskStatusResponse response = terminalTaskService.status(sessionId);
            return ApiResponse.success("terminal task status", response);
        } catch (TerminalTaskException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ApiResponse<TerminalTaskStatusResponse> stop(@RequestParam String sessionId) {
        try {
            TerminalTaskStatusResponse response = terminalTaskService.stop(sessionId);
            return ApiResponse.success("terminal task stop requested", response);
        } catch (TerminalTaskException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }
}
