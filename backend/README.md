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

3. 执行建表脚本：
```bash
# Linux / macOS
mysql -u root -p repopilot < business-service/src/main/resources/scripts/01_init_tables.sql

# Windows PowerShell
Get-Content .\business-service\src\main\resources\scripts\01_init_tables.sql | mysql -u root -p repopilot
```

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

当前版本为 4 张核心表：`doc_task`、`doc_file_dtl`、`deploy_task`、`build_task`。

### doc_task - 文档任务表（用于日志）
- id: 主键
- event_id: 事件ID
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- status: 状态
- duration: 执行时长
- create_time
- 关键约束:
	- `uk_doc_task_event_id`：event_id 去重
	- `idx_doc_task_project_branch_commit`：按 project_name/branch_name/commit_id 查询加速

### doc_file_dtl - 文档明细表
- id: 主键
- project_name: 项目名
- branch_name: 分支名
- file_path: 文件路径
- commit_id: 提交ID
- doc_json: JSON格式文档
- 关键约束:
	- `uk_project_branch_file_commit`：project_name + branch_name + file_path + commit_id 唯一
	- 内部使用 file_path 的 SHA-256 生成列避免 MySQL 组合唯一索引长度超限

### deploy_task - 部署任务表
- id: 主键
- task_id: 任务ID
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- script_name: 脚本名称
- args: 参数
- run_status: 状态
- operator: 操作人
- start_time: 开始时间
- end_time: 结束时间
- 关键约束:
	- `uk_deploy_task_task_id`：task_id 唯一
	- `uk_deploy_task_running_commit`：同一 project_name + branch_name + commit_id 仅允许一个 RUNNING 任务
	- `idx_project_status_time`：按 project_name/run_status/update_time 查询加速

### build_task - 构建任务表
- id: 主键
- build_id: 构建任务ID
- deploy_task_id: 关联部署任务ID（可空）
- project_name: 项目名
- branch_name: 分支名
- commit_id: 提交ID
- build_type: 构建类型（例如 PACKAGE、IMAGE）
- build_tool: 构建工具（例如 MAVEN、DOCKER）
- run_status: 构建状态
- artifact_url: 构建产物地址
- start_time: 开始时间
- end_time: 结束时间
- 关键约束:
	- `uk_build_task_build_id`：build_id 唯一
	- `idx_build_task_project_status_time`：按 project_name/run_status/update_time 查询加速
	- 外键 `fk_build_task_deploy_task_id`：关联 deploy_task.task_id
