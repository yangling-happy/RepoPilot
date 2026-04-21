package com.repopilot.business.service.gitlab.model;

import lombok.AllArgsConstructor;
import lombok.Data;

// 表示一次 commit diff 中的单个文件级变更。
@Data
@AllArgsConstructor
public class CommitFileChange {

    private String oldPath;
    private String newPath;
    private ChangeType changeType;

    // 提取流水线使用的标准化变更分类。
    public enum ChangeType {
        ADDED,
        MODIFIED,
        RENAMED,
        DELETED
    }
}
