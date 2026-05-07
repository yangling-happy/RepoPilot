package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//指定实体对应的数据库表名
@TableName("doc_file_dtl")
public class DocFile {

    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String gitlabUsername;
    private String projectName;
    private String branchName;
    private String filePath;
    private String commitId;
    private String docFilePath;
    private String parseStatus;
    private String parseErrorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getProject() {
        return projectName;
    }

    public void setProject(String project) {
        this.projectName = project;
    }

    public String getBranch() {
        return branchName;
    }

    public void setBranch(String branch) {
        this.branchName = branch;
    }
}
