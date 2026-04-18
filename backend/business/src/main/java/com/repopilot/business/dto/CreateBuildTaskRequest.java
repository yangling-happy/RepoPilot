package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CreateBuildTaskRequest {

    private String buildTaskId;
    private String deployTaskId;
    private String projectName;
    private String branchName;
    private String commitId;
    private String scriptPath;
    private String artifactPath;
    private String logDirPath;
    private String runStatus;
    private String errorMsg;
    private Integer duration;
}
