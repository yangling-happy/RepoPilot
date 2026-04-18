package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("doc_file_dtl")
public class DocFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("project_name")
    private String project;
    @TableField("branch_name")
    private String branch;
    private String filePath;
    private String commitId;
    private String docJson;
    private String docMarkdown;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
