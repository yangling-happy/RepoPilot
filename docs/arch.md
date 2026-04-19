# 一、功能介绍

在企业研发与发布场景下，提供“代码注释自动转文档”和“一键部署打包”两项核心能力，解决文档滞后、发布链路不透明、人工操作易出错的问题。

功能价值如下：

- 在多人协作和频繁提交场景下，提供“提交即文档更新”的能力，解决接口说明依赖人工维护导致的失真问题。
- 在多环境发布和跨团队协同场景下，提供“可追踪、可回放、可中断”的一键部署能力，解决发布过程黑盒、回溯困难的问题。

MVP 范围与约束如下：

- 仅实现两个功能的简单版：代码注释转文档、一键部署打包。
- 不做登录界面，采用现有会话机制。
- GitLab 组在后端固定，不允许前端传入组名覆盖。
- GitLab Token 由后端接口写入 Session，后续 GitLab API 调用从 Session 读取。

# 二、架构及核心逻辑

## 2.1 整体技术栈与项目结构

系统采用 React 前端 + Xterm 中台 + Java Spring Boot 后端的混合技术栈架构，通过 pnpm workspace 管理所有 JS/TS 代码（前端+中台），Maven管理 Java 后端，两者在根目录下平行共存。

**技术栈总览**：

- 前端：React + TypeScript + Ant Design + Xterm.js + Vite + pnpm workspace
- 后端：Java Spring Boot + Pty4J + Maven + Docker(待定)

**参考产品**：打包部署功能参考 Vercel 的设计思路——关联 GitLab 仓库的交互模式、Build Log 的实时展示方式、部署流程的视觉反馈。

项目目录结构：

```Bash
RepoPilot/
├── .vscode/                    # VS Code 配置
│   └── settings.json
│
├── apps/                       # 应用层（由 pnpm 管理）
│   ├── web/                    # React 前端应用
│   │   ├── src/
│   │   │   ├── App.tsx
│   │   │   └── main.tsx
│   │   ├── index.html
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   └── vite.config.ts
│   │
│   └── terminal/               # Xterm 中台（独立应用）
│       ├── src/
│       │   ├── index.ts        # 导出 WebSocket 连接类
│       │   ├── xterm-manager.ts
│       │   └── types.ts
│       ├── package.json
│       ├── tsconfig.json
│       └── vite.config.ts
│
├── packages/                   # 共享库（由 pnpm 管理）
│   └── shared-types/           # TypeScript 类型定义（前后端共享）
│       ├── src/
│       │   ├── api.ts          # API 请求/响应类型
│       │   ├── api.js
│       │   └── api.d.ts
│       ├── package.json
│       ├── tsconfig.json
│       └── tsconfig.tsbuildinfo
│
├── backend/                    # Java Spring Boot 后端（Maven管理）
│   ├── .mvn/                   # Maven 包装器
│   │   └── wrapper/
│   │       └── maven-wrapper.properties
│   ├── .gitignore
│   ├── README.md
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── pom.xml                 # Maven 父 POM
│   │
│   ├── gateway/                # Spring Cloud Gateway（可选）
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/
│   │       │   │       └── repopilot/
│   │       │   │           └── gateway/
│   │       │   │               ├── config/
│   │       │   │               ├── filter/
│   │       │   │               └── GatewayServiceApplication.java
│   │       │   └── resources/
│   │       │       └── application.yml
│   │       └── test/
│   │           └── java/
│   │               └── com/
│   │                   └── repopilot/
│   │                       └── gateway/
│   │
│   ├── terminal/               # 终端服务（WebSocket 处理）
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/
│   │       │   │       └── repopilot/
│   │       │   │           └── terminal/
│   │       │   │               ├── config/
│   │       │   │               ├── controller/
│   │       │   │               ├── handler/
│   │       │   │               ├── service/
│   │       │   │               └── TerminalServiceApplication.java
│   │       │   └── resources/
│   │       │       └── application.yml
│   │       └── test/
│   │           └── java/
│   │               └── com/
│   │                   └── repopilot/
│   │                       └── terminal/
│   │
│   ├── business/               # 业务服务
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/
│   │       │   │   └── com/
│   │       │   │       └── repopilot/
│   │       │   │           └── business/
│   │       │   │               ├── controller/
│   │       │   │               ├── dto/
│   │       │   │               ├── entity/
│   │       │   │               ├── mapper/
│   │       │   │               └── BusinessServiceApplication.java
│   │       │   └── resources/
│   │       │       ├── mapper/
│   │       │       ├── scripts/
│   │       │       └── application.yml
│   │       └── test/
│   │           └── java/
│   │               └── com/
│   │                   └── repopilot/
│   │                       └── business/
│   │
│   └── common/                 # 公共服务
│       ├── pom.xml
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   │   └── com/
│           │   │       └── repopilot/
│           │   │           └── common/
│           │   │               ├── config/
│           │   │               ├── constant/
│           │   │               ├── dto/
│           │   │               ├── entity/
│           │   │               ├── enums/
│           │   │               ├── exception/
│           │   │               └── util/
│           │   └── resources/
│           └── test/
│               └── java/
│                   └── com/
│                       └── repopilot/
│                           └── common/
│
├── docs/                       # 项目文档
│   ├── arch.md
│   └── 评审材料.pdf
│
├── .gitignore
├── README.md
├── package.json                # 根 package.json
├── pnpm-lock.yaml
└── pnpm-workspace.yaml         # pnpm workspace 配置
```

**关键配置文件**：

`pnpm-workspace.yaml`：

```Plain
packages:
  - 'apps/'
  - 'packages/'
  # backend 目录不在此列表，由 Maven 独立管理
```

根目录 `package.json`（统一命令入口）：

```JSON
{
  "name": "my-project-monorepo",
  "private": true,
  "scripts": {
    "dev": "pnpm run dev:all",
    "dev:frontend": "pnpm --filter @repo-pilot/web dev",
    "dev:terminal": "pnpm --filter @repo-pilot/terminal-client dev",
    "dev:backend": "cd backend && ./mvnw spring-boot:run",
    "dev:all": "concurrently \"pnpm:dev:frontend\" \"pnpm:dev:terminal\" \"pnpm:dev:backend\"",
    "build": "pnpm run build:all",
    "build:frontend": "pnpm --filter @repo-pilot/web build",
    "build:terminal": "pnpm --filter @repo-pilot/terminal-client build",
    "build:backend": "cd backend && ./mvnw clean package",
    "build:all": "pnpm run build:frontend && pnpm run build:terminal && pnpm run build:backend",
    "test": "pnpm run test:frontend && pnpm run test:backend",
    "test:frontend": "pnpm --filter @repo-pilot/web test",
    "test:backend": "cd backend && ./mvnw test",
    "lint": "eslint apps packages --ext .ts,.tsx",
    "format": "prettier --write \"apps//*.{ts,tsx,css}\" \"packages//*.ts\""
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "concurrently": "^8.0.0",
    "eslint": "^8.0.0",
    "prettier": "^3.0.0",
    "typescript": "^5.0.0"
  },
  "engines": {
    "node": ">=18.0.0",
    "pnpm": ">=8.0.0"
  }
}
```

`backend/pom.xml`（Maven 父 POM）：

```XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.repopilot</groupId>
    <artifactId>repopilot-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
    <modules>
        <module>gateway</module>
        <module>terminal</module>
        <module>business</module>
        <module>common</module>
    </modules>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
        <pty4j.version>0.12.13</pty4j.version>
        <gitlab4j.version>5.5.0</gitlab4j.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
    </properties>
</project>
```

## 2.3 总体架构

系统按照 SpringBoot 框架三层实现：

- **Controller 层**：HTTP、WebSocket
- **Service 层**：GitLab API、JavaDoc 解析器、脚本执行引擎（PTY + Shell）
- **Mapper 层**：MySQL、文件日志过程追踪

## 2.4 前端架构设计

`apps/web`（React 前端应用）：

```JSON
{
  "name": "@repo-pilot/web",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "test": "vitest"
  },
  "dependencies": {
    "react": "18.2.0",
    "react-dom": "18.2.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.0.0",
    "typescript": "^5.0.0",
    "vite": "^4.0.0"
  }
}
```

前端采用 React 18 + TypeScript + Vite 构建。

`apps/terminal`（Xterm 中台）：

```JSON
{
  "name": "@repo-pilot/terminal-client",
  "version": "0.0.1",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "scripts": {
    "dev": "tsc --watch",
    "build": "tsc",
    "test": "echo \"No tests configured\""
  },
  "dependencies": {
    "xterm": "^5.3.0",
    "xterm-addon-fit": "^0.8.0",
    "xterm-addon-web-links": "^0.9.0"
  },
  "devDependencies": {
    "typescript": "^5.0.0",
    "vite": "^4.0.0"
  }
}
```

终端中台封装 xterm.js 及 WebSocket 连接逻辑，作为独立库被前端引用。

`packages/shared-types`（共享类型定义）：

```TypeScript
{
  "name": "@repo-pilot/shared-types",
  "version": "0.0.1",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch"
  },
  "devDependencies": {
    "typescript": "^5.0.0"
  }
}
共享类型示例（api.ts）：
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface DocTask {
  eventId: string;
  project: string;
  branch: string;
  commitId: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  duration: number;
}

export interface DocFile {
  project: string;
  branch: string;
  filePath: string;
  commitId: string;
  docJson: string;
  docMarkdown: string;
  updateTime: Date;
}

export interface DeployTask {
  taskId: string;
  project: string;
  branch: string;
  scriptName: string;
  args: string[];
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  operator: string;
  startTime: Date;
  endTime: Date;
  result?: string;
}
```

**前后端类型同步方案**：通过脚本从 Java 代码生成 TypeScript 类型。

```Markdown
#!/bin/bash
# scripts/generate-api-types.sh
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--openapi.export=/tmp/openapi.json
npx openapi-typescript /tmp/openapi.json -o packages/shared-types/src/generated/api.ts
```

## 2.5 功能 A：代码注释自动转在线文档

**问题**：每次 commit 后，如何把 Java 注释自动转换为结构化在线文档？怎么追溯到 commit？

**技术**：

- GitLab API（拉增量文件）
- JavaDoc 解析（类、方法、param、return）
- MySQL

**逻辑**：

1. GitLab 克隆仓库到达后端
2. 提取 project、branch、commitId，过滤仅处理 Java 文件变更（默认规则：filePath 以 `.java` 结尾）
3. 调用 GitLab API 拉取变更文件内容（仅增量，避免全仓再次克隆）
4. 调用 JavaDoc 解析器
5. 根据结构化的 JSON 提取结构体（类、方法、注释摘要、参数、返回值）
6. 以 project + branch + filePath + commitId 写入数据库
7. 先比对 commitId 与缓存中的 latestCommitId；相同则不刷新，不同才刷新
8. 对外提供查询 API（按项目、分支、文件、commit 查询）

**关键设计**：

- 每条文档记录保留 commitId 与 author，支持比较和回滚
- 解析失败不阻断主流程，落失败状态并告警

**多语言扩展细节**：先看后缀，`.java` 就先走 Javadoc；后续多语言在后端放一个 Map（后缀 → 工具），没有命中的文件写警告信息，不直接报错中断。

**去重与刷新策略**：同一个 eventId 或 commitId 可能被重复推送，后端做去重——查到该 commit 已经处理过，直接返回成功，不再重复解析。缓存刷新策略：先比对 commitId，和缓存里 latestCommitId 一样就不刷，不一样才刷新，避免重复计算。文档入库按 project + branch + filePath + commitId 判断：四个值一样就覆盖更新，不一样就新增一条版本记录。

## 2.6 功能 B：一键部署打包

**问题**：如何让用户在 Web 端一键触发打包/部署，并实时看到执行状态与日志。

**技术**：

- WebSocket（实时日志推送）
- PTY 终端执行（后端执行脚本并回传）
- Shell 脚本链（构建、打包、部署）
- GitLab API（必要时获取分支/提交信息）
- MySQL

**前端终端集成示例**：

```TypeScript
// apps/web/src/hooks/useTerminal.ts
import { TerminalClient } from '@repo-pilot/terminal-client';

export const useTerminal = (sessionId: string) => {
  const ws = new WebSocket(`ws://localhost:8080/ws/terminal/${sessionId}`);
  const client = new TerminalClient(ws);

  client.onMessage((msg: any) => {
    if (msg.type === 'stdout') {
      // 处理终端输出
    }
  });

  return client;
};
```

**逻辑**：

1. 前端调用“触发部署”API，传入项目、分支、环境、部署参数
2. 后端创建 deployTask 记录，状态置为 Running
3. 后端通过 PTY 启动部署脚本（deploy.sh）
4. 执行输出推送到 WebSocket，前端实时展示日志
5. 脚本完成后更新任务状态为 SUCCESS/FAILED，并记录结束时间
6. 如果有产物路径/下载信息，回写 result 字段供后续下载或外传

**关键设计**：

- 状态转换：RUNNING → SUCCESS/FAILED
- **防重复部署**：同一 project + branch + commitId 同时仅允许一个 RUNNING 任务，避免互相覆盖环境
- **超时保护**：每个任务设置超时（如 5 分钟或 10 分钟），超时后自动标记 FAILED，并尝试 kill 执行进程，防止一直占资源

## 2.7 当前版本约束

- GitLab Group 固定为后端常量，不从前端透传
- Session 中保存 gitlabToken，调用 GitLab API 时优先取 Session Token
- 无登录界面

# 三、存储设计

## 3.1 数据库选型与职责

- **MySQL**：任务主数据、文档索引、审计信息

## 3.2 核心表设计

> MVP 先不考虑日志功能，后续还要加上日志相关的表

**核心表**：

- `docTask`：文档任务（eventId、project、branch、commitId、status、duration）
- `docFile`：文档结果（project、branch、filePath、commitId、docJson、docMarkdown、updateTime）
- `deployTask`：部署任务（taskId、project、branch、scriptName、args、status、operator、startTime、endTime）

**索引**：

- `docFileUkProjectBranchFileCommit`：唯一索引（project + branch + filePath + commitId）
- `deployTaskIdxProjectBranchStatusUpdateTime`：普通索引（project + branch + status + updateTime）

## 3.3 大表处理

> MVP 先不考虑，后续的 docFile 和 deployLog 可能成为数据很多的大表

# 四、API 设计

## 4.1 对外 API

**文档流水线**：

- `POST /api/session/setGitlabToken`
- `POST /api/doc/webhook/gitlab`
- `POST /api/doc/rebuild`
- `GET /api/doc/query`

**部署流水线**：

- `POST /api/deploy/trigger`
- `GET /api/deploy/task`
- `GET /api/deploy/log`
- `POST /api/deploy/cancel`

## 4.2 鉴权与数据隔离模型

**MVP 鉴权**：

- 不做登录界面
- 通过 Session 维持 token 与操作上下文
- 接口使用签名密钥校验来源

**数据隔离**：

- 此阶段先在后端固定 GitLab 组

## 4.3 限流与防重

- 触发部署：同一 commitId 同时仅允许一个 RUNNING 任务
- 防重复：eventId/commitId 去重

## 4.4 命令行交互

- 后端统一通过脚本执行器调用 Shell 命令
- 标准化返回：exitCode、stdout/stderr 摘要

# 五、开发工作流与 CI/CD

## 5.1 本地开发初始化

```Markdown
# 1. 克隆项目
git clone <your-repo>

# 2. 安装前端依赖
pnpm install

# 3. 构建共享包
pnpm --filter @repo-pilot/shared-types build

# 4. 安装后端依赖
cd backend && ./mvnw dependency:resolve

# 5. 启动所有服务（一键启动）
pnpm dev
```

## 5.2 CI/CD 配置（GitHub Actions）

```YAML
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'pnpm'
      - run: pnpm install
      - run: pnpm run lint
      - run: pnpm run test:frontend
      - run: pnpm run build:frontend

  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      - run: cd backend && ./mvnw verify
```

## 5.3 Docker 编排

```YAML
# docker/docker-compose.yml
version: '3.8'
services:
  backend:
    build:
      context: ../backend
      dockerfile: ../docker/Dockerfile.backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker

  frontend:
    build:
      context: ..
      dockerfile: docker/Dockerfile.frontend
    ports:
      - "3000:3000"
    depends_on:
      - backend

  terminal:
    build:
      context: ..
      dockerfile: docker/Dockerfile.terminal
    ports:
      - "3001:3001"
```

# 六、监控/报警/应急设计

## 6.1 稳定性风险识别

> ⚠️ 存在稳定性风险

**主要风险**：

- 任务阻塞：脚本卡死导致 RUNNING 长时间不结束
- 解析异常：异常 Java 文件导致文档解析失败
- 外部依赖波动：GitLab API 超时

## 6.2 监控指标

**核心指标**：

- `docPipelineSuccessRate`
- `docParseLatency`
- `deploySuccessRate`
- `deployRunningDuration`
- `gitlabApiErrorRate`

## 6.3 报警策略

- 部署任务 RUNNING 超过 5 分钟报警
- 文档解析超过 5 分钟报警
- GitLab API 连续超时报警

## 6.4 应急预案

- 任务恢复：对失败任务支持按 taskId 重试
- API 异常重试：GitLab API 异常时切到重试队列

# 七、性能评估

## 7.1 查询性能影响与优化

**影响点**：文档查询可能按项目/分支/路径高频访问

**优化策略**：文档主查询命中索引（project + branch + filePath + commitId）

## 7.2 入库性能影响与优化

**影响点**：单次 push 可能包含多个 Java 文件，造成突发写入

**优化策略**：文档入库批量写
