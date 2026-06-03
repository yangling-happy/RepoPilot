package com.repopilot.business.dto;

import lombok.Data;

//手动创建文档任务时的请求体 DTO
//对应 DocController 的 /doc/task/create 接口，用来把前端传来的 JSON 参数接成 Java 对象
//
//注意：这只是“请求参数对象”，不是数据库实体。
//Controller 会先校验这些字段，再把它们复制到 DocTask 实体中写入 doc_task 表。
//
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CreateDocTaskRequest {

    //外部事件 ID，用来把一次文档任务和触发它的业务事件关联起来
    //例如：GitLab webhook 事件、本地扫描事件、手动重建事件等
    private String eventId;
    //GitLab 项目 ID 或项目标识，当前业务里通常传数字项目 ID 的字符串
    private String project;
    //目标分支名，如 main、dev，后续会和 commitId 一起定位代码版本
    private String branch;
    //文档任务对应的 commit hash，表示本次文档是基于哪个代码版本生成的
    private String commitId;
    //任务状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED
    //Controller 会统一转成大写并校验是否在允许列表里
    private String status;
    //任务耗时，单位秒；可以为空，表示当前还没有统计耗时
    private Integer duration;
}
