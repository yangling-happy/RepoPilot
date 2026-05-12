package com.repopilot.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//SpringBootApplication 是 Spring Boot 的核心启动注解，它是一个组合注解，包含：
//  @Configuration - 标记这是一个配置类
//  @EnableAutoConfiguration - 开启自动配置（Spring Boot 会根据依赖自动配置 Bean）
//  @ComponentScan - 开启组件扫描
//scanBasePackages 指定扫描哪些包下的组件（@Component、@Service、@Controller 等）
//这里扫描了 business 和 common 两个模块的包
@SpringBootApplication(scanBasePackages = {"com.repopilot.business", "com.repopilot.common"})
//MyBatis-Plus 注解，指定扫描哪个包下的 Mapper 接口，将它们注册为 Spring Bean
//如果不加这个注解，Mapper 接口不会被 Spring 识别，注入时会报错
@MapperScan("com.repopilot.business.mapper")
public class BusinessServiceApplication {

    //Spring Boot 应用的入口方法
    //SpringApplication.run() 会启动整个 Spring 容器，自动扫描、创建和装配所有 Bean
    public static void main(String[] args) {
        SpringApplication.run(BusinessServiceApplication.class, args);
    }
}
