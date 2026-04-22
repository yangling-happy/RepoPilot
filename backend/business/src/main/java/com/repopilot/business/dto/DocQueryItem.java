package com.repopilot.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

// 文档查询返回中的单文件记录模型。
@Data
public class DocQueryItem {

    private String project;
    private String branch;
    private String filePath;
    private String commitId;
    private String docFilePath;
    private String parseStatus;
    private String parseErrorMsg;
    private LocalDateTime updateTime;
}
