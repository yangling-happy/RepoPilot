package com.repopilot.business.dto;

import lombok.Data;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CreateDocFileRequest {

    //关联的文档任务 ID（对应 doc_task 表的主键）
    private Long taskId;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //源文件在仓库中的路径（如 src/main/java/Demo.java）
    private String filePath;
    //源文件所属的 commit hash
    private String commitId;
    //生成的文档文件路径
    private String docFilePath;
    //解析状态（如 SUCCESS、FAILED）
    private String parseStatus;
    //解析失败时的错误信息
    private String parseErrorMsg;
}
