package com.repopilot.business.service;

import com.repopilot.business.dto.DocLocalScanResult;
import com.repopilot.business.dto.DocQueryItem;
import com.repopilot.business.dto.DocRefreshResult;

import java.util.List;

//文档流水线服务的接口定义（Service 层的抽象）
//接口的作用：定义"做什么"，而实现类（DocPipelineServiceImpl）负责"怎么做"
//这种接口-实现分离的设计是 Spring 推荐的做法，便于测试和替换实现
public interface DocPipelineService {

    //增量刷新：对比远程分支的最新 commit 和本地记录的 baseline，
    //找出新增的 commit，为每个包含 Java 文件变更的 commit 创建文档任务
    DocRefreshResult refresh(String gitlabUsername, String project, String branch, String token);

    //重建：为指定的 commit 重新生成文档（覆盖已有的文档产物）
    void rebuild(String gitlabUsername, String project, String branch, String commitId, String token);

    //本地全量扫描（不带终端输出）：扫描本地仓库中所有 Java 文件并生成文档
    DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch);

    //本地全量扫描（带终端输出）：同上，但会将扫描进度推送到 WebSocket 终端
    DocLocalScanResult scanLocal(String gitlabUsername, String project, String branch, String terminalSessionId);

    //查询文档：根据项目/分支/文件路径/commit 等条件查询已生成的文档记录
    List<DocQueryItem> query(String gitlabUsername, String project, String branch, String filePath, String commitId);
}
