package com.repopilot.terminal.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class PtySession {

    @Getter
    private final String sessionId;

    private final Process process;
    private final OutputStream processInput;
    private final Thread outputPumpThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public PtySession(String sessionId, Consumer<String> stdoutConsumer) throws IOException {
        this.sessionId = sessionId;
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash");
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.putAll(System.getenv());
        this.process = processBuilder.start();
        this.processInput = process.getOutputStream();
        this.outputPumpThread = startOutputPump(stdoutConsumer);
    }

    public void write(String input) throws IOException {
        if (closed.get()) {
            throw new IOException("PTY session is closed");
        }
        String normalizedInput = input.replace("\r\n", "\n").replace('\r', '\n');
        processInput.write(normalizedInput.getBytes(StandardCharsets.UTF_8));
        processInput.flush();
    }

    public void resize(int cols, int rows) {
        // Keep API compatibility for frontend resize messages.
        // Current minimal Linux shell session doesn't apply terminal size.
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            processInput.close();
        } catch (IOException e) {
            log.debug("Failed to close PTY input for session {}", sessionId, e);
        }
        process.destroy();
        outputPumpThread.interrupt();
    }

    private Thread startOutputPump(Consumer<String> stdoutConsumer) {
        Thread thread = new Thread(() -> pumpOutput(stdoutConsumer), "pty-output-" + sessionId);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void pumpOutput(Consumer<String> stdoutConsumer) {
        try (InputStream inputStream = process.getInputStream()) {
            byte[] buffer = new byte[2048];
            int read;
            while (!closed.get() && (read = inputStream.read(buffer)) != -1) {
                if (read > 0) {
                    stdoutConsumer.accept(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            if (!closed.get()) {
                stdoutConsumer.accept("\r\n[terminal error] " + e.getMessage() + "\r\n");
            }
        } finally {
            close();
        }
    }
}