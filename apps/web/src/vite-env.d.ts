/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 公开仓库或组织页 URL，由部署环境配置；未设置时 GitHub 按钮使用 `#` */
  readonly VITE_GITHUB_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
