package com.repopilot.terminal.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TerminalTaskStartRequest {

    private String sessionId;
    private String taskType;
    private Map<String, Object> args = new LinkedHashMap<>();
}
