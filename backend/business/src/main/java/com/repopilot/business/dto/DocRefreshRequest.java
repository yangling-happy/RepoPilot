package com.repopilot.business.dto;

import lombok.Data;

// 手动 refresh 的请求参数。
//Lombok 注解，自动生成 getter/setter、toString()、equals()、hashCode() 等方法，减少样板代码
@Data
public class DocRefreshRequest {

    private String project;
    private String branch;
}
