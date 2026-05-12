package com.repopilot.terminal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.terminal.dto.TerminalTaskStartRequest;
import com.repopilot.terminal.dto.TerminalTaskStartResponse;
import com.repopilot.terminal.dto.TerminalTaskType;
import com.repopilot.terminal.exception.TerminalTaskException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalTaskService {

    private final ScriptRegistry scriptRegistry;
    private final TerminalLogPublisher terminalLogPublisher;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ScriptProcessSession> activeSessions = new ConcurrentHashMap<>();

    @Value("${terminal.tasks.workspace-base-dir:.}")
    private String workspaceBaseDir;

    @Value("${terminal.tasks.business-jar:}")
    private String businessJar;

    @Value("${terminal.tasks.backend-root:}")
    private String configuredBackendRoot;

    public TerminalTaskStartResponse start(TerminalTaskStartRequest request) {
        if (request == null) {
            throw new TerminalTaskException(400, "request body is required");
        }
        String sessionId = normalizeSessionId(request.getSessionId());
        TerminalTaskType taskType = scriptRegistry.parseTaskType(request.getTaskType());
        ScriptLaunchPlan launchPlan = scriptRegistry.createLaunchPlan(taskType, request.getArgs());

        synchronized (activeSessions) {
            if (activeSessions.containsKey(sessionId)) {
                throw new TerminalTaskException(409, "terminal task already running for sessionId: " + sessionId);
            }

            ScriptProcessSession processSession = createProcessSession(sessionId, taskType, launchPlan);
            activeSessions.put(sessionId, processSession);
            terminalLogPublisher.publishStdout(sessionId,
                    "[terminal-task] started taskType=" + taskType + ", sessionId=" + sessionId + "\r\n");
            processSession.start();
        }
        return new TerminalTaskStartResponse(sessionId, taskType, "RUNNING");
    }

    private ScriptProcessSession createProcessSession(String sessionId,
                                                      TerminalTaskType taskType,
                                                      ScriptLaunchPlan launchPlan) {
        Path resultFile = createResultFile(sessionId);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            processBuilder.directory(launchPlan.workingDirectory().toFile());
            processBuilder.redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            environment.put("REPOPILOT_SESSION_ID", sessionId);
            environment.put("REPOPILOT_TASK_TYPE", taskType.name());
            environment.put("REPOPILOT_TASK_RESULT_FILE", resultFile.toString());
            environment.put("REPOPILOT_WORKSPACE_BASE", resolveWorkspaceBaseDir().toString());
            environment.put("REPOPILOT_BACKEND_ROOT", resolveBackendRoot().toString());
            if (StringUtils.hasText(businessJar)) {
                environment.put("REPOPILOT_BUSINESS_CLI_JAR", Path.of(businessJar).toAbsolutePath().normalize().toString());
            }
            environment.putAll(new HashMap<>(launchPlan.environment()));

            Process process = processBuilder.start();
            return new ScriptProcessSession(
                    sessionId,
                    taskType,
                    process,
                    terminalLogPublisher,
                    objectMapper,
                    resultFile,
                    () -> activeSessions.remove(sessionId));
        } catch (IOException e) {
            try {
                Files.deleteIfExists(resultFile);
            } catch (IOException ignored) {
                // Best effort cleanup for a task that never started.
            }
            terminalLogPublisher.publishError(sessionId, "failed to start terminal task: " + e.getMessage());
            throw new TerminalTaskException(500, "failed to start terminal task: " + e.getMessage());
        }
    }

    private Path createResultFile(String sessionId) {
        try {
            String safeSession = sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
            return Files.createTempFile("repopilot-task-" + safeSession + "-", ".json")
                    .toAbsolutePath()
                    .normalize();
        } catch (IOException e) {
            throw new TerminalTaskException(500, "failed to create terminal task result file: " + e.getMessage());
        }
    }

    private Path resolveWorkspaceBaseDir() {
        if (StringUtils.hasText(workspaceBaseDir)) {
            return Path.of(workspaceBaseDir).toAbsolutePath().normalize();
        }
        return resolveBackendRoot();
    }

    private Path resolveBackendRoot() {
        if (StringUtils.hasText(configuredBackendRoot)) {
            return Path.of(configuredBackendRoot).toAbsolutePath().normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("business").resolve("pom.xml"))) {
            return cwd;
        }
        if (Files.exists(cwd.resolve("backend").resolve("business").resolve("pom.xml"))) {
            return cwd.resolve("backend").toAbsolutePath().normalize();
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("business").resolve("pom.xml"))) {
            return parent;
        }
        if (parent != null && Files.exists(parent.resolve("backend").resolve("business").resolve("pom.xml"))) {
            return parent.resolve("backend").toAbsolutePath().normalize();
        }
        return cwd;
    }

    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new TerminalTaskException(400, "sessionId is required");
        }
        String normalized = sessionId.trim();
        if (normalized.indexOf('\0') >= 0) {
            throw new TerminalTaskException(400, "sessionId contains an invalid character");
        }
        return normalized;
    }
}
