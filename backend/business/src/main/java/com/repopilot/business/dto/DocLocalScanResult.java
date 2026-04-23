package com.repopilot.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 本地仓库全量扫描文档生成结果。
@Data
public class DocLocalScanResult {

    private String project;
    private String branch;
    private String commitId;
    private String localRepoPath;
    private Integer scannedFileCount = 0;
    private Integer generatedFileCount = 0;
    private Integer skippedFileCount = 0;
    private Integer failedFileCount = 0;
    private List<String> generatedFilePaths = new ArrayList<>();
    private List<String> failedFilePaths = new ArrayList<>();
    private String message;
}
