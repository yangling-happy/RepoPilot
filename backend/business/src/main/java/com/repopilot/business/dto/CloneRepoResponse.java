package com.repopilot.business.dto;

import lombok.Data;

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
