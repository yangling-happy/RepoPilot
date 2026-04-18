package com.repopilot.terminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.repopilot.terminal", "com.repopilot.common"})
public class TerminalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerminalServiceApplication.class, args);
    }
}
