package com.repopilot.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TerminalTaskStartResponse {

    private String sessionId;
    private TerminalTaskType taskType;
    private String status;
}
