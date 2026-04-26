package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("doc_task")
public class DocTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gitlabUsername;
    private String eventId;
    private String project;
    private String branch;
    private String commitId;
    private String status;
    private Integer duration;

    private LocalDateTime createTime;
}
