# AI智能排队分诊实施清单

更新日期：2026-03-21

## 一、实施结论

本轮 AI 智能排队分诊改造已经完成核心闭环，当前系统已具备以下能力：

- 患者自助取号前可生成 AI 预分诊建议，并在 AI 不可用时自动回退到本地规则
- 护士分诊 `create/reassess` 已接入 AI 建议，保留规则判断与人工最终确认
- 候诊队列已支持高峰模式加权、等待老化解释、优先原因展示
- 分诊 AI 结果已持久化到 `triage_assessment`，并新增 `triage_ai_audit` 审计表
- Moonshot 配置已改为环境变量注入，仓库内未写入真实密钥

本轮同时修复了一个关键排序问题：

- `QueueDispatchServiceImpl.buildRank(...)` 现已按“前方人数”计算 `waitingCount`，不再误把“除自己外所有候诊人”都算进去

---

## 二、实施范围与落地原则

### 1. 保持现有排队主链路

本次没有重建独立 AI 排队引擎，仍复用现有链路：

- 分诊层产出 `triageLevel`、`priorityScore`、`fastTrack`
- 队列层通过 `QueueDispatchServiceImpl` 计算队列 score
- Redis ZSet 负责候诊顺序
- `call-next.lua` 继续负责原子叫号

### 2. AI 先建议、再入审计、最后影响展示

当前实现遵循以下原则：

- AI 输出结构化建议，不直接绕过人工确认
- AI 不可用时必须回退，不阻断取号和分诊主流程
- 优先级解释必须可读，且尽量给前端展示字段
- 审计先落分诊链路，再逐步扩展到队列链路

---

## 三、分阶段完成清单

## 第一阶段：患者智能预分诊

### 状态

已完成核心功能。

### 后端已落地

- `PatientSelfQueueServiceImpl` 已在自助取号流程中接入 `PatientTriageAiService.analyze(...)`
- 自助取号会先构建 AI 请求，再生成评估记录，再正式入队
- Moonshot 不可用、返回异常、非 JSON、配置不完整时，会自动回退到本地规则建议
- 自助取号结果会回写 AI 相关字段到 `triage_assessment`
- 自助取号结果会写入 `triage_ai_audit`
- `PatientQueueQueryServiceImpl` 已把 AI 建议字段回填给患者查询结果

### 前端已落地

- `PatientSelfQueuePage.vue` 已新增 AI 建议卡片
- 已展示推荐科室、风险等级、建议说明、置信度等信息
- `web/src/types/patient-queue.ts` 与 `web/src/api/patient-queue.ts` 对应接口字段已补齐

### 当前可验收结果

- 患者在自助取号后，可从查询结果看到 AI 推荐科室、风险等级、建议说明
- 即使未启用 AI，也不会阻断取号流程，系统会返回规则回退结果

---

## 第二阶段：医护智能辅助分诊

### 状态

已完成核心功能。

### 后端已落地

- `TriageAssessmentServiceImpl.create(...)` 已接入 AI 建议
- `TriageAssessmentServiceImpl.reassess(...)` 已接入 AI 建议
- AI 建议结果会写入：
  - `ai_suggested_level`
  - `ai_suggested_dept_id`
  - `ai_priority_score`
  - `ai_risk_level`
  - `ai_risk_tags`
  - `ai_confidence`
  - `ai_advice`
  - `ai_need_manual_review`
  - `ai_rule_diff`
  - `ai_model_version`
  - `ai_source`
  - `ai_audit_id`
- `TriageAssessmentVO`、`TriageAssessmentCreateDTO`、`TriageAssessment` 已补齐 AI 相关字段
- 每次 `create/reassess` 后都会写入 `triage_ai_audit`

### 前端已落地

- `TriageAssessmentCreatePage.vue` 已新增 AI 建议展示区域
- 前端可同时展示规则结果与 AI 建议
- 已展示 AI 建议等级、科室、风险标签、说明、置信度

### 当前可验收结果

- 分诊工作台已经不是纯规则结果页面，而是规则结果 + AI 建议并排展示
- AI 结果已具备持久化与审计链路，不再只是临时前端字段

---

## 第三阶段：智能优先排队

### 状态

已完成基础高峰策略与解释性增强。

### 后端已落地

- `AppQueueProperties` 已新增高峰策略配置：
  - `surgeWaitingThreshold`
  - `surgeHighPriorityThreshold`
  - `surgePriorityBonus`
  - `surgeFastTrackBonus`
  - `agingExplainThresholdMinutes`
  - `surgeEligibleLevelThreshold`
- `QueueDispatchServiceImpl` 已支持科室高峰模式判定
- 高峰模式下会对高等级和 fast track 患者进行额外加权
- 老化等待分仍保留，不会因为高峰策略导致普通患者长期饥饿等待
- `QueueTicketVO` 已增加：
  - `priorityReason`
  - `queueStrategyMode`
  - `surgePriorityApplied`
  - `agingBoostApplied`
  - `aiPriorityAdvice`
- `buildRank(...)` 已修复等待人数计算错误

### 前端已落地

- `QueueCallPage.vue` 已展示当前排序原因
- 已展示策略模式、是否触发高峰加权、是否触发等待老化
- 队列相关 types 与 API 字段已同步

### 当前可验收结果

- 系统已经可以解释“为什么这一位被优先”
- 高峰模式与等待老化已能体现在前端展示层
- 当前 Redis / Lua 叫号主链路无需重写即可继续工作

---

## 第四阶段：治理与审计

### 状态

已完成主体，仍有补强项。

### 已落地内容

- `schema.sql` 已为 `triage_assessment` 增加 AI 持久化字段
- `schema.sql` 已新增 `triage_ai_audit` 表
- `TriageAiAudit.java`、`TriageAiAuditMapper.java` 已补齐
- 审计表已记录：
  - 建议等级
  - 建议科室
  - 建议优先分
  - 风险等级
  - 风险标签
  - 建议说明
  - 置信度
  - 模型版本
  - 请求载荷
  - 响应载荷
  - 错误信息
  - 是否采纳
  - 最终等级与最终优先分

### 已落实的配置治理

- `application.yml` 中 AI 配置已经切换为环境变量占位：
  - `MOONSHOT_BASE_URL`
  - `MOONSHOT_API_KEY`
  - `MOONSHOT_MODEL`
- 仓库内没有落真实 Moonshot Key

### 当前未完全落地的点

- 还没有独立的 `queue_ai_audit` 表
- `QueueTicketVO.aiPriorityAdvice` 已预留，但后端尚未形成稳定映射来源
- 当前只更新了初始化 `schema.sql`，未补线上环境专用迁移脚本

---

## 四、关键改造清单

## 后端核心改造

- `src/main/java/com/hospital/triage/modules/patient/service/impl/PatientSelfQueueServiceImpl.java`
- `src/main/java/com/hospital/triage/modules/patient/service/impl/PatientQueueQueryServiceImpl.java`
- `src/main/java/com/hospital/triage/modules/patient/entity/vo/PatientQueueViewVO.java`
- `src/main/java/com/hospital/triage/modules/triage/service/PatientTriageAiService.java`
- `src/main/java/com/hospital/triage/modules/triage/service/impl/PatientTriageAiServiceImpl.java`
- `src/main/java/com/hospital/triage/modules/triage/service/impl/PatientTriageAiProperties.java`
- `src/main/java/com/hospital/triage/modules/triage/service/model/PatientTriageAiRequest.java`
- `src/main/java/com/hospital/triage/modules/triage/service/model/PatientTriageAiResult.java`
- `src/main/java/com/hospital/triage/modules/triage/service/impl/TriageAssessmentServiceImpl.java`
- `src/main/java/com/hospital/triage/modules/triage/entity/po/TriageAssessment.java`
- `src/main/java/com/hospital/triage/modules/triage/entity/vo/TriageAssessmentVO.java`
- `src/main/java/com/hospital/triage/modules/triage/entity/po/TriageAiAudit.java`
- `src/main/java/com/hospital/triage/modules/triage/mapper/TriageAiAuditMapper.java`
- `src/main/java/com/hospital/triage/modules/queue/service/impl/AppQueueProperties.java`
- `src/main/java/com/hospital/triage/modules/queue/service/impl/QueueDispatchServiceImpl.java`
- `src/main/java/com/hospital/triage/modules/queue/entity/vo/QueueTicketVO.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/schema.sql`

## 前端核心改造

- `web/src/views/patient/PatientSelfQueuePage.vue`
- `web/src/views/triage/TriageAssessmentCreatePage.vue`
- `web/src/views/queues/QueueCallPage.vue`
- `web/src/types/patient-queue.ts`
- `web/src/types/triage.ts`
- `web/src/types/queue.ts`
- `web/src/api/patient-queue.ts`
- `web/src/api/queue.ts`

---

## 五、测试与验证证据

## 已执行并通过

### 后端阶段验证

1. `mvn "-Dtest=PatientTriageAiServiceImplTest,PatientSelfQueueServiceImplTest,PatientQueueQueryServiceImplTest,TriageAssessmentServiceImplTest" test`
2. `mvn "-Dtest=PatientSelfQueueServiceImplTest,TriageAssessmentServiceImplTest,QueueDispatchServiceImplTest" test`
3. `mvn -Dtest=QueueDispatchServiceImplTest test`

### 前端阶段验证

1. `npm --prefix web run build`

### 全量回归验证

1. `mvn test`

结果：

- Maven 单测全量通过，共 51 个测试通过
- 另有 5 个 `QueueDispatchIntegrationTest` 集成测试因 Docker / Testcontainers 不可用被跳过
- 前端构建通过

## 本轮重点验证点

- 自助取号链路在 AI 启用与 AI 回退场景下均可完成入队
- 分诊 `create/reassess` 链路可产生 AI 建议并落审计
- 候诊排序解释字段可在后端 VO 与前端页面正常展示
- `buildRank(...)` 修复后，中间位置患者的 `waitingCount` 与 `estimatedWaitMinutes` 计算正确

---

## 六、手工串测清单

## 串测 1：患者自助取号

- 输入主诉、选择科室、完成取号
- 检查页面是否展示 AI 推荐科室、风险等级、建议说明、置信度
- 检查患者查询结果是否带回 AI 字段
- 关闭 AI 配置后再次取号，确认仍可正常完成流程

## 串测 2：护士分诊

- 对未分诊患者发起 `create`
- 检查页面是否同时展示规则结果和 AI 建议
- 触发一次 `reassess`
- 检查 `triage_assessment` 与 `triage_ai_audit` 是否产生对应记录

## 串测 3：候诊解释与高峰策略

- 制造同科室多名等待患者
- 放入 1-2 名高等级或 fast track 患者
- 检查 `QueueCallPage` 是否展示：
  - 当前排序原因
  - `SURGE` / `NORMAL` 策略模式
  - 高峰加权标记
  - 等待老化标记
- 检查中间位置患者的 `waitingCount` 是否等于其前方人数

---

## 七、总结反思

## 本轮做对的地方

- 没有推翻现有排队主链路，而是在原有 `priorityScore + aging + Redis/Lua` 基础上增量增强
- AI 服务做成了“可启用、可回退、可审计”的结构，降低了上线风险
- 前后端字段基本同步，患者端、护士端、叫号端三条主视图都已接住
- 在实现功能的同时补上了排序解释，避免系统变成不可理解的黑盒

## 深审后确认的剩余问题

### 1. 队列 AI 审计仍未独立建表

当前分诊 AI 审计已完成，但队列侧还没有 `queue_ai_audit`。如果后续要审计“高峰调权为什么生效、何时生效、对谁生效”，仍需要单独补表或补事件扩展字段。

### 2. `aiPriorityAdvice` 仍是预留位

前端和 VO 都已经预留了 `aiPriorityAdvice`，但当前后端没有稳定赋值链路。现阶段页面可正常显示，但该字段尚不构成完整业务能力。

### 3. 真实 Moonshot 联网集成尚未做

当前已验证本地回退逻辑、请求封装、返回解析和异常回退，但还没有基于真实 Moonshot 环境执行端到端联网集成测试。

### 4. 数据库迁移策略仍需补齐

当前只更新了 `schema.sql`，更适合初始化环境。若用于已有数据库环境，仍需补正式迁移脚本或上线说明。

### 5. Docker 依赖的集成测试未在本机跑通

`QueueDispatchIntegrationTest` 因 Docker / Testcontainers 不可用被跳过，因此 Redis/Lua 相关的完整集成验证仍建议在具备容器环境的 CI 或验收机补跑。

### 6. 会话中暴露过真实 Key，需视为已泄露

虽然仓库代码未写入真实密钥，但会话里曾出现真实 API Key，应视为已泄露并立即旋转。后续只允许通过环境变量或本地私有配置注入。

---

## 八、补救动作

## P0

- 立即旋转已暴露的 Moonshot Key
- 在部署环境统一改用 `MOONSHOT_API_KEY`、`MOONSHOT_BASE_URL` 注入

## P1

- 为队列策略新增独立 `queue_ai_audit` 或扩展事件审计字段
- 给 `aiPriorityAdvice` 建立明确来源，建议优先映射最新分诊评估中的 AI 建议说明

## P2

- 补线上数据库迁移脚本
- 在可用 Docker 的环境补跑 `QueueDispatchIntegrationTest`
- 追加真实 Moonshot 联网验收记录

---

## 九、验收结论

当前版本已经完成“患者智能预分诊 + 医护智能辅助分诊 + 基础高峰优先排队 + AI 分诊审计”的主闭环，具备提交验收和继续迭代的条件。

若按交付成熟度划分：

- 核心业务能力：已完成
- 前后端主页面接入：已完成
- 单元测试与构建验证：已完成
- 队列侧深度审计与线上迁移配套：待补强

结论：可以进入验收收尾阶段，但应把 `queue_ai_audit`、`aiPriorityAdvice` 真正赋值、真实联网验证、数据库迁移脚本列为下一轮优先补强项。
