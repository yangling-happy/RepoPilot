package com.repopilot.business.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "deploy")
public class DeployProperties {

    private Integer timeoutMinutes = 10;
}
