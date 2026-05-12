package com.repopilot.business.dto;

import lombok.Data;

//DTO, Data Transfer Object，用于在 Controller 层接收前端发来的本地扫描请求参数
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocLocalScanRequest {

    //项目名称
    private String project;
    //分支名称
    private String branch;
    //WebSocket 终端会话 ID，用于将扫描进度实时推送到前端
    private String terminalSessionId;
}
