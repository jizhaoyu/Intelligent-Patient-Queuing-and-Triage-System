# Intelligent Patient Queuing and Triage System

患者智能排队分诊系统，基于 Spring Boot 3 + MyBatis-Plus + Redis 构建，面向医院门诊/急诊场景，提供患者建档、到诊登记、智能分诊、排队叫号和看板统计等能力。

## 项目简介

本项目围绕“患者从建档到就诊完成”的完整链路设计，核心目标是：

- 提升分诊效率
- 优化候诊秩序
- 支持高优先级患者优先处理
- 为诊室与管理端提供实时队列看板

系统当前采用模块化单体结构，便于业务拆分与后续扩展。

## 核心功能

- 用户认证与权限控制
- 患者档案管理
- 到诊登记
- 智能分诊评估
- 分诊规则维护
- 排队取号与候诊排序
- 诊室叫号、重呼、过号、完成、取消
- 科室/诊室看板统计

## 技术栈

- JDK 17
- Maven
- Spring Boot 3.3.9
- Spring Security
- MyBatis-Plus
- MySQL
- Redis
- Lombok
- SpringDoc OpenAPI
- Knife4j
- Hutool

## 项目结构

```text
src/main/java/com/hospital/triage
├─ common        # 通用返回、枚举、异常等
├─ modules
│  ├─ auth       # 登录认证、权限
│  ├─ patient    # 患者档案
│  ├─ visit      # 到诊登记
│  ├─ triage     # 分诊评估、规则
│  ├─ queue      # 排队与叫号
│  ├─ dashboard  # 看板统计
│  └─ clinic     # 科室与诊室基础数据
└─ TriageQueueApplication.java
```

## 接口文档

已提供简明接口说明文档：

- [docs/API接口说明.md](docs/API接口说明.md)

运行后也可查看在线文档：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- Knife4j：`http://localhost:8080/doc.html`

## 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.x
- Redis 6.x 或以上

## 本地运行

### 1. 创建数据库

创建数据库：

```sql
CREATE DATABASE triage_queue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2. 修改配置

默认配置位于：

- `src/main/resources/application.yml`

当前默认配置：

- 服务端口：`8080`
- MySQL：`localhost:3306/triage_queue`
- Redis：`localhost:6379`
- 数据库密码默认占位值：`please-change-me`

如本地环境不同，请先修改数据库、Redis、JWT 等配置。

也可以通过环境变量覆盖：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_DB`
- `JWT_SECRET`

### 3. 启动项目

```bash
mvn spring-boot:run
```

或先打包再运行：

```bash
mvn clean package
java -jar target/triage-queue-0.0.1-SNAPSHOT.jar
```

## 数据初始化

项目启动时会自动执行：

- `src/main/resources/db/schema.sql`
- `src/main/resources/db/data.sql`

用于初始化：

- 患者就诊相关表
- 科室与诊室基础数据
- 分诊规则
- 用户、角色、权限示例数据

## 默认演示账号

初始化数据中包含以下演示账号：

- 管理员：`admin`
- 分诊护士：`triage.nurse`
- 医生：`doctor.zhang`
- 导诊台：`guide.desk`

密码为初始化脚本中的预置值，可按本地需要自行调整。

## 典型业务流程

1. 用户登录
2. 新增患者档案
3. 创建到诊记录
4. 执行分诊评估
5. 生成排队票据
6. 诊室叫号
7. 完成就诊
8. 看板查看实时状态

## 统一返回格式

接口统一返回 `Result<T>`：

```json
{
  "success": true,
  "code": "00000",
  "message": "success",
  "data": {}
}
```

## 后续可扩展方向

- 更细粒度的分诊规则引擎
- WebSocket 实时叫号推送
- 多院区/多楼层支持
- 数据报表与运营分析
- 审计日志与链路追踪增强

## 说明

本项目适合作为医院智能分诊排队场景的后端示例工程，也适合课程设计、毕业设计、原型系统和业务扩展基础。
