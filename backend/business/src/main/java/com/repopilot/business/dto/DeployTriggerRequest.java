package com.repopilot.business.dto;

import lombok.Data;

@Data
public class DeployTriggerRequest {

    private String project;
    private String branch;
    private String environment;
    private String commitId;
    private String terminalSessionId;
    private Boolean build;
    private String artifactPath;
    private String deployHost;
    private Integer deployPort;
    private String deployUser;
    private String deployTargetDir;
}
