# RepoPilot Backend

Spring Boot 后端服务，包含文档生成和部署功能。

## 项目结构

```
backend/
├── common/                  # 公共模块（DTO、异常处理、工具类）
├── business/                # 业务服务（文档生成、部署管理）
├── terminal/                # 终端服务（WebSocket + PTY4J）
└── gateway/                 # 网关服务（可选，Spring Cloud Gateway）
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
- MySQL 8.0+（仅 business 服务必需）

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

2. 确认 `business/src/main/resources/application.yml` 使用以下占位符配置（不要提交真实账号密码）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:repopilot}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
```

3. 在本机注入环境变量（使用各自账号密码）：

```bash
# Linux / macOS / WSL（当前终端生效）
export DB_USER=your_db_user
export DB_PASSWORD=your_db_password

# 可选：写入 ~/.bashrc 持久生效
echo 'export DB_USER=your_db_user' >> ~/.bashrc
echo 'export DB_PASSWORD=your_db_password' >> ~/.bashrc
```

```powershell
# Windows PowerShell（新开终端后生效）
setx DB_USER "your_db_user"
setx DB_PASSWORD "your_db_password"
```

4. 团队协作约定：
- `application.yml` 仅保留占位符，不提交真实账号密码。
- 如需临时切换数据库账号，只改本机环境变量，不改仓库文件。

### 运行服务

说明：
- 启动 business 前需先完成数据库配置
- terminal 与 gateway 可独立启动，无需数据库

```bash
# 运行业务服务
cd business
../mvnw spring-boot:run

# 运行终端服务
cd terminal
../mvnw spring-boot:run

# 运行网关服务（可选）
cd gateway
../mvnw spring-boot:run
```

## 服务端口

- business: 8080
- terminal: 8081
- gateway: 9000

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
