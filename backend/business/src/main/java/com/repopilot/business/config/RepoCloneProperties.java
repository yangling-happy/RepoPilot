package com.repopilot.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "repo.clone")
public class RepoCloneProperties {

    private String defaultBranch = "main";
    private Integer timeoutSeconds = 120;
}
