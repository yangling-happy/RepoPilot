package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskStartRequest;
import com.repopilot.terminal.dto.TerminalTaskStartResponse;
import com.repopilot.terminal.dto.TerminalTaskType;
import com.repopilot.terminal.exception.TerminalTaskException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
}
