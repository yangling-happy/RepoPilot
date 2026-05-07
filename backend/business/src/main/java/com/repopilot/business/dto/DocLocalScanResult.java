package com.repopilot.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 本地仓库全量扫描文档生成结果。
// Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocLocalScanResult {

    private String gitlabUsername;
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
