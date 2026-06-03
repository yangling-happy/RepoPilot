package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//指定实体对应的数据库表名
@TableName("doc_file_dtl")
//文档文件明细数据库实体
//
//一条 DocTask 可能会处理多个源文件，每个源文件的文档生成结果都会落一条 DocFile。
//因此它更像“文档任务的明细行”：记录源文件路径、生成产物路径、解析状态和错误信息。
public class DocFile {

    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    //关联的文档任务 ID（对应 doc_task 表的主键）
    private Long taskId;
    //GitLab 用户名
    private String gitlabUsername;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //源文件在仓库中的路径
    private String filePath;
    //源文件所属的 commit hash
    private String commitId;
    //生成的文档文件路径
    private String docFilePath;
    //解析状态（SUCCESS/FAILED）
    private String parseStatus;
    //解析失败时的错误信息
    private String parseErrorMsg;
    //记录创建时间
    private LocalDateTime createTime;
    //记录最后更新时间
    private LocalDateTime updateTime;

    //以下是别名方法：让外部可以用 getProject()/setProject() 来访问 projectName 字段
    //这样在其他地方使用时代码更简洁（project 比 projectName 更直观）
    public String getProject() {
        return projectName;
    }

    public void setProject(String project) {
        this.projectName = project;
    }

    //同理，getBranch()/setBranch() 是 branchName 的别名
    public String getBranch() {
        return branchName;
    }

    public void setBranch(String branch) {
        this.branchName = branch;
    }
}
