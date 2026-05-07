package com.repopilot.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

// 文档查询返回中的单文件记录模型。
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocQueryItem {

    private String gitlabUsername;
    private String project;
    private String branch;
    private String filePath;
    private String commitId;
    private String docFilePath;
    private String parseStatus;
    private String parseErrorMsg;
    private DocStructuredContent structuredDoc;
    private LocalDateTime updateTime;
}
