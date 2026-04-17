package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("deploy_task")
public class DeployTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;
    @TableField("project_name")
    private String project;
    @TableField("branch_name")
    private String branch;
    private String commitId;
    private String scriptName;
    private String args;
    @TableField("run_status")
    private String status;
    private String operator;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
