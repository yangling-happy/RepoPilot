package com.repopilot.business.service;

import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocRefreshResult;

import java.util.List;

public interface DocPipelineService {

    DocRefreshResult refresh(String gitlabUsername, String project, String branch, String token);

    void rebuild(String gitlabUsername, String project, String branch, String commitId, String token);

    DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch);

    List<DocQueryItem> query(String gitlabUsername, String project, String branch, String filePath, String commitId);
}
