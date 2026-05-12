package com.repopilot.terminal.exception;

import lombok.Getter;

//终端任务异常类
//与 business 模块的 BusinessException 类似，但使用 int 类型的错误码
@Getter
public class TerminalTaskException extends RuntimeException {

    //业务错误码
    private final int code;

    public TerminalTaskException(int code, String message) {
        super(message);
        this.code = code;
    }
}
