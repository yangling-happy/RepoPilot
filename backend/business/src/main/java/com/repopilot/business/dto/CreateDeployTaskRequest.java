package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CreateDeployTaskRequest {

    private String deployTaskId;
    private String projectName;
    private String branchName;
    private String commitId;
    private String deployParams;
    private String runStatus;
    private String logDirPath;
    private String resultPath;
    private String errorMsg;
    private Integer duration;
}
