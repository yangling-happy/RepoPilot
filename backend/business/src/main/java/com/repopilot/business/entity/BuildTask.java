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
//构建任务数据库实体
//
//它对应 build_task 表中的一行记录，用来描述一次构建过程：
//谁发起、哪个项目/分支/commit、脚本在哪里、产物在哪里、运行状态如何。
public class BuildTask {
    
    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    //发起构建的 GitLab 用户名
    private String gitlabUsername;
    //构建任务的业务唯一标识（由调用方生成）
    private String buildTaskId;
    //关联的部署任务 ID
    private String deployTaskId;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //触发构建的 commit hash
    private String commitId;
    //构建脚本路径
    private String scriptPath;
    //构建产物路径
    private String artifactPath;
    //日志目录路径
    private String logDirPath;
    //构建状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED/TIMEOUT
    private String runStatus;
    //构建失败时的错误信息
    private String errorMsg;
    //构建开始时间
    private LocalDateTime startTime;
    //构建耗时（秒）
    private Integer duration;
    //记录创建时间（数据库自动填充）
    private LocalDateTime createTime;
    //记录最后更新时间（数据库自动填充）
    private LocalDateTime updateTime;
}
