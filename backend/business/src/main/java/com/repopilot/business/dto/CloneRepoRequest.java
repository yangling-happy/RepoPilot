package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CloneRepoRequest {

    private Long projectId;
    private String branch;
    private String terminalSessionId;
}