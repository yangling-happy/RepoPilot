package com.repopilot.business.dto;

import lombok.Data;


//DTO, Data Transfer Object，用于在 Controller 层接收前端或客户端发来的请求参数

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CloneRepoRequest {

    private Long projectId;
    private String branch;
    private String terminalSessionId;
}