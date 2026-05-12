package com.repopilot.terminal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.terminal.dto.TerminalTaskType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ScriptProcessSession {

    private final String sessionId;
    private final TerminalTaskType taskType;
    private final Process process;
    private final TerminalLogPublisher terminalLogPublisher;
    private final ObjectMapper objectMapper;
    private final Path resultFile;
    private final Runnable onExit;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private Thread outputThread;
    private Thread waitThread;

    public ScriptProcessSession(String sessionId,
                                TerminalTaskType taskType,
                                Process process,
                                TerminalLogPublisher terminalLogPublisher,
                                ObjectMapper objectMapper,
                                Path resultFile,
                                Runnable onExit) {
        this.sessionId = sessionId;
        this.taskType = taskType;
        this.process = process;
        this.terminalLogPublisher = terminalLogPublisher;
        this.objectMapper = objectMapper;
        this.resultFile = resultFile;
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
            publishResult();
            terminalLogPublisher.publishExit(sessionId, exitCode);
            onExit.run();
            log.debug("Terminal task exited, sessionId={}, taskType={}, exitCode={}",
                    sessionId, taskType, exitCode);
        }
    }

    private void publishResult() {
        if (resultFile == null || !Files.isRegularFile(resultFile)) {
            return;
        }
        try {
            JsonNode result = objectMapper.readTree(resultFile.toFile());
            terminalLogPublisher.publishResult(sessionId, taskType.name(), result);
        } catch (Exception e) {
            terminalLogPublisher.publishError(sessionId, "failed to read task result: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(resultFile);
            } catch (IOException e) {
                log.debug("Failed to delete terminal task result file: {}", resultFile, e);
            }
        }
    }
}
