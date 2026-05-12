package com.repopilot.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
//指定实体对应的数据库表名
@TableName("deploy_task")
public class DeployTask {

    //TableId标记主键字段，并指定主键策略
    //IdType: MyBatis-Plus 主键生成策略枚举，AUTO表示数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;

    //发起部署的 GitLab 用户名
    private String gitlabUsername;
    //部署任务的业务唯一标识（由调用方生成）
    private String deployTaskId;
    //项目名称
    private String projectName;
    //分支名称
    private String branchName;
    //触发部署的 commit hash
    private String commitId;
    //部署参数（JSON 格式的环境变量或配置项）
    private String deployParams;
    //部署状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED/TIMEOUT
    private String runStatus;
    //日志目录路径
    private String logDirPath;
    //部署结果文件路径
    private String resultPath;
    //部署失败时的错误信息
    private String errorMsg;
    //部署开始时间
    private LocalDateTime startTime;
    //部署耗时（秒）
    private Integer duration;
    //记录创建时间（数据库自动填充）
    private LocalDateTime createTime;
    //记录最后更新时间（数据库自动填充）
    private LocalDateTime updateTime;
}
