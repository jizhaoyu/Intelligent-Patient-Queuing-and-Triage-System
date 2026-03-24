# 患者智能排队分诊系统 API 接口说明

## 1. 基本说明

- 接口前缀：`/api`
- 返回结构：统一使用 `Result<T>`
- 认证方式：除患者查询/自助取号等公开接口外，其余接口默认需要登录
- 推荐请求头：`Authorization: Bearer <token>`
- 数据格式：`application/json`

统一返回示例：

```json
{
  "success": true,
  "code": "00000",
  "message": "success",
  "data": {}
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `success` | 是否成功 |
| `code` | 业务状态码 |
| `message` | 提示信息 |
| `data` | 业务数据 |

### 1.1 本轮关键口径

- `POST /api/patient-queue/enroll` 已正式定义为“院内自助机取号 / 建档一体入口”。
- 患者端不引入 PATIENT 角色或 JWT，公开接口继续使用“身份标识 + 手机号后 4 位”完成校验；已有患者支持 `patientNo` 或 `patientName`，新患者支持现场建档。
- `POST /api/queues/tickets` 继续保留，但产品定位统一为“异常补录 / 管理员修复入口”。
- 队列与事件返回增加来源审计字段：
  - `sourceType`
  - `sourceRemark`
  - `lastAdjustReason`（仅票据相关返回）

### 1.2 队列来源枚举

| 值 | 含义 |
| --- | --- |
| `TRIAGE_AUTO` | 分诊自动入队 |
| `KIOSK` | 院内自助机正式取号 |
| `MANUAL_REPAIR` | 异常补录 / 管理员修复 |

---

## 2. 认证接口

### 2.1 `POST /api/auth/login`

说明：用户登录并获取访问令牌。  
权限：公开接口

请求示例：

```json
{
  "username": "admin",
  "password": "123456"
}
```

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `token` | 访问令牌 |
| `tokenType` | 令牌类型，通常为 `Bearer` |
| `expireSeconds` | 过期秒数 |
| `profile` | 当前登录用户信息 |

### 2.2 `POST /api/auth/logout`

说明：退出登录。  
权限：已登录

### 2.3 `GET /api/auth/me`

说明：获取当前登录用户信息与权限。  
权限：已登录

返回 `profile` 主要字段：

- `userId`
- `username`
- `nickname`
- `roleCode`
- `deptId`
- `roomId`
- `permissions`

---

## 3. 患者与就诊接口

### 3.1 `POST /api/patients`

说明：创建患者档案。  
权限：`patient:manage`

请求主要字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientName` | String | 是 | 患者姓名 |
| `gender` | String | 否 | 性别 |
| `birthDate` | LocalDate | 否 | 出生日期 |
| `phone` | String | 否 | 手机号 |
| `idCard` | String | 否 | 身份证号 |
| `allergyHistory` | String | 否 | 过敏史 |
| `specialTags` | String | 否 | 特殊标签 |

### 3.2 `GET /api/patients/{id}`

说明：查询患者详情。  
权限：`patient:manage` 或 `queue:call`

### 3.3 `GET /api/patients`

说明：按关键字查询患者列表。  
权限：`patient:manage`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `keyword` | 姓名、患者编号、手机号等模糊搜索关键字 |

### 3.4 `PUT /api/patients/{id}`

说明：更新患者档案。  
权限：`patient:manage`

### 3.5 `POST /api/visits`

说明：创建本次就诊记录。  
权限：`visit:manage`

请求主要字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientId` | Long | 是 | 患者 ID |
| `chiefComplaint` | String | 是 | 主诉 |

### 3.6 `GET /api/visits/{id}`

说明：查询就诊详情。  
权限：`visit:manage`

### 3.7 `POST /api/visits/{id}/arrive`

说明：患者到诊。  
权限：`visit:manage`

---

## 4. 分诊接口

### 4.1 `POST /api/triage/assessments`

说明：创建分诊评估。  
权限：`triage:assess`

请求字段沿用现有评估模型，包括生命体征、年龄、性别、特殊人群标签、人工调分和评估人等。

返回 `data` 关键字段：

| 字段 | 说明 |
| --- | --- |
| `triageLevel` | 分诊等级 |
| `recommendDeptId` | 推荐科室 ID |
| `recommendDeptName` | 推荐科室名称 |
| `priorityScore` | 优先分 |
| `queueCreated` | 是否已自动入队 |
| `queueTicketNo` | 自动入队生成的票号 |
| `queueStatus` | 自动入队后的队列状态 |
| `queueDeptId` | 入队科室 ID |
| `queueDeptName` | 入队科室名称 |
| `queueRoomId` | 入队诊室 ID |
| `queueRoomName` | 入队诊室名称 |

说明：

- 首次分诊和重评估都会尝试走自动入队链路。
- 推荐科室为空时，自动入队失败并返回明确错误。

### 4.2 `GET /api/triage/assessments/{id}`

说明：查询评估详情。  
权限：`triage:assess`

### 4.3 `POST /api/triage/assessments/{id}/reassess`

说明：基于原评估重新分诊。  
权限：`triage:assess`

---

## 5. 患者公开接口

### 5.1 `POST /api/patient-queue/query`

说明：患者查询当前排队进度。  
权限：公开接口  
请求时必须在 `patientNo` 与 `patientName` 中二选一，并配合 `phoneSuffix`

请求示例：

```json
{
  "patientNo": "P1234567890",
  "phoneSuffix": "1234"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientNo` | String | 否 | 患者编号，与 `patientName` 二选一 |
| `patientName` | String | 否 | 患者姓名，与 `patientNo` 二选一 |
| `phoneSuffix` | String | 是 | 手机号后 4 位 |

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `patientName` | 脱敏后的患者姓名 |
| `patientNo` | 患者编号 |
| `patientId` | 患者 ID |
| `visitId` / `visitNo` | 当前就诊信息 |
| `visitStatus` / `visitStatusText` | 当前就诊状态 |
| `queueStatus` / `queueStatusText` | 当前排队状态；无票据时为 `NONE` |
| `queueMessage` | 当前状态提示文案 |
| `ticketNo` | 当前票号 |
| `deptId` / `deptName` | 当前科室 |
| `roomId` / `roomName` | 当前诊室 |
| `doctorName` | 当前诊室医生 |
| `rank` | 当前排位 |
| `waitingCount` | 前方人数 |
| `estimatedWaitMinutes` | 预计等待分钟数 |
| `waitedMinutes` | 已等待分钟数 |
| `triageLevel` | 分诊等级 |
| `enqueueTime` / `callTime` / `completeTime` | 排队关键时间点 |
| `hasActiveQueue` | 是否存在有效排队 |
| `nextStep` | 患者下一步指引卡片数据 |

`nextStep` 结构：

| 字段 | 说明 |
| --- | --- |
| `stage` | 当前阶段标识，可能取自 `visitStatus` 或 `queueStatus` |
| `title` | 患者一眼可读的主标题 |
| `action` | 当前建议动作 |
| `locationHint` | 建议前往或停留的位置提示 |
| `urgency` | 紧急程度：`LOW` / `NORMAL` / `HIGH` / `IMMEDIATE` |

补充说明：

- 当已分诊但尚未生成票据时，接口仍返回成功，并通过 `queueStatus = NONE` 与 `queueMessage` 提示当前情况。
- 当患者编号或手机号后 4 位校验失败时，返回统一模糊提示，不泄露患者是否存在。
- 患者姓名查询适用于院内人工辅助核验；对外仍不返回患者是否存在的明确信号。
- `nextStep` 会根据当前就诊状态、排队状态、排位和预计等待时间，直接生成“现在该做什么”的指引文案。

### 5.2 `POST /api/patient-queue/enroll`

说明：院内自助机正式取号接口。  
权限：公开接口  
定位：仅面向院内自助机，不是移动端患者账号入口；同一接口同时支持“已有患者取号”和“新患者建档后取号”

已有患者请求示例：

```json
{
  "patientMode": "EXISTING",
  "patientName": "张三",
  "phoneSuffix": "1234",
  "deptId": 101,
  "chiefComplaint": "发热 2 天，咳嗽"
}
```

新患者请求示例：

```json
{
  "patientMode": "NEW",
  "patientName": "李四",
  "phone": "13800001234",
  "gender": "FEMALE",
  "birthDate": "1995-05-20",
  "deptId": 101,
  "chiefComplaint": "腹痛 3 小时"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientMode` | String | 否 | 办理方式，`EXISTING` 或 `NEW`，默认 `EXISTING` |
| `patientNo` | String | 条件必填 | 既有患者模式下可与 `patientName` 二选一 |
| `patientName` | String | 条件必填 | 既有患者模式下可与 `patientNo` 二选一；新患者模式下必填 |
| `phoneSuffix` | String | 条件必填 | 既有患者模式下必填；新患者模式下可由 `phone` 自动提取 |
| `phone` | String | 条件必填 | 新患者模式下必填，11 位手机号 |
| `gender` | String | 条件必填 | 新患者模式下必填，推荐使用 `MALE` / `FEMALE` |
| `birthDate` | LocalDate | 条件必填 | 新患者模式下必填 |
| `idCard` | String | 否 | 新患者可选补充 |
| `allergyHistory` | String | 否 | 新患者可选补充 |
| `specialTags` | String | 否 | 新患者可选补充 |
| `deptId` | Long | 是 | 本次就诊科室 |
| `chiefComplaint` | String | 否 | 主诉 |

行为说明：

- `patientMode = EXISTING` 时，必须提供 `patientNo` 或 `patientName` 之一，并配合 `phoneSuffix` 完成校验。
- `patientMode = NEW` 时，会先创建或复用同名同手机号患者档案，再继续后续取号流程。
- 若无法识别患者或校验失败，统一返回“请前往导诊台处理”。
- 若已存在有效排队，直接返回当前排队视图，不重复建票。
- 若无当前有效就诊，可自动创建本次 visit、自动到诊，再生成自助机评估并自动入队。
- 返回结构与 `POST /api/patient-queue/query` 一致。
- 自助取号成功后，返回的 `nextStep` 可直接用于页面顶部“下一步卡”展示，无需额外请求。

---

## 6. 排队接口

### 6.1 `POST /api/queues/tickets`

说明：创建排队票据。  
权限：`queue:manage`  
产品定位：异常补录 / 管理员修复入口，不属于主 happy path

请求示例：

```json
{
  "visitId": 1,
  "assessmentId": 10,
  "roomId": 101
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `visitId` | Long | 是 | 就诊 ID |
| `assessmentId` | Long | 是 | 分诊评估 ID |
| `roomId` | Long | 否 | 指定诊室 ID |

### 6.2 `GET /api/queues/tickets/{ticketNo}`

说明：查询票据详情。  
权限：`queue:manage` 或 `queue:call`

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `ticketNo` | 票号 |
| `visitId` | 就诊 ID |
| `patientId` / `patientNo` / `patientName` | 患者信息 |
| `assessmentId` | 评估 ID |
| `deptId` / `deptName` | 科室信息 |
| `roomId` / `roomName` | 诊室信息 |
| `doctorName` | 诊室医生 |
| `triageLevel` | 分诊等级 |
| `priorityScore` | 优先分 |
| `status` | 队列状态 |
| `recallCount` | 复呼次数 |
| `fastTrack` | 是否快速通道 |
| `sourceType` | 来源类型 |
| `sourceRemark` | 来源说明 |
| `lastAdjustReason` | 最近一次调整原因 |
| `waitingCount` | 候诊人数 |
| `rank` | 当前排位 |
| `estimatedWaitMinutes` | 预计等待分钟数 |
| `waitedMinutes` | 已等待分钟数 |
| `enqueueTime` / `callTime` / `completeTime` | 关键时间点 |

### 6.3 `GET /api/queues/waiting`

说明：查询当前候诊摘要。  
权限：公开接口  
查询参数：`deptId`（可选，`<= 0` 视为不限制）

### 6.4 `GET /api/queues/active`

说明：查询当前活跃票据列表（`WAITING` + `CALLING`）。  
权限：登录后可用  
查询参数：`deptId`（可选）

### 6.5 `GET /api/queues/depts/{deptId}/waiting`

说明：查询指定科室候诊摘要。  
权限：公开接口

### 6.6 `POST /api/queues/rooms/{roomId}/call-next`

说明：诊室叫下一位。  
权限：`queue:call`

### 6.7 `POST /api/queues/tickets/{ticketNo}/recall`

说明：复呼。  
权限：`queue:call`

### 6.8 `POST /api/queues/tickets/{ticketNo}/missed`

说明：标记过号。  
权限：`queue:call`

### 6.9 `POST /api/queues/tickets/{ticketNo}/complete`

说明：完成就诊。  
权限：`queue:call`

### 6.10 `POST /api/queues/tickets/{ticketNo}/cancel`

说明：取消排队。  
权限：`queue:manage`

### 6.11 `GET /api/queues/tickets/{ticketNo}/rank`

说明：查询票据排位与预计等待时间。  
权限：`queue:manage` 或 `dashboard:view`

返回字段：

| 字段 | 说明 |
| --- | --- |
| `ticketNo` | 票号 |
| `status` | 当前状态 |
| `rank` | 当前排位 |
| `waitingCount` | 候诊人数 |
| `estimatedWaitMinutes` | 预计等待分钟数 |

### 6.12 `GET /api/queues/events`

说明：查询排队事件日志。  
权限：`queue:manage`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `ticketNo` | 按票号过滤 |
| `eventType` | 按事件类型过滤 |

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `id` | 日志 ID |
| `ticketNo` | 票号 |
| `eventType` | 事件类型 |
| `fromStatus` / `toStatus` | 状态流转 |
| `visitId` / `patientId` / `deptId` / `roomId` | 关联业务标识 |
| `operatorName` | 操作人 |
| `sourceType` | 来源类型 |
| `sourceRemark` | 来源说明 |
| `remark` | 备注 |
| `createdTime` | 创建时间 |

### 6.13 `GET /api/queues/exceptions/unqueued-triaged`

说明：查询“已分诊未入队”异常列表。  
权限：`queue:manage`

查询参数：

| 参数 | 说明 |
| --- | --- |
| `deptId` | 科室过滤，可选 |

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `visitId` / `visitNo` | 就诊信息 |
| `patientId` / `patientNo` / `patientName` | 患者信息 |
| `chiefComplaint` | 主诉 |
| `triageLevel` | 分诊等级 |
| `assessmentId` | 评估 ID |
| `assessedTime` | 分诊时间 |
| `deptId` / `deptName` | 当前科室 |
| `recommendDeptId` / `recommendDeptName` | 推荐科室 |
| `reason` | 异常说明 |

---

## 7. 看板接口

### 7.1 `GET /api/dashboard/summary`

说明：查询后台运营看板摘要，可选按科室过滤。  
权限：公开接口  
查询参数：`deptId`

### 7.2 `GET /api/dashboard/depts/{deptId}/summary`

说明：查询指定科室看板摘要。  
权限：公开接口

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `deptId` | 科室 ID |
| `waitingCount` | 候诊人数 |
| `callingCount` | 叫号中人数 |
| `completedCount` | 已完成人数 |
| `averageWaitMinutes` | 平均等待时长 |
| `timeoutHighPriorityCount` | 高优先级超时人数 |
| `unqueuedTriagedCount` | 已分诊未入队异常数量 |

### 7.3 `GET /api/dashboard/rooms/{roomId}/current`

说明：查询指定诊室当前叫号信息。  
权限：公开接口

---

## 8. 门诊辅助接口

### 8.1 `GET /api/clinic/depts/options`

说明：查询可挂号/可分诊科室下拉选项。  
权限：公开接口

返回 `data` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `deptId` | 科室 ID |
| `deptCode` | 科室编码 |
| `deptName` | 科室名称 |
| `enabled` | 是否启用 |

### 8.2 `GET /api/clinic/depts/rooms/{roomId}`

说明：根据诊室 ID 反查所属科室 ID。  
权限：登录后可用

路径参数：

| 参数 | 说明 |
| --- | --- |
| `roomId` | 诊室 ID |

返回 `data`：`Long`，即所属 `deptId`

---

## 9. 推荐联调顺序

当前版本推荐按以下顺序联调：

1. 登录：`POST /api/auth/login`
2. 新建患者：`POST /api/patients`
3. 新建就诊并到诊：`POST /api/visits` -> `POST /api/visits/{id}/arrive`
4. 创建分诊评估：`POST /api/triage/assessments`
5. 读取自动入队结果：从分诊返回中获取 `queueTicketNo / queueStatus`
6. 查询票据详情与排位：`GET /api/queues/tickets/{ticketNo}`、`GET /api/queues/tickets/{ticketNo}/rank`
7. 诊室叫号与状态流转：`call-next / recall / missed / complete / cancel`
8. 查询事件日志与异常治理：`GET /api/queues/events`、`GET /api/queues/exceptions/unqueued-triaged`
9. 患者侧验证：
   - 查询：`POST /api/patient-queue/query`
   - 院内自助机取号：`POST /api/patient-queue/enroll`

说明：

- 主 happy path 已改为“分诊自动入队”，联调时不再把 `POST /api/queues/tickets` 当作正常流程的一部分。
- `POST /api/queues/tickets` 只应在异常治理或管理员修复场景下使用。
