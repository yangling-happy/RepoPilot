package com.repopilot.business.dto;

import lombok.Data;

@Data
public class CreateDocFileRequest {

    private Long taskId;
    private String projectName;
    private String branchName;
    private String filePath;
    private String commitId;
    private String docFilePath;
    private String parseStatus;
    private String parseErrorMsg;
}
