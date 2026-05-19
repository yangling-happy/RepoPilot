package com.repopilot.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeployTriggerResponse {

    private String deployTaskId;
    private String status;
    private String terminalSessionId;
    private String commitId;
}
