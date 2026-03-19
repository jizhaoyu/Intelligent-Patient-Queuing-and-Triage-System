# 患者智能排队分诊系统接口说明

本文档根据当前后端控制器整理，适合前端联调、接口浏览和功能梳理。

## 1. 基本说明

- 接口前缀：`/api`
- 返回格式：统一使用 `Result<T>`
- 认证方式：登录后在请求头中携带 Token
- 推荐请求头：`Authorization: Bearer <token>`
- 数据格式：`application/json`

### 统一返回结构

```json
{
  "success": true,
  "code": "00000",
  "message": "success",
  "data": {}
}
```

字段说明：

- `success`：是否成功
- `code`：业务状态码
- `message`：提示信息
- `data`：业务数据

---

## 2. 认证接口

### 2.1 用户登录

- 方法：`POST`
- 路径：`/api/auth/login`
- 说明：用户登录并获取访问令牌
- 权限：无需登录

请求示例：

```json
{
  "username": "admin",
  "password": "123456"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | String | 访问令牌 |
| tokenType | String | 令牌类型，通常为 `Bearer` |
| expireSeconds | Long | 过期秒数 |
| profile | Object | 当前登录用户信息 |

`profile` 主要字段：`userId`、`username`、`nickname`、`roleCode`、`deptId`、`roomId`、`permissions`

---

### 2.2 用户登出

- 方法：`POST`
- 路径：`/api/auth/logout`
- 说明：退出登录
- 权限：已登录

返回：`data` 为空

---

### 2.3 获取当前用户信息

- 方法：`GET`
- 路径：`/api/auth/me`
- 说明：获取当前登录用户资料与权限
- 权限：已登录

返回 `data` 主要字段：

- `userId`：用户ID
- `username`：用户名
- `nickname`：昵称
- `roleCode`：角色编码
- `deptId`：所属科室ID
- `roomId`：所属诊室ID
- `permissions`：权限列表

---

## 3. 患者管理接口

### 3.1 新增患者

- 方法：`POST`
- 路径：`/api/patients`
- 说明：创建患者档案
- 权限：`patient:manage`

请求示例：

```json
{
  "patientName": "张三",
  "gender": "男",
  "birthDate": "1990-01-01",
  "phone": "13800000000",
  "idCard": "110101199001010011",
  "allergyHistory": "青霉素过敏",
  "specialTags": "老人"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientName | String | 是 | 患者姓名 |
| gender | String | 否 | 性别 |
| birthDate | LocalDate | 否 | 出生日期，格式 `yyyy-MM-dd` |
| phone | String | 否 | 手机号 |
| idCard | String | 否 | 身份证号 |
| allergyHistory | String | 否 | 过敏史 |
| specialTags | String | 否 | 特殊标签 |

返回 `data` 主要字段：`id`、`patientNo`、`patientName`、`gender`、`birthDate`、`phone`、`idCard`、`allergyHistory`、`specialTags`、`createdTime`

---

### 3.2 获取患者详情

- 方法：`GET`
- 路径：`/api/patients/{id}`
- 说明：根据患者ID查询详情
- 权限：`patient:manage`

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 患者ID |

---

### 3.3 查询患者列表

- 方法：`GET`
- 路径：`/api/patients`
- 说明：按关键字查询患者列表
- 权限：`patient:manage`

查询参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | String | 否 | 关键字，可用于模糊搜索 |

返回：`data` 为患者列表

---

### 3.4 更新患者信息

- 方法：`PUT`
- 路径：`/api/patients/{id}`
- 说明：修改患者档案
- 权限：`patient:manage`

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 患者ID |

请求体字段与“新增患者”一致。

---

## 4. 到诊管理接口

### 4.1 创建到诊记录

- 方法：`POST`
- 路径：`/api/visits`
- 说明：患者挂号/到诊登记
- 权限：`visit:manage`

请求示例：

```json
{
  "patientId": 1,
  "chiefComplaint": "发热、咳嗽两天"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | Long | 是 | 患者ID |
| chiefComplaint | String | 是 | 主诉 |

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 到诊ID |
| patientId | Long | 患者ID |
| visitNo | String | 到诊单号 |
| status | String | 状态 |
| registerTime | LocalDateTime | 登记时间 |
| arrivalTime | LocalDateTime | 到达时间 |
| chiefComplaint | String | 主诉 |
| currentDeptId | Long | 当前科室ID |
| currentRoomId | Long | 当前诊室ID |

---

### 4.2 查询到诊详情

- 方法：`GET`
- 路径：`/api/visits/{id}`
- 说明：根据到诊ID查询详情
- 权限：`visit:manage`

---

### 4.3 患者到达登记

- 方法：`POST`
- 路径：`/api/visits/{id}/arrive`
- 说明：将到诊记录标记为已到达
- 权限：`visit:manage`

---

## 5. 分诊接口

### 5.1 创建分诊评估

- 方法：`POST`
- 路径：`/api/triage/assessments`
- 说明：为患者进行分诊评估
- 权限：`triage:assess`

请求示例：

```json
{
  "visitId": 1,
  "symptomTags": "发热,咳嗽",
  "bodyTemperature": 38.5,
  "heartRate": 105,
  "bloodPressure": "120/80",
  "bloodOxygen": 97,
  "age": 35,
  "gender": "男",
  "elderly": false,
  "pregnant": false,
  "child": false,
  "disabled": false,
  "revisit": false,
  "manualAdjustScore": 0,
  "assessor": "nurse01"
}
```

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| visitId | Long | 是 | 到诊ID |
| symptomTags | String | 否 | 症状标签，建议逗号分隔 |
| bodyTemperature | BigDecimal | 否 | 体温 |
| heartRate | Integer | 否 | 心率 |
| bloodPressure | String | 否 | 血压 |
| bloodOxygen | Integer | 否 | 血氧 |
| age | Integer | 否 | 年龄 |
| gender | String | 否 | 性别 |
| elderly | Boolean | 否 | 是否老人 |
| pregnant | Boolean | 否 | 是否孕妇 |
| child | Boolean | 否 | 是否儿童 |
| disabled | Boolean | 否 | 是否残障 |
| revisit | Boolean | 否 | 是否复诊 |
| manualAdjustScore | Integer | 否 | 人工调节分值 |
| assessor | String | 否 | 评估人 |

返回 `data` 主要字段：`id`、`visitId`、`symptomTags`、`bodyTemperature`、`heartRate`、`bloodPressure`、`bloodOxygen`、`triageLevel`、`recommendDeptId`、`priorityScore`、`fastTrack`、`manualAdjustScore`、`assessor`、`assessedTime`

---

### 5.2 获取分诊评估详情

- 方法：`GET`
- 路径：`/api/triage/assessments/{id}`
- 说明：查询分诊评估结果
- 权限：`triage:assess`

---

### 5.3 重新评估

- 方法：`POST`
- 路径：`/api/triage/assessments/{id}/reassess`
- 说明：基于原评估记录重新分诊
- 权限：`triage:assess`

说明：请求体与“创建分诊评估”一致。

---

## 6. 分诊规则接口

### 6.1 获取分诊规则列表

- 方法：`GET`
- 路径：`/api/triage/rules`
- 说明：查看当前分诊规则
- 权限：`triage:rule`

返回 `data` 为规则列表，主要字段：

- `id`
- `ruleCode`
- `ruleName`
- `symptomKeyword`
- `triageLevel`
- `recommendDeptId`
- `specialWeight`
- `fastTrack`
- `enabled`

---

### 6.2 更新分诊规则

- 方法：`PUT`
- 路径：`/api/triage/rules/{id}`
- 说明：修改分诊规则配置
- 权限：`triage:rule`

请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| ruleName | String | 否 | 规则名称 |
| symptomKeyword | String | 否 | 症状关键词 |
| triageLevel | Integer | 否 | 分诊等级 |
| recommendDeptId | Long | 否 | 推荐科室ID |
| specialWeight | Integer | 否 | 特殊权重 |
| fastTrack | Integer | 否 | 是否快速通道 |
| enabled | Integer | 否 | 是否启用 |

---

## 7. 排队接口

### 7.1 创建排队票据

- 方法：`POST`
- 路径：`/api/queues/tickets`
- 说明：根据到诊和分诊结果生成排队票据
- 权限：`queue:manage`

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
| visitId | Long | 是 | 到诊ID |
| assessmentId | Long | 是 | 分诊评估ID |
| roomId | Long | 否 | 指定诊室ID |

返回 `data` 主要字段：`ticketNo`、`visitId`、`patientId`、`assessmentId`、`deptId`、`roomId`、`triageLevel`、`priorityScore`、`status`、`recallCount`、`fastTrack`、`waitingCount`、`rank`、`estimatedWaitMinutes`、`enqueueTime`、`callTime`、`completeTime`

---

### 7.2 查询票据详情

- 方法：`GET`
- 路径：`/api/queues/tickets/{ticketNo}`
- 说明：根据票号查看排队详情
- 权限：`queue:manage` 或 `queue:call`

---

### 7.3 查询科室候诊列表

- 方法：`GET`
- 路径：`/api/queues/depts/{deptId}/waiting`
- 说明：查看指定科室当前候诊情况
- 权限：`queue:manage` 或 `dashboard:view`

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deptId | Long | 科室ID |
| waitingCount | Long | 候诊人数 |
| waitingTickets | List<QueueTicketVO> | 候诊票据列表 |

---

### 7.4 叫号下一位

- 方法：`POST`
- 路径：`/api/queues/rooms/{roomId}/call-next`
- 说明：诊室叫下一位患者
- 权限：`queue:call`

---

### 7.5 重呼

- 方法：`POST`
- 路径：`/api/queues/tickets/{ticketNo}/recall`
- 说明：对指定票据再次叫号
- 权限：`queue:call`

---

### 7.6 标记过号

- 方法：`POST`
- 路径：`/api/queues/tickets/{ticketNo}/missed`
- 说明：将票据标记为过号
- 权限：`queue:call`

---

### 7.7 完成就诊

- 方法：`POST`
- 路径：`/api/queues/tickets/{ticketNo}/complete`
- 说明：就诊完成后结束本次排队
- 权限：`queue:call`

---

### 7.8 取消排队

- 方法：`POST`
- 路径：`/api/queues/tickets/{ticketNo}/cancel`
- 说明：取消指定票据
- 权限：`queue:manage`

---

### 7.9 查询排队位次

- 方法：`GET`
- 路径：`/api/queues/tickets/{ticketNo}/rank`
- 说明：查询当前票据排队位置和预计等待时间
- 权限：`queue:manage` 或 `dashboard:view`

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| ticketNo | String | 票号 |
| status | String | 当前状态 |
| rank | Long | 当前排位 |
| waitingCount | Long | 候诊人数 |
| estimatedWaitMinutes | Long | 预计等待分钟数 |

---

## 8. 看板接口

### 8.1 科室看板汇总

- 方法：`GET`
- 路径：`/api/dashboard/depts/{deptId}/summary`
- 说明：查看某科室的候诊、叫号、完成等统计信息
- 权限：`dashboard:view`

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| deptId | Long | 科室ID |
| waitingCount | Long | 候诊人数 |
| callingCount | Long | 叫号中人数 |
| completedCount | Long | 已完成人数 |
| averageWaitMinutes | Long | 平均等待时长 |
| timeoutHighPriorityCount | Long | 超时高优先级人数 |

---

### 8.2 诊室当前叫号信息

- 方法：`GET`
- 路径：`/api/dashboard/rooms/{roomId}/current`
- 说明：查看某诊室当前正在处理的票据
- 权限：`dashboard:view`

返回 `data` 主要字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| roomId | Long | 诊室ID |
| ticketNo | String | 当前票号 |
| status | String | 当前状态 |
| patientId | Long | 患者ID |
| triageLevel | Integer | 分诊等级 |
| priorityScore | Integer | 优先级分数 |

---

## 9. 建议联调顺序

首次联调时，建议按下面顺序调用：

1. 登录 `/api/auth/login`
2. 新增患者 `/api/patients`
3. 创建到诊 `/api/visits`
4. 创建分诊评估 `/api/triage/assessments`
5. 创建排队票据 `/api/queues/tickets`
6. 查询排队位次 `/api/queues/tickets/{ticketNo}/rank`
7. 诊室叫号 `/api/queues/rooms/{roomId}/call-next`
8. 完成就诊 `/api/queues/tickets/{ticketNo}/complete`

---

## 10. 说明

- 本文档基于当前控制器代码整理。
- 若后续补充 Swagger、Knife4j 或新增接口，建议同步更新本文件。
- 如果需要，我还可以继续补一版“带完整请求/响应示例”的详细接口文档。
