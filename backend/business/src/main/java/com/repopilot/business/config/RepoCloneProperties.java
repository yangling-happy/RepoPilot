package com.repopilot.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//Spring Boot 注解，表示该类会从配置文件（如 application.yml 或 application.properties）中读取指定前缀的属性并绑定到字段上
@Component
//Spring 的类级别注解，将该类作为一个 Spring Bean 交由容器管理，使得其他地方可以通过 @Autowired 注入使用
@ConfigurationProperties(prefix = "repo.clone")
public class RepoCloneProperties {
    //如果配置文件没写defaultBranch和timeoutSeconds，则保持以下默认值
    private String defaultBranch = "main";
    private Integer timeoutSeconds = 120;
}
