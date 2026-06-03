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
    //默认克隆分支
    //如果前端没有传 branch，GitlabRepoCloneService 会优先使用这个配置
    //配置文件没写时，默认克隆 main 分支
    private String defaultBranch = "main";
    //JGit clone 命令的超时时间，单位秒
    //防止网络卡住时请求一直占用后端线程
    private Integer timeoutSeconds = 120;
}
