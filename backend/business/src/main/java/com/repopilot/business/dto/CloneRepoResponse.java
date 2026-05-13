package com.repopilot.business.dto;

import lombok.Data;

//仓库克隆接口返回给前端的结果 DTO
//它把“远程 GitLab 项目信息”和“本地工作空间路径”放在一起返回，
//方便前端知道这次克隆到了哪里、对应哪个 commit。
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CloneRepoResponse {

    //GitLab 项目 ID
    private Long projectId;
    //当前克隆操作对应的 GitLab 用户名
    private String gitlabUsername;
    //项目在 GitLab 中的路径（如 group/project-name）
    private String projectPath;
    //实际克隆的分支名
    private String branch;
    //Git 克隆 URL
    private String cloneUrl;
    //用户工作空间根目录路径
    private String workspacePath;
    //仓库克隆到本地的绝对路径
    private String localPath;
    //克隆完成后 HEAD 指向的 commit hash
    private String commitId;
}
