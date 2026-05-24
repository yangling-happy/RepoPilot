package com.repopilot.terminal.dto;

//终端任务类型枚举
//每种类型对应一个 shell 脚本，由 ScriptRegistry 管理
public enum TerminalTaskType {
    CLONE_REPO,      //克隆 GitLab 仓库到本地
    REFRESH_DOC,     //增量刷新文档（对比远程新 commit）
    SCAN_LOCAL_DOC,  //本地全量扫描并生成文档
    BUILD_PROJECT,   //构建项目
    DEPLOY_PROJECT,  //部署项目
    CUSTOM_DEPLOY,   //用户自定义部署脚本（仓库内 deploy.sh）
    SETUP_SSH_KEY    //一键配置 SSH 密钥
}
