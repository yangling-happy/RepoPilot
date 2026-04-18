package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("deploy_task")
public class DeployTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deployTaskId;
    private String projectName;
    private String branchName;
    private String commitId;
    private String deployParams;
    private String runStatus;
    private String logDirPath;
    private String resultPath;
    private String errorMsg;
    private LocalDateTime startTime;
    private Integer duration;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
