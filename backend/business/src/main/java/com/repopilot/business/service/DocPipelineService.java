package com.repopilot.business.service;

import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocRefreshResult;

import java.util.List;

// 手动触发、基于 commit 的文档流水线服务契约。
public interface DocPipelineService {

    // 检测新 commit 并执行增量提取任务。
    DocRefreshResult refresh(String project, String branch, String token);

    // 对指定 commit 强制执行提取。
    void rebuild(String project, String branch, String commitId, String token);

    // 扫描已经克隆到本地的仓库并全量生成文档。
    DocLocalScanResult scanLocal(String project, String branch);

    // 查询最新快照或指定 commit 的文档记录。
    List<DocQueryItem> query(String project, String branch, String filePath, String commitId);
}
