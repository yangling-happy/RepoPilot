package com.repopilot.terminal.service;

import com.repopilot.terminal.dto.TerminalTaskType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

//脚本启动计划（不可变数据对象）
//封装了启动一个 shell 脚本所需的全部信息
//使用 Java record 语法，自动生成 getter、equals、hashCode、toString
public record ScriptLaunchPlan(
        //任务类型
        TerminalTaskType taskType,
        //完整的命令行（如 ["bash", "/path/to/script.sh", "--project", "xxx"]）
        List<String> command,
        //环境变量（如 {"GITLAB_TOKEN": "xxx"}）
        Map<String, String> environment,
        //脚本执行的工作目录
        Path workingDirectory) {
}
