package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ScriptLaunchPlan(
        TerminalTaskType taskType,
        List<String> command,
        Map<String, String> environment,
        Path workingDirectory) {
}
