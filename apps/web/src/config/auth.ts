/**
 * GitLab OAuth 登录入口：由后端 /api/oauth/login 生成授权 URL。
 * 前端不再需要拼接 OAuth URL，直接跳转到后端接口即可。
 */
export const OAUTH_LOGIN_URL = "/api/oauth/login";
export const OAUTH_ME_URL = "/api/oauth/me";
export const OAUTH_LOGOUT_URL = "/api/oauth/logout";
