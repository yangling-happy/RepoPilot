package com.repopilot.business.dto;

import lombok.Data;

import java.time.LocalDateTime;

// 文档查询返回中的单文件记录模型。
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocQueryItem {

    //GitLab 用户名
    private String gitlabUsername;
    //项目名称
    private String project;
    //分支名称
    private String branch;
    //源文件在仓库中的路径
    private String filePath;
    //源文件所属的 commit hash
    private String commitId;
    //生成的文档文件路径
    private String docFilePath;
    //解析状态（如 SUCCESS、FAILED）
    private String parseStatus;
    //解析失败时的错误信息
    private String parseErrorMsg;
    //结构化的文档内容（解析成功时才有值）
    private DocStructuredContent structuredDoc;
    //记录最后更新时间
    private LocalDateTime updateTime;
}
