package com.repopilot.business.service;

import com.repopilot.business.dto.DeployTriggerRequest;
import com.repopilot.business.dto.DeployTriggerResponse;
import com.repopilot.business.entity.DeployTask;
import com.repopilot.business.service.gitlab.GitLabUserContext;

public interface DeployPipelineService {

    DeployTriggerResponse trigger(DeployTriggerRequest request, GitLabUserContext context);

    DeployTask getTask(String taskId, GitLabUserContext context);

    DeployTask cancel(String taskId, String terminalSessionId, GitLabUserContext context);
}
