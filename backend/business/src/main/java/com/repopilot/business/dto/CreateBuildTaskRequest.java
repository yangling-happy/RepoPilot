package com.repopilot.business.dto;

import lombok.Data;

//手动创建构建任务时的请求体 DTO
//对应 DeployController 的 /deploy/build/task/create 接口
//
//构建任务通常描述“把某个 commit 构建成产物”的过程：
//  1. 记录构建脚本、日志目录、产物路径
//  2. 记录运行状态和耗时
//  3. 可选关联到一个部署任务 deployTaskId
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CreateBuildTaskRequest {

    //构建任务的唯一标识符
    private String buildTaskId;
    //关联的部署任务 ID，表示这次构建是为哪个部署任务服务的
    private String deployTaskId;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //触发构建的 commit hash
    private String commitId;
    //构建脚本在仓库中的路径（如 scripts/build.sh）
    private String scriptPath;
    //构建产物输出路径
    private String artifactPath;
    //构建日志存放目录
    private String logDirPath;
    //构建状态，如 PENDING、RUNNING、SUCCESS、FAILED 等
    private String runStatus;
    //构建失败时的错误信息
    private String errorMsg;
    //构建耗时（秒）
    private Integer duration;
}
