# Intelligent Patient Queuing and Triage System

患者智能排队分诊系统，面向医院门诊/急诊场景，覆盖患者建档、到诊登记、规则分诊、排队叫号、候诊看板和三端联动展示。

当前仓库已经包含：

- Spring Boot 3 后端
- Vue 3 + Vite 独立前端
- 管理后台、工作台、大屏三端入口
- 示例数据、接口文档和联调脚本

说明：

- 当前版本的“智能分诊”仍以规则和阈值计算为主，还不是大模型驱动的 AI 分诊。
- AI 升级规划已整理在 [docs/规划.md](docs/规划.md)。

## 核心功能

- 用户认证与权限控制
- 患者档案管理
- 就诊建档与到诊登记
- 分诊评估与规则维护
- 排队取号、叫号、重呼、过号、完成、取消
- 候诊队列查询与事件日志
- 科室看板、诊室屏、三端分层路由

## 技术栈

### 后端

- JDK 17
- Maven
- Spring Boot 3.3.9
- Spring Security
- MyBatis-Plus
- MySQL
- Redis
- SpringDoc OpenAPI
- Knife4j
- Hutool

### 前端

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus
- Axios

## 项目结构

```text
src/main/java/com/hospital/triage
├─ common        # 通用返回、常量、枚举、基础模型
├─ config        # 安全、Redis、OpenAPI、MyBatis 配置
├─ exception     # 全局异常处理
├─ modules
│  ├─ auth       # 登录认证、JWT、权限
│  ├─ patient    # 患者档案
│  ├─ visit      # 就诊与到诊登记
│  ├─ triage     # 分诊评估、分诊规则
│  ├─ queue      # 排队、叫号、事件日志
│  ├─ dashboard  # 科室看板、诊室屏
│  ├─ clinic     # 科室与诊室基础数据
│  └─ system     # 用户、角色、权限基础实体
└─ TriageQueueApplication.java

src/main/resources
├─ application.yml
├─ db/schema.sql
├─ db/data.sql
├─ mapper/
└─ scripts/queue/call-next.lua

web/
├─ src/layout    # login/admin/workstation/screen 布局
├─ src/router    # 三端分层路由与权限守卫
├─ src/stores    # 登录态与权限状态
├─ src/views     # 管理后台 / 工作台 / 大屏页面
├─ src/api       # 前端 API 封装
└─ vite.config.ts

docs/
├─ API接口说明.md
├─ 三端说明.md
└─ 规划.md

tools/
└─ start-three-portals.ps1
```

## 当前三端说明

- `admin`
  管理后台，提供运营总览、患者管理、候诊队列、分诊规则、事件查看等能力。
- `workstation`
  导诊/分诊/医生工作台，提供患者查询、就诊建档、分诊评估、诊室叫号。
- `screen`
  科室候诊屏和诊室叫号屏，面向现场展示，不包含后台操作入口。

## 已接入的主要接口

### 业务主链路

- `/api/auth/login`
- `/api/auth/me`
- `/api/auth/logout`
- `/api/patients`
- `/api/patients/{id}`
- `/api/visits`
- `/api/visits/{id}`
- `/api/visits/{id}/arrive`
- `/api/triage/assessments`
- `/api/triage/assessments/{id}`
- `/api/triage/assessments/{id}/reassess`
- `/api/triage/rules`
- `/api/queues/tickets`
- `/api/queues/tickets/{ticketNo}`
- `/api/queues/tickets/{ticketNo}/rank`
- `/api/queues/tickets/{ticketNo}/recall`
- `/api/queues/tickets/{ticketNo}/missed`
- `/api/queues/tickets/{ticketNo}/complete`
- `/api/queues/tickets/{ticketNo}/cancel`
- `/api/queues/events`

### 看板与展示

- `/api/queues/depts/{deptId}/waiting`
- `/api/dashboard/depts/{deptId}/summary`
- `/api/dashboard/rooms/{roomId}/current`

说明：

- 当前大屏相关接口已放开匿名访问，便于候诊区和诊室展示屏直接读取。

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+
- npm 10+
- MySQL 8.x
- Redis 6.x+

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE triage_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2. 修改后端配置

默认配置文件：

- `src/main/resources/application.yml`

可按需覆盖以下环境变量：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_DB`
- `JWT_SECRET`

### 3. 启动后端

```bash
mvn spring-boot:run
```

或：

```bash
mvn clean package
java -jar target/triage-queue-0.0.1-SNAPSHOT.jar
```

### 4. 启动前端

```bash
npm --prefix web install
npm --prefix web run dev
```

默认入口：

- `http://localhost:5173/login`

如需三端同时启动：

```bash
npm --prefix web run dev:admin
npm --prefix web run dev:workstation
npm --prefix web run dev:screen
```

对应入口：

- 管理后台：`http://localhost:5173/login`
- 工作台：`http://localhost:5174/login`
- 大屏端：`http://localhost:5175/login`

也可以直接运行脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\start-three-portals.ps1
```

如需修改 Vite 代理目标：

```bash
VITE_API_PROXY_TARGET=http://localhost:8080
```

## 数据初始化

数据库初始化脚本位于：

- `src/main/resources/db/schema.sql`
- `src/main/resources/db/data.sql`

用于初始化：

- 患者/就诊/分诊/排队相关表
- 科室与诊室基础数据
- 角色、权限、用户示例数据
- 演示患者、分诊规则、队列样例数据

## 默认演示账号

- 管理员：`admin`
- 分诊护士：`triage.nurse`
- 医生：`doctor.zhang`
- 导诊台：`guide.desk`

默认演示密码：

- `password`

推荐验证入口：

- `admin` -> `/admin/dashboard`
- `triage.nurse` -> `/workstation/triage/assessments/new`
- `guide.desk` -> `/workstation/visits/new`
- `doctor.zhang` -> `/workstation/queue-call`

## 典型业务流程

1. 用户登录
2. 新增患者档案
3. 创建就诊记录
4. 患者到诊登记
5. 分诊评估
6. 生成排队票据
7. 医生叫号 / 重呼 / 过号 / 完成
8. 科室大屏与诊室屏展示实时状态

## 验证命令

### 后端

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=QueueDispatchServiceImplTest,TriageAssessmentServiceImplTest" test
```

说明：

- 完整 `mvn test` 依赖 Docker/Testcontainers。

### 前端

```bash
npm --prefix web run typecheck
npm --prefix web run build
```

## 接口文档

- [docs/API接口说明.md](docs/API接口说明.md)
- [docs/三端说明.md](docs/三端说明.md)
- [docs/规划.md](docs/规划.md)

运行后也可查看在线文档：

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Knife4j: `http://localhost:8080/doc.html`

## 当前状态与后续方向

当前仓库已经完成：

- 后端主链路接口
- 三端独立前端工程
- 候诊队列与诊室叫号页面
- 队列事件日志接口与管理页
- 科室/诊室大屏联动展示

可继续扩展方向：

- 真正的大模型 AI 分诊建议
- WebSocket/SSE 实时推送
- 更细粒度的规则维护与运营报表
- 多院区/多楼层支持
- HIS/LIS/EMR 对接
- 脱敏审计与患者侧自助服务

## 说明

本项目适合作为医院排队分诊场景的课程设计、毕业设计、原型系统和业务扩展基础。
