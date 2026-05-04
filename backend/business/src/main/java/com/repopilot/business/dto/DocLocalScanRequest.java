package com.repopilot.business.dto;

import lombok.Data;

@Data
public class DocLocalScanRequest {

    private String project;
    private String branch;
    private String terminalSessionId;
}
