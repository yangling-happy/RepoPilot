package com.repopilot.business.dto;

import lombok.Data;

// 手动 refresh 的请求参数。
@Data
public class DocRefreshRequest {

    private String project;
    private String branch;
}
