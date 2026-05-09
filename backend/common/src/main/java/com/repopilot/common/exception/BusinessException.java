package com.repopilot.common.exception;

import lombok.Getter;

//lombok注解，只有@Getter，没有 @Setter
//因为 code 和继承来的 message（来自 Throwable）应该在构造时确定，异常对象通常不修改字段值
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    //仅消息（默认500错误）
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    //自定义错误码 + 消息
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    //消息 + 原始异常
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
}
