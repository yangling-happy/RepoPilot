package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.repopilot.business.dto.GitLabProjectInfo;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabUserClient {

    private final GitLabHttpClient gitLabHttpClient;

    public List<GitLabProjectInfo> getUserProjects(String token, int page, int perPage) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }

        String pathAndQuery = "/projects?membership=true&order_by=last_activity_at&sort=desc"
                + "&page=" + page + "&per_page=" + perPage;

        JsonNode projectsArray = gitLabHttpClient.getJson(token, pathAndQuery);
        List<GitLabProjectInfo> projects = new ArrayList<>();

        if (projectsArray.isArray()) {
            for (JsonNode projectNode : projectsArray) {
                GitLabProjectInfo info = new GitLabProjectInfo();
                info.setId(projectNode.path("id").asLong());
                info.setName(projectNode.path("name").asText(""));
                info.setPathWithNamespace(projectNode.path("path_with_namespace").asText(""));
                info.setDefaultBranch(projectNode.path("default_branch").asText("main"));
                info.setVisibility(projectNode.path("visibility").asText("private"));
                info.setDescription(projectNode.path("description").asText(""));
                info.setHttpUrlToRepo(projectNode.path("http_url_to_repo").asText(""));
                info.setSshUrlToRepo(projectNode.path("ssh_url_to_repo").asText(""));
                info.setLastActivityAt(projectNode.path("last_activity_at").asText(""));

                JsonNode ownerNode = projectNode.path("owner");
                if (ownerNode != null && !ownerNode.isMissingNode()) {
                    info.setOwnerUsername(ownerNode.path("username").asText(""));
                }

                projects.add(info);
            }
        }
        return projects;
    }
}
