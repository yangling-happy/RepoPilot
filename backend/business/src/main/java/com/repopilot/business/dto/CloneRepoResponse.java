package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CloneRepoResponse {

    private Long projectId;
    private String projectPath;
    private String branch;
    private String cloneUrl;
    private String localPath;
    private String commitId;
}