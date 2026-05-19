package com.repopilot.common.terminal;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ScriptTaskRunRequest {

    private String sessionId;

    private String taskType;

    private Map<String, Object> args = new LinkedHashMap<>();

    private Map<String, String> environment = new LinkedHashMap<>();

    private Map<String, String> secretEnvironment = new LinkedHashMap<>();

    private Long timeoutSeconds;
}
