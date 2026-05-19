package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

//脚本进程会话
//职责：管理一个 shell 脚本进程的生命周期，包括：
//  1. 读取进程的标准输出并通过 WebSocket 推送到前端
//  2. 等待进程退出并发布退出事件
@Slf4j
public class ScriptProcessSession {

    //WebSocket 会话 ID
    private final String sessionId;
    //任务类型
    private final TerminalTaskType taskType;
    //shell 脚本进程
    private final Process process;
    //日志发布器，将输出推送到 WebSocket
    private final TerminalLogPublisher terminalLogPublisher;
    //进程退出后的回调（传入 exitCode，用于从活跃会话列表中移除并记录状态）
    private final IntConsumer onExit;
    //防止重复启动的标志位（CAS 操作，线程安全）
    private final AtomicBoolean started = new AtomicBoolean(false);

    //输出读取线程
    private Thread outputThread;
    //进程退出等待线程
    private Thread waitThread;

    public ScriptProcessSession(String sessionId,
                                TerminalTaskType taskType,
                                Process process,
                                TerminalLogPublisher terminalLogPublisher,
                                IntConsumer onExit) {
        this.sessionId = sessionId;
        this.taskType = taskType;
        this.process = process;
        this.terminalLogPublisher = terminalLogPublisher;
        this.onExit = onExit;
    }

    //启动两个守护线程：一个读取输出，一个等待进程退出
    //使用 compareAndSet 确保只启动一次
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        //守护线程（daemon thread）：主线程退出后这些线程也会自动退出，不会阻止 JVM 关闭
        outputThread = new Thread(this::pumpOutput, "terminal-task-output-" + sessionId);
        outputThread.setDaemon(true);
        outputThread.start();

        waitThread = new Thread(this::waitForExit, "terminal-task-wait-" + sessionId);
        waitThread.setDaemon(true);
        waitThread.start();
    }

    //强制终止进程
    public void destroy() {
        process.destroy();
    }

    //持续读取进程的标准输出，实时推送到 WebSocket 前端
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

    //等待进程退出，然后发布退出事件并执行回调
    private void waitForExit() {
        int exitCode = -1;
        try {
            //阻塞等待进程退出，返回退出码
            exitCode = process.waitFor();
            //等待输出线程读取完剩余数据（最多等 1 秒）
            if (outputThread != null) {
                outputThread.join(1000);
            }
        } catch (InterruptedException e) {
            //恢复中断标志（Java 并发编程最佳实践）
            Thread.currentThread().interrupt();
            terminalLogPublisher.publishError(sessionId, "task wait interrupted");
        } finally {
            //无论成功还是失败，都发布退出事件
            terminalLogPublisher.publishExit(sessionId, exitCode);
            //执行回调，从活跃会话列表中移除并记录退出状态
            onExit.accept(exitCode);
            log.debug("Terminal task exited, sessionId={}, taskType={}, exitCode={}",
                    sessionId, taskType, exitCode);
        }
    }
}
