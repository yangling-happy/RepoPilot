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

> [!Warning]
>
> 这里要注意，需要用Wsl下的mysql建表，避免后续出现数据库在Windows下的mysql，而环境在Wsl下的情况
>
> 我的做法是：Windows的mysql修改端口到3307，Wsl下保持3306，这样登录某个数据库可视化软件，连接3306端口看到的就是WSL下的数据库

1. 创建数据库：
```sql
CREATE DATABASE repopilot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行建表脚本（脚本仅包含建表语句）：

```bash
# Linux / macOS / WSL
mysql -h127.0.0.1 -P3306 -uroot -p repopilot < business/src/main/resources/scripts/01_init_tables.sql
```

3. 确认 `business/src/main/resources/application.yml` 使用以下占位符配置（不要提交真实账号密码）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:repopilot}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:}
```

4. 在本机注入环境变量（使用各自账号密码）：

```bash
# Linux / macOS / WSL（当前终端生效）
export DB_USER=your_db_user #这里要改成你的账户  比如root
export DB_PASSWORD=your_db_password  ##这里要改成账户对应的密码

# 可选：写入 ~/.bashrc 持久生效
echo 'export DB_USER=your_db_user' >> ~/.bashrc
echo 'export DB_PASSWORD=your_db_password' >> ~/.bashrc
```



5. 团队协作约定：
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
- `POST /api/doc/task/create` - 写入文档任务（doc_task）
- `POST /api/doc/file/create` - 写入文档明细（doc_file_dtl）

### 部署管理
- `POST /api/deploy/trigger` - 触发部署
- `POST /api/deploy/task/create` - 写入部署任务（deploy_task）
- `POST /api/deploy/build/task/create` - 写入构建任务（build_task）
- `GET /api/deploy/task` - 查询部署任务
- `GET /api/deploy/log` - 查询部署日志
- `POST /api/deploy/cancel` - 取消部署

## 写入接口结构速览

团队协作时，推荐按统一链路理解接口写入流程：

1. Controller 接收请求 DTO（结构体）
2. 参数校验（必填、状态枚举、时长非负）
3. DTO 映射到 Entity
4. Mapper `insert` 写入数据库
5. 返回 `ApiResponse`

当前已实现的写入接口与数据结构：

### 1) 文档任务写入
- 接口：`POST /api/doc/task/create`
- 对应表：`doc_task`
- 请求体字段：
  - `eventId` `project` `branch` `commitId` `status` `duration`

### 2) 文档明细写入
- 接口：`POST /api/doc/file/create`
- 对应表：`doc_file_dtl`
- 请求体字段：
  - `taskId` `projectName` `branchName` `filePath` `commitId` `docFilePath` `parseStatus` `parseErrorMsg`

### 3) 部署任务写入
- 接口：`POST /api/deploy/task/create`
- 对应表：`deploy_task`
- 请求体字段：
  - `deployTaskId` `projectName` `branchName` `commitId` `deployParams` `runStatus` `logDirPath` `resultPath` `errorMsg` `duration`

### 4) 构建任务写入
- 接口：`POST /api/deploy/build/task/create`
- 对应表：`build_task`
- 请求体字段：
  - `buildTaskId` `deployTaskId` `projectName` `branchName` `commitId` `scriptPath` `artifactPath` `logDirPath` `runStatus` `errorMsg` `duration`

状态字段约束：
- `doc_task.status`：`PENDING | RUNNING | SUCCESS | FAILED | SKIPPED`
- `doc_file_dtl.parse_status`：`PENDING | SUCCESS | FAILED`
- `deploy_task.run_status`、`build_task.run_status`：`PENDING | RUNNING | SUCCESS | FAILED | CANCELLED | TIMEOUT`

## 写入验证样例

以下示例用于快速验证新增写入接口（Windows PowerShell）：

```powershell
$ts = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$deployTaskId = "dep-$ts"
$buildTaskId = "bld-$ts"
$deployCommitId = "commit-$ts"
$docCommitId = "doc-$ts"
$docFilePath = "src/main/java/com/repopilot/demo/Sample$ts.java"

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/deploy/task/create" -ContentType "application/json" -Body (@{
  deployTaskId = $deployTaskId
  projectName = "RepoPilot"
  branchName = "main"
  commitId = $deployCommitId
  deployParams = "--profile=test"
  runStatus = "SUCCESS"
  logDirPath = "logs/deploy/$deployTaskId"
  resultPath = "output/$deployTaskId.json"
  duration = 42
} | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/deploy/build/task/create" -ContentType "application/json" -Body (@{
  buildTaskId = $buildTaskId
  deployTaskId = $deployTaskId
  projectName = "RepoPilot"
  branchName = "main"
  commitId = $deployCommitId
  scriptPath = "scripts/build.sh"
  artifactPath = "dist/app.jar"
  logDirPath = "logs/build/$buildTaskId"
  runStatus = "SUCCESS"
  duration = 21
} | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/doc/file/create" -ContentType "application/json" -Body (@{
  projectName = "RepoPilot"
  branchName = "main"
  filePath = $docFilePath
  commitId = $docCommitId
  docFilePath = "docs/Sample$ts.md"
  parseStatus = "SUCCESS"
} | ConvertTo-Json)
```

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
