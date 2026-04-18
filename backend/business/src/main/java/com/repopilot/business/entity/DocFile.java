package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("doc_file_dtl")
public class DocFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String projectName;
    private String branchName;
    private String filePath;
    private String commitId;
    private String docFilePath;
    private String parseStatus;
    private String parseErrorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
