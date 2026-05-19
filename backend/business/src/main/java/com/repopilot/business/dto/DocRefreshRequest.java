package com.repopilot.business.dto;

import lombok.Data;

//手动 refresh 的请求参数
//对应 DocController 的 /doc/refresh 接口
//
//refresh 的含义：对本地仓库执行 git fetch/pull，找出远程分支新增的 commit，
//然后只为发生变更且被支持的文件生成文档，属于“增量刷新”
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocRefreshRequest {

    //GitLab 项目 ID 或项目标识，Service 层会用它定位本地仓库目录
    private String project;
    //要刷新的分支名，如 main、dev；支持传 refs/heads/main，Service 层会做标准化
    private String branch;
    private String terminalSessionId;
}
