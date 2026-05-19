package com.repopilot.terminal.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

//终端任务启动请求 DTO
@Data
public class TerminalTaskStartRequest {

    //WebSocket 会话 ID，任务输出会推送到这个会话
    private String sessionId;
    //任务类型（如 CLONE_REPO、REFRESH_DOC 等），字符串形式，后续会转为枚举
    private String taskType;
    //任务参数键值对（如 projectId、branch、repoDir 等），密钥不允许放在这里
    //使用 LinkedHashMap 保持参数插入顺序
    private Map<String, Object> args = new LinkedHashMap<>();
}
