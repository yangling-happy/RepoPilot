package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskStartRequest;
import com.repopilot.terminal.dto.TerminalTaskStartResponse;
import com.repopilot.terminal.dto.TerminalTaskStatusResponse;
import com.repopilot.terminal.dto.TerminalTaskType;
import com.repopilot.terminal.exception.TerminalTaskException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalTaskService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final ScriptRegistry scriptRegistry;
    private final TerminalLogPublisher terminalLogPublisher;
    private final ConcurrentMap<String, ScriptProcessSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TaskSnapshot> sessionSnapshots = new ConcurrentHashMap<>();
    private final Set<String> cancelledSessions = ConcurrentHashMap.newKeySet();

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
            cancelledSessions.remove(sessionId);
            sessionSnapshots.put(sessionId, TaskSnapshot.running(taskType));
            terminalLogPublisher.publishStdout(sessionId,
                    "[terminal-task] started taskType=" + taskType + ", sessionId=" + sessionId + "\r\n");
            processSession.start();
        }
        return new TerminalTaskStartResponse(sessionId, taskType, STATUS_RUNNING);
    }

    public TerminalTaskStatusResponse status(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        TaskSnapshot snapshot = sessionSnapshots.get(normalized);
        if (activeSessions.containsKey(normalized)) {
            if (snapshot == null) {
                snapshot = TaskSnapshot.running(null);
            }
            return toStatusResponse(normalized, snapshot.withStatus(STATUS_RUNNING));
        }
        if (snapshot == null) {
            throw new TerminalTaskException(404, "terminal session not found: " + normalized);
        }
        return toStatusResponse(normalized, snapshot);
    }

    public TerminalTaskStatusResponse stop(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        ScriptProcessSession session = activeSessions.get(normalized);
        if (session == null) {
            return status(normalized);
        }
        cancelledSessions.add(normalized);
        session.destroy();
        TaskSnapshot snapshot = sessionSnapshots.getOrDefault(normalized, TaskSnapshot.running(null));
        return toStatusResponse(normalized, snapshot.withStatus(STATUS_CANCELLED));
    }

    private ScriptProcessSession createProcessSession(String sessionId,
            TerminalTaskType taskType,
            ScriptLaunchPlan launchPlan) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            processBuilder.directory(launchPlan.workingDirectory().toFile());
            processBuilder.redirectErrorStream(true);

            Map<String, String> environment = processBuilder.environment();
            environment.put("REPOPILOT_SESSION_ID", sessionId);
            environment.put("REPOPILOT_TASK_TYPE", taskType.name());
            environment.putAll(new HashMap<>(launchPlan.environment()));

            Process process = processBuilder.start();
            return new ScriptProcessSession(
                    sessionId,
                    taskType,
                    process,
                    terminalLogPublisher,
                    exitCode -> onSessionExit(sessionId, taskType, exitCode));
        } catch (IOException e) {
            terminalLogPublisher.publishError(sessionId, "failed to start terminal task: " + e.getMessage());
            throw new TerminalTaskException(500, "failed to start terminal task: " + e.getMessage());
        }
    }

    private void onSessionExit(String sessionId, TerminalTaskType taskType, int exitCode) {
        activeSessions.remove(sessionId);
        boolean cancelled = cancelledSessions.remove(sessionId);
        String status;
        if (cancelled) {
            status = STATUS_CANCELLED;
        } else {
            status = exitCode == 0 ? STATUS_SUCCESS : STATUS_FAILED;
        }
        sessionSnapshots.put(sessionId, new TaskSnapshot(taskType, status, exitCode, Instant.now()));
    }

    private TerminalTaskStatusResponse toStatusResponse(String sessionId, TaskSnapshot snapshot) {
        return new TerminalTaskStatusResponse(
                sessionId,
                snapshot.taskType(),
                snapshot.status(),
                snapshot.exitCode());
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

    private record TaskSnapshot(
            TerminalTaskType taskType,
            String status,
            Integer exitCode,
            Instant updatedAt) {

        private static TaskSnapshot running(TerminalTaskType taskType) {
            return new TaskSnapshot(taskType, STATUS_RUNNING, null, Instant.now());
        }

        private TaskSnapshot withStatus(String status) {
            return new TaskSnapshot(taskType, status, exitCode, Instant.now());
        }
    }
}
