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

#### 方式一：一键启动所有服务（推荐）

同时启动前端、终端中台和后端服务：

```bash
pnpm dev
```

#### 方式二：单独启动

- **仅启动前端**：
  ```bash
  pnpm dev:frontend
  ```
- **仅启动后端**：
  ```bash
  pnpm dev:backend
  ```

### 5. 访问地址

- **前端界面**：http://localhost:3000
- **后端接口**：http://localhost:8080

## 常用命令

| 命令                | 描述                        |
| :------------------ | :-------------------------- |
| `pnpm dev`          | 启动所有服务（前端+后端）   |
| `pnpm dev:frontend` | 仅启动前端开发服务器        |
| `pnpm dev:backend`  | 仅启动后端 Spring Boot 应用 |
| `pnpm build`        | 构建所有模块                |
| `pnpm lint`         | 执行 ESLint 检查            |
| `pnpm format`       | 格式化代码                  |

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
