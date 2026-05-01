package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ScriptProcessSession {

    private final String sessionId;
    private final TerminalTaskType taskType;
    private final Process process;
    private final TerminalLogPublisher terminalLogPublisher;
    private final Runnable onExit;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private Thread outputThread;
    private Thread waitThread;

    public ScriptProcessSession(String sessionId,
                                TerminalTaskType taskType,
                                Process process,
                                TerminalLogPublisher terminalLogPublisher,
                                Runnable onExit) {
        this.sessionId = sessionId;
        this.taskType = taskType;
        this.process = process;
        this.terminalLogPublisher = terminalLogPublisher;
        this.onExit = onExit;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        outputThread = new Thread(this::pumpOutput, "terminal-task-output-" + sessionId);
        outputThread.setDaemon(true);
        outputThread.start();

        waitThread = new Thread(this::waitForExit, "terminal-task-wait-" + sessionId);
        waitThread.setDaemon(true);
        waitThread.start();
    }

    public void destroy() {
        process.destroy();
    }

    private void pumpOutput() {
        try (InputStream inputStream = process.getInputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read > 0) {
                    terminalLogPublisher.publishStdout(
                            sessionId,
                            new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            terminalLogPublisher.publishError(sessionId, "failed to read task output: " + e.getMessage());
        }
    }

    private void waitForExit() {
        int exitCode = -1;
        try {
            exitCode = process.waitFor();
            if (outputThread != null) {
                outputThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminalLogPublisher.publishError(sessionId, "task wait interrupted");
        } finally {
            terminalLogPublisher.publishExit(sessionId, exitCode);
            onExit.run();
            log.debug("Terminal task exited, sessionId={}, taskType={}, exitCode={}",
                    sessionId, taskType, exitCode);
        }
    }
}
