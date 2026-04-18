package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("build_task")
public class BuildTask {

    @TableId(type = IdType.AUTO)
    private Long id;

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
