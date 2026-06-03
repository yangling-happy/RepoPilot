package com.repopilot.business.dto;

import lombok.Data;

/**
 * GitLab项目信息DTO
 */
@Data
public class GitLabProjectInfo {
    /** 项目ID */
    private Long id;
    /** 项目名称 */
    private String name;
    /** 项目完整路径（group/project） */
    private String pathWithNamespace;
    /** 默认分支 */
    private String defaultBranch;
    /** 可见性：private/internal/public */
    private String visibility;
    /** 项目描述 */
    private String description;
    /** HTTP克隆地址 */
    private String httpUrlToRepo;
    /** SSH克隆地址 */
    private String sshUrlToRepo;
    /** 最后活动时间 */
    private String lastActivityAt;
    /** 项目所有者用户名 */
    private String ownerUsername;
}
