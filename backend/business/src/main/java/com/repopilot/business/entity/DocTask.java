package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//指定实体对应的数据库表名
@TableName("doc_task")
//文档任务数据库实体
//
//它对应 doc_task 表中的一行记录，用来描述一次文档生成任务。
//任务级别记录整体状态和耗时，具体到每个源文件的生成结果则记录在 doc_file_dtl 表里。
public class DocTask {

    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    //任务所属的 GitLab 用户名
    //所有查询都会带上用户名，避免不同用户之间看到彼此的任务数据
    private String gitlabUsername;
    //业务事件 ID，用来追踪任务来源
    //例如 doc-local-scan:{commitId}，或者 webhook 事件 ID
    private String eventId;
    //GitLab 项目 ID 或项目标识
    private String project;
    //分支名
    private String branch;
    //本次任务处理的 commit hash
    private String commitId;
    //任务状态：RUNNING/SUCCESS/FAILED/SKIPPED/PENDING
    //DocPipelineServiceImpl 会根据生成结果更新这个字段
    private String status;
    //任务耗时，单位秒
    private Integer duration;

    //记录创建时间，通常由数据库或 MyBatis-Plus 自动填充
    private LocalDateTime createTime;
}
