package com.repopilot.terminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//Spring Boot 应用启动注解，扫描 terminal 和 common 两个模块的组件
@SpringBootApplication(scanBasePackages = {"com.repopilot.terminal", "com.repopilot.common"})
public class TerminalServiceApplication {

    //Terminal 模块的入口方法，启动独立的 Spring Boot 应用
    public static void main(String[] args) {
        SpringApplication.run(TerminalServiceApplication.class, args);
    }
}
