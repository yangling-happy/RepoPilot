package com.repopilot.business.dto;

import lombok.Data;

@Data
public class GitLabProjectInfoResponse {

    private Long id;
    private String gitlabUsername;
    private String pathWithNamespace;
    private String httpUrlToRepo;
    private String defaultBranch;
    private String workspacePath;
    private String localPath;
}
