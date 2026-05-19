package com.repopilot.terminal.controller;

import com.repopilot.common.dto.ApiResponse;
import com.repopilot.common.terminal.ScriptTaskRunRequest;
import com.repopilot.common.terminal.ScriptTaskRunResult;
import com.repopilot.terminal.exception.TerminalTaskException;
import com.repopilot.terminal.service.TerminalTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/terminal/tasks")
@RequiredArgsConstructor
public class InternalTerminalTaskController {

    private final TerminalTaskService terminalTaskService;

    @PostMapping("/run")
    public ApiResponse<ScriptTaskRunResult> run(@RequestBody ScriptTaskRunRequest request) {
        try {
            return ApiResponse.success("terminal task completed", terminalTaskService.runAndWait(request));
        } catch (TerminalTaskException e) {
            return ApiResponse.error(e.getCode(), e.getMessage());
        }
    }
}
