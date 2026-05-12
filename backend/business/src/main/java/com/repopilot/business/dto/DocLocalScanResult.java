package com.repopilot.business.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 本地仓库全量扫描文档生成结果。
// Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocLocalScanResult {

    //当前操作的 GitLab 用户名
    private String gitlabUsername;
    //项目名称
    private String project;
    //分支名称
    private String branch;
    //扫描时的 commit hash
    private String commitId;
    //本地仓库的绝对路径
    private String localRepoPath;
    //扫描到的总文件数
    private Integer scannedFileCount = 0;
    //成功生成文档的文件数
    private Integer generatedFileCount = 0;
    //跳过的文件数（如不支持的文件类型、被 .gitignore 忽略等）
    private Integer skippedFileCount = 0;
    //生成失败的文件数
    private Integer failedFileCount = 0;
    //成功生成文档的文件路径列表
    private List<String> generatedFilePaths = new ArrayList<>();
    //生成失败的文件路径列表
    private List<String> failedFilePaths = new ArrayList<>();
    //汇总提示信息
    private String message;
}
