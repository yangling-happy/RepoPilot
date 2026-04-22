/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 公开仓库或组织页 URL，由部署环境配置；未设置时 GitHub 按钮使用 `#` */
  readonly VITE_GITHUB_URL?: string;
  /** GitLab OAuth 授权入口（完整 URL 或同源路径，如 `/api/oauth2/authorization/gitlab`） */
  readonly VITE_GITLAB_OAUTH_LOGIN_URL?: string;
  /** 终端 WebSocket 基础地址（如 `ws://localhost:8081/ws/terminal`） */
  readonly VITE_TERMINAL_WS_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
