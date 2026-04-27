package com.repopilot.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "user.workspace")
public class UserWorkspaceProperties {

    private String baseDir = ".";
}
