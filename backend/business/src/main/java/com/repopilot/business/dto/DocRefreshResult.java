package com.repopilot.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 返回给前端的 refresh 汇总结果。
@Data
public class DocRefreshResult {

    private String gitlabUsername;
    private String project;
    private String branch;
    private String baselineCommit;
    private String headCommit;
    private Integer newCommitCount = 0;
    private List<String> detectedCommitIds = new ArrayList<>();
    private List<String> createdTaskCommitIds = new ArrayList<>();
    private List<String> skippedCommitIds = new ArrayList<>();
    private List<String> failedTaskCommitIds = new ArrayList<>();
    private String message;
}
