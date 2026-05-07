package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//指定实体对应的数据库表名
@TableName("build_task")
public class BuildTask {
    
    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    private String gitlabUsername;
    private String buildTaskId;
    private String deployTaskId;
    private String projectName;
    private String branchName;
    private String commitId;
    private String scriptPath;
    private String artifactPath;
    private String logDirPath;
    private String runStatus;
    private String errorMsg;
    private LocalDateTime startTime;
    private Integer duration;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
