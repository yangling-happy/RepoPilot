package com.repopilot.common.terminal;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ScriptTaskRunResult {

    private String sessionId;

    private String taskType;

    private int exitCode;

    private boolean timedOut;

    private String stdout;

    private String stderr;

    private Map<String, String> results = new LinkedHashMap<>();

    private long durationMillis;
}
