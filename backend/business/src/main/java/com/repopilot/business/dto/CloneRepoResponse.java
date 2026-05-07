package com.repopilot.business.dto;

import lombok.Data;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CloneRepoResponse {

    private Long projectId;
    private String gitlabUsername;
    private String projectPath;
    private String branch;
    private String cloneUrl;
    private String workspacePath;
    private String localPath;
    private String commitId;
}
