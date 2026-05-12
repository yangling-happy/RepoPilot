package com.repopilot.business.dto;

import lombok.Data;


//DTO, Data Transfer Object，用于在 Controller 层接收前端或客户端发来的请求参数

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CloneRepoRequest {

    //GitLab 项目 ID，用于在 GitLab API 中定位要克隆的仓库
    private Long projectId;
    //要克隆的分支名，如果为空则使用配置文件中的默认分支（通常是 main）
    private String branch;
    //WebSocket 终端会话 ID，用于将克隆进度实时推送到前端终端
    private String terminalSessionId;
}