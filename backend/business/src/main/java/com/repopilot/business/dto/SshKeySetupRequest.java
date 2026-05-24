package com.repopilot.business.dto;

import lombok.Data;

@Data
public class SshKeySetupRequest {

    private String host;
    private Integer port;
    private String user;
    private String password;
}
