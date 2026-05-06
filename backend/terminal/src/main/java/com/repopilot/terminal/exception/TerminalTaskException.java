package com.repopilot.terminal.exception;

import lombok.Getter;

@Getter
public class TerminalTaskException extends RuntimeException {

    private final int code;

    public TerminalTaskException(int code, String message) {
        super(message);
        this.code = code;
    }
}
