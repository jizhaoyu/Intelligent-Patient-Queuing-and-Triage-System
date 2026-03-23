# 患者智能排队分诊系统

面向医院门诊/急诊场景的排队与分诊系统，覆盖患者建档、到诊登记、分诊评估、自动入队、诊室叫号、异常治理、患者自助机取号、排队查询，以及 AI 预分诊与辅助分诊能力。

当前仓库的交付重点如下：

- 主 happy path 已打通：`到诊 -> 分诊评估 -> 自动入队 -> 诊室叫号`
- 患者端正式能力是“院内自助机取号 + 排队查询”
- 手工建票仅保留为“异常补录 / 管理员修复”
- AI 已接入患者自助取号与护士分诊，但定位为“辅助建议层”，不可用时会回退到规则结果，不阻断主链路

## 当前入口

### 后台端

- 地址：`http://localhost:5173/login`
- 职责：治理、审计、异常修复、规则维护

### 工作台

- 地址：`http://localhost:5174/login`
- 职责：建档、到诊、分诊、叫号

### 屏显端

- 地址：`http://localhost:5175/login`
- 职责：科室 / 诊室候诊展示

### 患者端（院内自助机）

- 地址：`http://localhost:5176/patient/self-queue`
- 能力：
  - 自助机正式取号：`/patient/self-queue`
  - 排队结果查询：`/patient/queue`

## 核心功能

- 用户认证与权限控制
- 患者档案管理
- 就诊建档与到诊登记
- 分诊评估、AI 辅助建议与自动入队
- 诊室叫号、复呼、过号、完成接诊
- 队列来源审计与事件日志
- “已分诊未入队”异常治理
- 患者院内自助机取号与排队查询
- 科室 / 诊室屏显展示

## 技术栈

### 后端

- JDK 17+，已在 JDK 25 环境验证
- Maven
- Spring Boot 3.3.9
- Spring Security
- MyBatis-Plus
- MySQL
- Redis
- SpringDoc OpenAPI + Knife4j

### 前端

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus

## 项目结构

```text
src/main/java/com/hospital/triage
├─ common
├─ config
├─ exception
├─ modules
│  ├─ auth
│  ├─ patient
│  ├─ visit
│  ├─ triage
│  ├─ queue
│  ├─ dashboard
│  ├─ clinic
│  └─ system
└─ TriageQueueApplication.java

src/main/resources
├─ application.yml
├─ db/schema.sql
├─ db/data.sql
├─ db/triage-rule-seed.sql
└─ scripts/queue/call-next.lua

web/
├─ src/layout
├─ src/router
├─ src/views
├─ src/api
└─ vite.config.ts

docs/
├─ API接口说明.md
├─ AI智能排队分诊规划方案.md
├─ AI智能排队分诊实施清单.md
├─ 单机上线部署清单.md
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+
- MySQL 8.x
- Redis 6.x+

### 2. 初始化数据库

```sql
CREATE DATABASE triage_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

导入基础表结构和演示数据：

```bash
mysql -uroot -p triage_queue < src/main/resources/db/schema.sql
mysql -uroot -p triage_queue < src/main/resources/db/data.sql
```

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
```

按入口分别启动：

```bash
npm --prefix web run dev:admin
npm --prefix web run dev:workstation
npm --prefix web run dev:screen
npm --prefix web run dev:kiosk
```

默认地址：

- 后台端：`http://localhost:5173/login`
- 工作台：`http://localhost:5174/login`
- 屏显端：`http://localhost:5175/login`
- 患者端：`http://localhost:5176/patient/self-queue`

如需统一修改后端代理目标：

```bash
VITE_API_PROXY_TARGET=http://localhost:8080
```

## AI 本地配置

后端会通过 `spring.config.import` 读取可选文件 `src/main/resources/application-ai.yml`。推荐做法是本地创建该文件或直接注入环境变量，不要把真实 Key 提交到仓库。

常用环境变量：

```bash
AI_ENABLED=true
AI_PROVIDER=moonshot
AI_MODEL=kimi-k2.5
MOONSHOT_BASE_URL=https://api.moonshot.cn/v1
MOONSHOT_API_KEY=replace-with-your-own-key
```

如果只想验证主链路，可保持：

```bash
AI_ENABLED=false
```

## 默认演示账号

- 管理员：`admin`
- 分诊护士：`triage.nurse`
- 导诊台：`guide.desk`
- 诊室医生：`doctor.zhang`

默认密码：

- `password`

推荐首页：

- `admin` -> `/admin/dashboard`
- `triage.nurse` -> `/workstation/triage/assessments/new`
- `guide.desk` -> `/workstation/visits/new`
- `doctor.zhang` -> `/workstation/queue-call`

## 典型业务流程

### 工作台主链路

1. 导诊台建档 / 挂号后到诊
2. 分诊护士完成评估，系统生成规则结果与 AI 建议
3. 系统自动生成或刷新 `WAITING` 票据
4. 诊室医生在 `/workstation/queue-call` 叫号、复呼、过号、完成接诊

### 患者端主链路

1. 既有患者在院内自助机输入 `patientNo + 手机号后 4 位`
2. 若已有有效排队，直接返回当前排队结果
3. 若无有效就诊，系统创建本次 visit、自动到诊、生成 AI 预分诊建议并自动入队
4. 患者可通过查询页查看 `WAITING`、`CALLING`、`MISSED`、`COMPLETED`、`CANCELLED` 等状态，以及当前 AI 建议信息

## 测试与校验

### 后端

```bash
mvn test
```

说明：

- 当前测试基线已恢复，`mvn test` 可执行通过
- 个别依赖 Docker / Testcontainers 的集成用例在本地无 Docker 时会自动跳过，不影响本轮验收基线

### 前端

```bash
npm --prefix web run typecheck
npm --prefix web run build
```

## 相关文档

- [docs/API接口说明.md](docs/API接口说明.md)

- [docs/AI智能排队分诊实施清单.md](docs/AI智能排队分诊实施清单.md)
- [docs/单机上线部署清单.md](docs/单机上线部署清单.md)


在线接口文档：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- Knife4j：`http://localhost:8080/doc.html`

## 当前状态

本仓库当前已完成：

- 主链路自动入队与诊室叫号
- 患者端院内自助机正式化
- AI 预分诊、护士辅助分诊与 AI 审计落库
- 队列来源审计字段与事件展示
- “已分诊未入队”异常治理入口
- 文档、接口、页面、测试口径对齐

后续阶段聚焦：

- 异常治理深化与运营兜底
- 队列侧 AI 审计与运营分析增强
- 实时化能力、权限平台化与多院区扩展
