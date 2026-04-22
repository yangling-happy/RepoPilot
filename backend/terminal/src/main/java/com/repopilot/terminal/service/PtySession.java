package com.repopilot.terminal.service;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class PtySession {

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;

    @Getter
    private final String sessionId;

    private final PtyProcess process;
    private final OutputStream processInput;
    private final Thread outputPumpThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public PtySession(String sessionId, Consumer<String> stdoutConsumer) throws IOException {
        this.sessionId = sessionId;
        this.process = startPtyProcess();
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
        if (closed.get()) {
            return;
        }

        int safeCols = Math.max(1, cols);
        int safeRows = Math.max(1, rows);
        try {
            process.setWinSize(new WinSize(safeCols, safeRows));
        } catch (Exception e) {
            log.debug("Failed to resize PTY for session {}", sessionId, e);
        }
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

    private PtyProcess startPtyProcess() throws IOException {
        return new PtyProcessBuilder(createShellCommand())
                .setDirectory(System.getProperty("user.dir"))
                .setEnvironment(new HashMap<>(System.getenv()))
                .setRedirectErrorStream(true)
                .setInitialColumns(DEFAULT_COLUMNS)
                .setInitialRows(DEFAULT_ROWS)
                .start();
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    private String[] createShellCommand() {
        if (isWindows()) {
            return new String[] { "powershell.exe", "-NoLogo" };
        }
        return new String[] { "/bin/bash", "-i" };
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