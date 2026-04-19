# RepoPilot

## 项目简介

RepoPilot 旨在解决企业研发场景下的文档滞后与发布链路不透明问题。核心提供两项能力：

1. **代码注释自动转文档**：基于 GitLab 提交增量，自动解析 JavaDoc 并生成结构化在线文档，实现“提交即文档更新”。
2. **一键部署打包**：提供可追踪、可回放的一键部署能力，通过 WebSocket 实时展示构建日志，解决发布过程黑盒问题。

## 技术栈

- **前端**：React 18, TypeScript, Vite, Ant Design, Xterm.js
- **后端**：Java Spring Boot 3, Maven, Pty4J (PTY终端), MySQL
- **包管理**：pnpm workspace (管理前端与中台), Maven (管理后端)

## 前置要求

- Node.js >= 18.0.0
- pnpm >= 8.0.0
- JDK 17+
- Maven 3.6+

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/yangling-happy/RepoPilot.git
cd RepoPilot
```

### 2. 安装依赖

在项目根目录执行：

```bash
pnpm install
```

### 3. 构建共享类型包

前端依赖的共享类型定义需要先构建：

```bash
pnpm --filter @repo-pilot/shared-types build
```

### 4. 启动服务

#### 启动前端

```bash
pnpm dev:frontend
```

#### 启动后端

> 说明：后端当前为多模块 Maven 工程（`backend/common`、`backend/business`、`backend/terminal`、`backend/gateway`）。
> 根脚本基于 `bash + ./mvnw`，请在仓库根目录执行（Git Bash / WSL 终端）。

```bash
# 默认启动 business（8080）
pnpm dev:backend

# 仅做后端依赖预热（安装父 POM + common）
pnpm dev:backend:prepare

# 按服务启动
pnpm dev:backend:business
pnpm dev:backend:terminal
pnpm dev:backend:gateway
```

如果你当前在 `backend/business` 目录里，先回到根目录再执行：

```bash
cd /f/RepoPilot
pnpm dev:backend
```

### 5. 访问地址

- **前端界面**：http://localhost:3000
- **后端接口**：http://localhost:8080

## 常用命令

| 命令                        | 描述                                |
| :-------------------------- | :---------------------------------- |
| `pnpm dev`                  | 启动前端与后端（后端默认 business） |
| `pnpm dev:frontend`         | 仅启动前端开发服务器                |
| `pnpm dev:backend`          | 一行启动后端 business（8080）       |
| `pnpm dev:backend:prepare`  | 预热后端依赖（安装父 POM + common） |
| `pnpm dev:backend:business` | 启动 business（8080）               |
| `pnpm dev:backend:terminal` | 启动 terminal（8081）               |
| `pnpm dev:backend:gateway`  | 启动 gateway（9000）                |
| `pnpm build`                | 构建所有模块                        |
| `pnpm lint`                 | 执行 ESLint 检查                    |
| `pnpm format`               | 格式化代码                          |

## 项目结构

```text
RepoPilot/
├── apps/
│   ├── web/            # React 前端应用
│   └── terminal/       # Xterm 终端中台库
├── packages/
│   └── shared-types/   # 前后端共享 TypeScript 类型定义
├── backend/            # Java Spring Boot 后端
│   ├── gateway/        # 网关服务
│   ├── business/       # 业务逻辑服务
│   ├── terminal/       # 终端 WebSocket 服务
│   └── common/         # 公共组件与工具
├── docs/               # 项目文档
└── pnpm-workspace.yaml # pnpm 工作区配置
```

## 核心功能说明

### 代码注释转文档

- **触发机制**：支持 GitLab Webhook 触发或手动重建。
- **处理逻辑**：后端拉取增量 Java 文件，解析 JavaDoc，存入 MySQL。
- **去重策略**：基于 `project + branch + filePath + commitId` 唯一索引，避免重复解析。

### 一键部署打包

- **实时日志**：通过 WebSocket 将后端 PTY 执行的 Shell 脚本输出实时推送到前端 Xterm 终端。
- **任务管控**：支持任务状态追踪（RUNNING/SUCCESS/FAILED），具备超时自动终止与防重复部署机制。
