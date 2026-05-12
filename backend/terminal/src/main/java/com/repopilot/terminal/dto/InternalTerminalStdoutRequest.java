package com.repopilot.terminal.dto;

import lombok.Data;

//内部终端标准输出请求 DTO
//business 模块通过 HTTP POST 调用 terminal 模块的内部接口时，用这个对象传递消息内容
@Data
public class InternalTerminalStdoutRequest {

    //要推送到 WebSocket 终端的文本数据
    private String data;
}
