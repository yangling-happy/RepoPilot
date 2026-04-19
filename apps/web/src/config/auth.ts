/**
 * GitLab OAuth 入口：优先读环境变量；未配置时走网关代理的 Spring Security 常见路径。
 * 部署时在 `.env` / 平台注入中设置 `VITE_GITLAB_OAUTH_LOGIN_URL` 为实际授权地址。
 */
export function getGitLabOAuthLoginUrl(): string {
  const configured = import.meta.env.VITE_GITLAB_OAUTH_LOGIN_URL;
  if (typeof configured === "string" && configured.trim().length > 0) {
    return configured.trim();
  }
  return "/api/oauth2/authorization/gitlab";
}
