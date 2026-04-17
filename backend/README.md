# RepoPilot Backend

Spring Boot 后端服务，包含文档生成和部署功能。

## 项目结构

```
backend/
├── common-service/          # 公共模块（DTO、异常处理、工具类）
├── business-service/        # 业务服务（文档生成、部署管理）
├── terminal-service/        # 终端服务（WebSocket + PTY4J）
└── gateway-service/         # 网关服务（可选，Spring Cloud Gateway）
```

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring Cloud Gateway
- MyBatis Plus
- MySQL
- GitLab4J API
- PTY4J
- WebSocket

## 快速开始

### 前置要求

- JDK 17+
- MySQL 8.0+

说明：仓库已包含 Maven Wrapper（`mvnw` / `mvnw.cmd`），可不预装 Maven。

### 构建项目

```bash
# Linux / macOS
./mvnw clean install

# Windows PowerShell
.\mvnw.cmd clean install
```

### 配置数据库

1. 创建数据库：
```sql
CREATE DATABASE repopilot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改 `business-service/src/main/resources/application.yml` 中的数据库配置

### 运行服务

```bash
# 运行业务服务
cd business-service
../mvnw spring-boot:run

# 运行终端服务
cd terminal-service
../mvnw spring-boot:run

# 运行网关服务（可选）
cd gateway-service
../mvnw spring-boot:run
```

## 服务端口

- business-service: 8080
- terminal-service: 8081
- gateway-service: 9000

## API 文档

### 会话管理
- `POST /api/session/setGitlabToken` - 设置 GitLab Token

### 文档管理
- `POST /api/doc/webhook/gitlab` - GitLab Webhook
- `POST /api/doc/rebuild` - 重新构建文档
- `GET /api/doc/query` - 查询文档

### 部署管理
- `POST /api/deploy/trigger` - 触发部署
- `GET /api/deploy/task` - 查询部署任务
- `GET /api/deploy/log` - 查询部署日志
- `POST /api/deploy/cancel` - 取消部署

## 数据库表结构

### doc_task - 文档任务表（用于日志）
- id: 主键
- event_id: 事件ID
- project: 项目名
- branch: 分支名
- commit_id: 提交ID
- status: 状态
- create_time：创建时间
- duration: 执行时长

### doc_file_dtl - 文档明细表
- id: 主键
- task_id: 关联文档任务ID
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- file_path: Java文件路径
- doc_file_path: 文档解析结果文件路径
- parse_status: 解析状态
- parse_error_msg: 解析失败信息
- create_time: 创建时间
- update_time: 更新时间

### deploy_task - 部署任务表
- id: 主键
- deploy_task_id: 部署任务ID
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- deploy_params: 部署参数
- run_status: 运行状态
- log_dir_path: 日志目录路径
- result_path: 部署结果路径
- error_msg: 部署失败信息
- start_time: 开始时间
- duration: 执行时长

### build_task - 构建任务表
- id: 主键
- build_task_id: 构建任务ID
- deploy_task_id: 关联部署任务ID
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- script_path: 执行构建脚本路径
- artifact_path: 构建产物路径
- log_dir_path: 构建日志目录路径
- run_status: 运行状态
- error_msg: 构建失败信息
- start_time: 开始时间
- duration: 执行时长
