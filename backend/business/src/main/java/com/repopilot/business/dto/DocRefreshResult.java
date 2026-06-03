package com.repopilot.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 返回给前端的 refresh 汇总结果。
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocRefreshResult {

    //GitLab 用户名
    private String gitlabUsername;
    //项目名称
    private String project;
    //分支名称
    private String branch;
    //上次刷新时记录的 commit（增量刷新的起点）
    private String baselineCommit;
    //本次刷新时分支的最新 commit
    private String headCommit;
    //本次检测到的新 commit 数量
    private Integer newCommitCount = 0;
    //本次检测到的所有新 commit ID 列表
    private List<String> detectedCommitIds = new ArrayList<>();
    //成功创建文档任务的 commit ID 列表
    private List<String> createdTaskCommitIds = new ArrayList<>();
    //跳过的 commit ID 列表（如不包含 Java 文件变更的 commit）
    private List<String> skippedCommitIds = new ArrayList<>();
    //创建任务失败的 commit ID 列表
    private List<String> failedTaskCommitIds = new ArrayList<>();
    //汇总提示信息
    private String message;
}
