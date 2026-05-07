package com.repopilot.business.dto;

import lombok.Data;

//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class CreateDocTaskRequest {

    private String eventId;
    private String project;
    private String branch;
    private String commitId;
    private String status;
    private Integer duration;
}
