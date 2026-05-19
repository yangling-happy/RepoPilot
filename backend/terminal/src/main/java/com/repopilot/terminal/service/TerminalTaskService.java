package com.repopilot.terminal.service;

import com.repopilot.common.terminal.ScriptTaskRunRequest;
import com.repopilot.common.terminal.ScriptTaskRunResult;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

//终端任务服务
//职责：接收任务启动请求，创建 shell 进程执行脚本，并管理任务的生命周期
//使用 ConcurrentHashMap 保证同一 sessionId 同时只能运行一个任务
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalTaskService {

    //脚本注册表，用于将任务类型映射到具体的 shell 脚本
    private final ScriptRegistry scriptRegistry;
    //日志发布器
    private final TerminalLogPublisher terminalLogPublisher;
    //活跃的任务会话映射：sessionId -> 正在运行的脚本进程
    //用于防止同一会话重复启动任务，以及支持任务销毁
    private final ConcurrentMap<String, ScriptProcessSession> activeSessions = new ConcurrentHashMap<>();

    @Value("${terminal.tasks.default-timeout-seconds:600}")
    private long defaultTimeoutSeconds;

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

    public ScriptTaskRunResult runAndWait(ScriptTaskRunRequest request) {
        if (request == null) {
            throw new TerminalTaskException(400, "request body is required");
        }

        String sessionId = normalizeOptionalSessionId(request.getSessionId());
        TerminalTaskType taskType = scriptRegistry.parseTaskType(request.getTaskType());
        ScriptLaunchPlan launchPlan = scriptRegistry.createLaunchPlan(taskType, request.getArgs());
        long timeoutSeconds = resolveTimeoutSeconds(request.getTimeoutSeconds());
        long startedAt = System.currentTimeMillis();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            processBuilder.directory(launchPlan.workingDirectory().toFile());
            processBuilder.redirectErrorStream(false);

            Map<String, String> environment = processBuilder.environment();
            if (StringUtils.hasText(sessionId)) {
                environment.put("REPOPILOT_SESSION_ID", sessionId);
            }
            environment.put("REPOPILOT_TASK_TYPE", taskType.name());
            environment.putAll(new HashMap<>(launchPlan.environment()));
            addEnvironment(environment, request.getEnvironment());
            addEnvironment(environment, request.getSecretEnvironment());

            if (StringUtils.hasText(sessionId)) {
                terminalLogPublisher.publishStdout(sessionId,
                        "[terminal-task] started taskType=" + taskType + ", sessionId=" + sessionId + "\r\n");
            }

            Process process = processBuilder.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread stdoutThread = pumpStream(process.getInputStream(), stdout, sessionId);
            Thread stderrThread = pumpStream(process.getErrorStream(), stderr, sessionId);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            boolean timedOut = false;
            int exitCode;
            if (!finished) {
                timedOut = true;
                process.destroyForcibly();
                exitCode = -1;
                if (StringUtils.hasText(sessionId)) {
                    terminalLogPublisher.publishError(sessionId, "terminal task timed out");
                }
            } else {
                exitCode = process.exitValue();
            }

            joinQuietly(stdoutThread);
            joinQuietly(stderrThread);

            if (StringUtils.hasText(sessionId)) {
                terminalLogPublisher.publishExit(sessionId, exitCode);
            }

            ScriptTaskRunResult result = new ScriptTaskRunResult();
            result.setSessionId(sessionId);
            result.setTaskType(taskType.name());
            result.setExitCode(exitCode);
            result.setTimedOut(timedOut);
            result.setStdout(stdout.toString());
            result.setStderr(stderr.toString());
            result.setResults(parseMachineResults(result.getStdout(), result.getStderr()));
            result.setDurationMillis(System.currentTimeMillis() - startedAt);
            return result;
        } catch (IOException e) {
            throw new TerminalTaskException(500, "failed to start terminal task: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TerminalTaskException(500, "terminal task interrupted");
        }
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
                    () -> activeSessions.remove(sessionId));
        } catch (IOException e) {
            terminalLogPublisher.publishError(sessionId, "failed to start terminal task: " + e.getMessage());
            throw new TerminalTaskException(500, "failed to start terminal task: " + e.getMessage());
        }
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

    private String normalizeOptionalSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String normalized = sessionId.trim();
        if (normalized.indexOf('\0') >= 0) {
            throw new TerminalTaskException(400, "sessionId contains an invalid character");
        }
        return normalized;
    }

    private long resolveTimeoutSeconds(Long requestedTimeoutSeconds) {
        if (requestedTimeoutSeconds != null && requestedTimeoutSeconds > 0) {
            return requestedTimeoutSeconds;
        }
        return defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : 600;
    }

    private void addEnvironment(Map<String, String> target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private Thread pumpStream(InputStream inputStream, StringBuilder target, String sessionId) {
        Thread thread = new Thread(() -> {
            try (InputStream in = inputStream) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (read <= 0) {
                        continue;
                    }
                    String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    synchronized (target) {
                        target.append(chunk);
                    }
                    if (StringUtils.hasText(sessionId)) {
                        terminalLogPublisher.publishStdout(sessionId, chunk);
                    }
                }
            } catch (IOException e) {
                if (StringUtils.hasText(sessionId)) {
                    terminalLogPublisher.publishError(sessionId, "failed to read task output: " + e.getMessage());
                }
            }
        }, "terminal-task-sync-output-" + System.nanoTime());
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void joinQuietly(Thread thread) throws InterruptedException {
        if (thread != null) {
            thread.join(1000);
        }
    }

    private Map<String, String> parseMachineResults(String... outputs) {
        Map<String, String> results = new LinkedHashMap<>();
        for (String output : outputs) {
            if (!StringUtils.hasText(output)) {
                continue;
            }
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("REPOPILOT_RESULT_")) {
                    continue;
                }
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex <= "REPOPILOT_RESULT_".length()) {
                    continue;
                }
                String key = trimmed.substring("REPOPILOT_RESULT_".length(), equalsIndex);
                String value = trimmed.substring(equalsIndex + 1);
                results.put(key, value);
            }
        }
        return results;
    }
}
