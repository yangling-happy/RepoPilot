package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CreateDocTaskRequest {

    private String eventId;
    private String project;
    private String branch;
    private String commitId;
    private String status;
    private Integer duration;
}
