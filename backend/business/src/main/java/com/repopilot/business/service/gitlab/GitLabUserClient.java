package com.repopilot.business.service.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.business.dto.GitLabProjectInfo;
import com.repopilot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

//Spring 注解，将这个类注册为 Spring Bean
@Slf4j
@Component
//Lombok 注解，为 final 字段生成构造函数
@RequiredArgsConstructor
public class GitLabUserClient {

    //JSON 解析工具
    private final ObjectMapper objectMapper;
    //Java 11+ 内置的 HTTP 客户端，用于调用 GitLab REST API
    private final HttpClient httpClient = HttpClient.newHttpClient();

    //从 application.yml 配置文件中读取 GitLab API 地址
    //冒号后面是默认值，如果配置文件没写就用这个默认地址
    @Value("${gitlab.api-url:https://gitlab.com/api/v4}")
    private String gitlabApiUrl;

    //通过 GitLab Token 获取当前用户的用户名
    //调用的是 GitLab 的 /user 接口，该接口返回当前 Token 对应的用户信息
    public String getCurrentUsername(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }

        //OAuth Access Token 通过 Authorization: Bearer header 传递
        //同时兼容 Personal Access Token（GitLab 两种头都接受 PAT，但 OAuth token 只接受 Bearer）
        //这里不把 token 放到 URL 里，避免它出现在访问日志或浏览器历史中
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/user"))
                .header("Authorization", "Bearer " + token.trim())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new BusinessException(401, "GitLab token is invalid or lacks permission to read current user");
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException(500, "GitLab API failed to read current user, status=" + status);
            }

            JsonNode json = objectMapper.readTree(response.body());
            //GitLab /user 的 username 是登录名，不是展示昵称 name
            //后续用它来隔离本地工作空间目录和数据库记录
            String username = json.path("username").asText(null);
            if (!StringUtils.hasText(username)) {
                throw new BusinessException(500, "GitLab username is empty");
            }
            return username.trim();
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read current user from GitLab");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while reading current user from GitLab");
        }
    }

    /**
     * 获取当前用户的GitLab项目列表
     * @param token GitLab访问令牌
     * @param page 页码（从1开始）
     * @param perPage 每页数量
     * @return 项目列表
     */
    public List<GitLabProjectInfo> getUserProjects(String token, int page, int perPage) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(400, "GitLab token is required");
        }

        String url = apiBase() + "/projects?membership=true&order_by=last_activity_at&sort=desc"
                + "&page=" + page + "&per_page=" + perPage;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token.trim())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                log.error("GitLab API returned status={}, body={}", status, response.body());
                throw new BusinessException(401, "GitLab token is invalid or lacks permission");
            }
            if (status < 200 || status >= 300) {
                log.error("GitLab API returned status={}, body={}", status, response.body());
                throw new BusinessException(500, "GitLab API failed to get projects, status=" + status);
            }

            JsonNode projectsArray = objectMapper.readTree(response.body());
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

                    // 提取所有者信息
                    JsonNode ownerNode = projectNode.path("owner");
                    if (ownerNode != null && !ownerNode.isMissingNode()) {
                        info.setOwnerUsername(ownerNode.path("username").asText(""));
                    }

                    projects.add(info);
                }
            }

            return projects;
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to get projects from GitLab");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "Interrupted while getting projects from GitLab");
        }
    }

    private String apiBase() {
        if (!StringUtils.hasText(gitlabApiUrl)) {
            throw new BusinessException(500, "GitLab API URL is not configured");
        }
        //统一去掉结尾斜杠，避免拼接 /user 时出现双斜杠
        String trimmed = gitlabApiUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
