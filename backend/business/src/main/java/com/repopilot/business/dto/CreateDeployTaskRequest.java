package com.repopilot.business.dto;

import lombok.Data;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CreateDeployTaskRequest {

    //部署任务的唯一标识符
    private String deployTaskId;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //触发部署的 commit hash
    private String commitId;
    //部署参数（如环境变量、配置项等），可为空
    private String deployParams;
    //部署状态，如 PENDING、RUNNING、SUCCESS、FAILED 等
    private String runStatus;
    //部署日志存放目录
    private String logDirPath;
    //部署结果文件路径
    private String resultPath;
    //部署失败时的错误信息
    private String errorMsg;
    //部署耗时（秒）
    private Integer duration;
}
