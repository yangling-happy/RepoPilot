package com.repopilot.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

//终端任务启动响应 DTO
@Data
@AllArgsConstructor
public class TerminalTaskStartResponse {

    //WebSocket 会话 ID
    private String sessionId;
    //任务类型枚举
    private TerminalTaskType taskType;
    //任务当前状态（如 "RUNNING"）
    private String status;
}
