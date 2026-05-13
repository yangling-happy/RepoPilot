/**
 * GitLab 集成相关组件所在的包。
 *
 * 这个包里的类主要做三件事：
 * 1. 读取/校验 GitLab Token 对应的用户信息
 * 2. 调用 GitLab REST API 获取项目、commit、文件内容
 * 3. 使用 JGit 把远程仓库克隆到本地工作空间
 */
package com.repopilot.business.service.gitlab;
