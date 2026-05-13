package com.repopilot.business.service.gitlab.model;

import lombok.AllArgsConstructor;
import lombok.Data;

//表示一次 commit diff 中的单个文件级变更
//
//DocPipelineServiceImpl 不直接处理 GitLab 原始 diff JSON，
//而是先把它转成这个小对象，这样后面的文档生成逻辑只需要关心：
//  - 旧路径 oldPath
//  - 新路径 newPath
//  - 变更类型 changeType
//Lombok 注解，自动生成 getter/setter 等方法
@Data
//Lombok 注解，生成包含所有字段的构造函数
@AllArgsConstructor
public class CommitFileChange {

    //变更前的文件路径（重命名时有用，其他情况与 newPath 相同）
    private String oldPath;
    //变更后的文件路径
    private String newPath;
    //变更类型
    private ChangeType changeType;

    //文件变更类型枚举
    public enum ChangeType {
        ADDED,      //新增文件
        MODIFIED,   //修改已有文件
        RENAMED,    //重命名文件
        DELETED     //删除文件
    }
}
