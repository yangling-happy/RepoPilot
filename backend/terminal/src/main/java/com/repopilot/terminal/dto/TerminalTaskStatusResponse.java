package com.repopilot.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TerminalTaskStatusResponse {

    private String sessionId;
    private TerminalTaskType taskType;
    private String status;
    private Integer exitCode;
}
