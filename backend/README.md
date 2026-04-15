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

- Java 21
- Spring Boot 3.2.0
- Spring Cloud Gateway
- MyBatis Plus
- MySQL
- GitLab4J API
- PTY4J
- WebSocket

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+

### 安装依赖

```bash
cd backend
./mvnw clean install
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

### doc_task - 文档任务表
- id: 主键
- event_id: 事件ID
- project: 项目名
- branch: 分支名
- commit_id: 提交ID
- status: 状态
- duration: 执行时长

### doc_file - 文档文件表
- id: 主键
- project: 项目名
- branch: 分支名
- file_path: 文件路径
- commit_id: 提交ID
- doc_json: JSON格式文档
- doc_markdown: Markdown格式文档

### deploy_task - 部署任务表
- id: 主键
- task_id: 任务ID
- project: 项目名
- branch: 分支名
- commit_id: 提交ID
- script_name: 脚本名称
- args: 参数
- status: 状态
- operator: 操作人
- start_time: 开始时间
- end_time: 结束时间
