
<template>
  <div class="self-queue-page">
    <div class="self-queue-shell">
      <section class="self-queue-hero">
        <div class="self-queue-hero__content">
          <span class="self-queue-hero__eyebrow">Patient Self-Service Portal</span>
          <h1>患者自助取号</h1>
          <p>同一入口支持已有患者直接取号，也支持新患者先建档再进入候诊队列。提交后会返回票号、候诊位置、预计等待时间，以及 AI 预分诊建议。</p>
          <div class="self-queue-hero__badges">
            <span>已有患者可快速核验取号</span>
            <span>新患者支持现场建档后排队</span>
            <span>结果以现场叫号与分诊安排为准</span>
          </div>
        </div>

        <div class="self-queue-hero__panel">
          <div class="self-queue-hero__panel-label">办理流程</div>
          <ol>
            <li>选择“已有患者取号”或“新患者建档”。</li>
            <li>填写身份信息或建档信息。</li>
            <li>选择本次就诊科室并补充主诉。</li>
            <li>提交后查看票号、候诊进度与 AI 建议。</li>
          </ol>
        </div>
      </section>

      <section class="self-queue-main">
        <el-card shadow="hover" class="self-queue-card self-queue-card--form">
          <template #header>
            <div class="self-queue-card__header">
              <div>
                <strong>自助取号办理</strong>
                <div>支持已有患者直接取号，也支持新患者先建档再排队，统一返回当前候诊信息。</div>
              </div>
              <el-tag type="info" effect="plain" round>与现场导诊同源流程</el-tag>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
            <section class="self-queue-form-section">
              <div class="self-queue-section-title">
                <span class="self-queue-section-title__index">01</span>
                <div>
                  <strong>办理方式</strong>
                  <p>请选择当前是已有患者直接取号，还是首次来院需要先建档。</p>
                </div>
              </div>

              <div class="mode-switch" role="radiogroup" aria-label="办理方式">
                <button
                  v-for="item in patientModeOptions"
                  :key="item.value"
                  type="button"
                  class="mode-card"
                  :class="{ 'is-active': form.patientMode === item.value }"
                  @click="changePatientMode(item.value)"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.description }}</span>
                </button>
              </div>
            </section>

            <section class="self-queue-form-section">
              <div class="self-queue-section-title">
                <span class="self-queue-section-title__index">02</span>
                <div>
                  <strong>{{ identitySectionTitle }}</strong>
                  <p>{{ identitySectionDescription }}</p>
                </div>
              </div>

              <div v-if="isNewPatientMode" class="self-queue-form-grid">
                <el-form-item
                  label="患者姓名"
                  prop="patientName"
                  :show-message="shouldShowFieldMessage('patientName')"
                  @mouseenter="handleFieldMouseEnter('patientName')"
                  @mouseleave="handleFieldMouseLeave('patientName')"
                >
                  <el-input v-model="form.patientName" placeholder="如 张三" clearable size="large" />
                </el-form-item>

                <el-form-item
                  label="手机号"
                  prop="phone"
                  :show-message="shouldShowFieldMessage('phone')"
                  @mouseenter="handleFieldMouseEnter('phone')"
                  @mouseleave="handleFieldMouseLeave('phone')"
                >
                  <el-input v-model="form.phone" maxlength="11" placeholder="如 13800001234" clearable size="large" />
                </el-form-item>

                <el-form-item
                  label="性别"
                  prop="gender"
                  :show-message="shouldShowFieldMessage('gender')"
                  @mouseenter="handleFieldMouseEnter('gender')"
                  @mouseleave="handleFieldMouseLeave('gender')"
                >
                  <el-select v-model="form.gender" placeholder="请选择性别" style="width: 100%" size="large">
                    <el-option v-for="item in genderOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>

                <el-form-item
                  label="出生日期"
                  prop="birthDate"
                  :show-message="shouldShowFieldMessage('birthDate')"
                  @mouseenter="handleFieldMouseEnter('birthDate')"
                  @mouseleave="handleFieldMouseLeave('birthDate')"
                >
                  <el-input
                    v-model="form.birthDate"
                    placeholder="如 1995-05-20"
                    clearable
                    maxlength="10"
                    size="large"
                    @blur="normalizeBirthDateField"
                  />
                </el-form-item>

                <el-form-item label="身份证号（可选）" prop="idCard">
                  <el-input v-model="form.idCard" placeholder="用于补充档案信息" clearable size="large" />
                </el-form-item>

                <el-form-item label="过敏史（可选）" prop="allergyHistory">
                  <el-input
                    v-model="form.allergyHistory"
                    type="textarea"
                    :autosize="{ minRows: 2, maxRows: 4 }"
                    placeholder="如：青霉素过敏 / 无明确过敏史"
                    maxlength="120"
                    show-word-limit
                  />
                </el-form-item>

                <el-form-item label="特殊标签（可选）" prop="specialTags">
                  <el-input
                    v-model="form.specialTags"
                    placeholder="如：孕妇 / 老年人 / 需轮椅协助"
                    clearable
                    maxlength="60"
                    show-word-limit
                    size="large"
                  />
                </el-form-item>
              </div>

              <div v-else class="self-queue-form-grid">
                <el-form-item
                  label="患者姓名"
                  prop="patientName"
                  :show-message="shouldShowFieldMessage('patientName')"
                  @mouseenter="handleFieldMouseEnter('patientName')"
                  @mouseleave="handleFieldMouseLeave('patientName')"
                >
                  <el-input v-model="form.patientName" placeholder="如 张三" clearable size="large" />
                </el-form-item>

                <el-form-item
                  label="手机号后 4 位"
                  prop="phoneSuffix"
                  :show-message="shouldShowFieldMessage('phoneSuffix')"
                  @mouseenter="handleFieldMouseEnter('phoneSuffix')"
                  @mouseleave="handleFieldMouseLeave('phoneSuffix')"
                >
                  <el-input v-model="form.phoneSuffix" maxlength="4" placeholder="如 1234" clearable size="large" />
                </el-form-item>
              </div>

              <div class="mode-tip">{{ modeTip }}</div>
            </section>

            <section class="self-queue-form-section">
              <div class="self-queue-section-title">
                <span class="self-queue-section-title__index">03</span>
                <div>
                  <strong>就诊信息</strong>
                  <p>选择本次就诊科室，并可选填写主诉，帮助现场更快识别就诊上下文。</p>
                </div>
              </div>

              <div class="self-queue-form-grid self-queue-form-grid--single">
                <el-form-item
                  label="选择就诊科室"
                  prop="deptId"
                  :show-message="shouldShowFieldMessage('deptId')"
                  @mouseenter="handleFieldMouseEnter('deptId')"
                  @mouseleave="handleFieldMouseLeave('deptId')"
                >
                  <el-select v-model="form.deptId" placeholder="请选择科室" style="width: 100%" size="large" :loading="deptLoading">
                    <el-option v-for="item in deptOptions" :key="item.id" :label="item.deptName" :value="item.id" />
                  </el-select>
                </el-form-item>

                <el-form-item label="主诉 / 主要不适（可选）" prop="chiefComplaint">
                  <el-input
                    v-model="form.chiefComplaint"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 5 }"
                    placeholder="如：发热 2 天，伴咳嗽、咽痛"
                    maxlength="100"
                    show-word-limit
                  />
                </el-form-item>
              </div>

              <div class="complaint-tags">
                <span class="complaint-tags__label">常见主诉快捷填写</span>
                <div class="complaint-tags__list">
                  <button v-for="tag in complaintTags" :key="tag" type="button" class="complaint-tag" @click="applyComplaintTag(tag)">{{ tag }}</button>
                </div>
                <div class="complaint-tags__hint">点击后会补充到主诉文本中，不会额外新增提交字段。</div>
              </div>
            </section>

            <div class="self-queue-actions">
              <el-button type="primary" size="large" :loading="submitting" @click="handleEnroll">{{ submitButtonText }}</el-button>
              <span class="self-queue-tip">{{ serviceTip }}</span>
            </div>
          </el-form>
        </el-card>

        <aside class="self-queue-aside">
          <el-card shadow="hover" class="self-queue-card self-queue-card--aside">
            <template #header>
              <div class="self-queue-card__header self-queue-card__header--stack">
                <div>
                  <strong>办理说明</strong>
                  <div>同一入口支持已有患者取号与新患者建档，提交后会自动返回当前排队状态。</div>
                </div>
              </div>
            </template>
            <ul class="info-list">
              <li>已有患者可通过“姓名 + 手机号后 4 位”快速核验并直接取号。</li>
              <li>新患者补充基础信息后，系统会先创建患者档案，再自动创建本次排队记录。</li>
              <li>新患者手机号会自动提取后 4 位，用作后续自助查询和身份核验。</li>
              <li>过敏史和特殊标签虽然选填，但建议尽量填写，方便医生更快了解情况。</li>
            </ul>
          </el-card>

          <el-card shadow="hover" class="self-queue-card self-queue-card--aside self-queue-card--tone">
            <template #header>
              <div class="self-queue-card__header self-queue-card__header--stack">
                <div>
                  <strong>填写建议</strong>
                  <div>尽量使用简洁短句描述主要症状，便于分诊与候诊沟通。</div>
                </div>
              </div>
            </template>
            <div class="suggestion-block">
              <div class="suggestion-block__item"><span>示例 1</span><strong>发热 2 天，伴咳嗽</strong></div>
              <div class="suggestion-block__item"><span>示例 2</span><strong>腹痛 3 小时，伴恶心</strong></div>
              <div class="suggestion-block__item"><span>示例 3</span><strong>头晕乏力，血压偏高</strong></div>
            </div>
          </el-card>

          <el-card shadow="hover" class="self-queue-card self-queue-card--aside">
            <template #header>
              <div class="self-queue-card__header self-queue-card__header--stack">
                <div>
                  <strong>AI 预分诊提示</strong>
                  <div>提交后系统会生成 AI 预分诊建议，并展示推荐科室、风险等级和结构化主诉。</div>
                </div>
              </div>
            </template>
            <ul class="info-list">
              <li>AI 结果仅作为辅助参考，最终安排仍以现场分诊和叫号为准。</li>
              <li>若识别到高风险或特殊症状，页面会提示人工复核。</li>
              <li>当外部模型不可用时，系统会自动回退到本地规则结果。</li>
            </ul>
          </el-card>
        </aside>
      </section>

      <el-alert v-if="errorMessage" class="self-queue-alert" type="error" :closable="false" :title="errorMessage" show-icon />

      <section class="result-dashboard">
        <el-card v-if="result" shadow="hover" class="self-queue-card result-card result-card--status">
          <template #header>
            <div class="self-queue-card__header">
              <div>
                <strong>{{ resultHeading.title }}</strong>
                <div>{{ resultHeading.description }}</div>
              </div>
              <el-tag :type="statusTagType(result.queueStatus)" effect="dark">{{ result.queueStatusText || '暂无排队状态' }}</el-tag>
            </div>
          </template>
          <div class="result-status-hero">
            <div>
              <div class="result-status-hero__name">{{ result.patientName || '-' }}</div>
              <div class="result-status-hero__message">{{ result.queueMessage || '当前暂无排队信息' }}</div>
            </div>
            <div class="result-status-hero__ticket"><span>票号</span><strong>{{ result.ticketNo || '-' }}</strong></div>
          </div>
          <div class="result-metrics">
            <article class="result-metric result-metric--accent"><span>当前排位</span><strong>{{ formatCount(result.rank) }}</strong></article>
            <article class="result-metric"><span>前方人数</span><strong>{{ formatCount(result.waitingCount) }}</strong></article>
            <article class="result-metric"><span>预计等待</span><strong>{{ formatMinutes(result.estimatedWaitMinutes) }}</strong></article>
            <article class="result-metric"><span>已等待</span><strong>{{ formatMinutes(result.waitedMinutes) }}</strong></article>
          </div>
        </el-card>

        <el-card shadow="hover" class="self-queue-card ai-card">
          <template #header>
            <div class="self-queue-card__header ai-card__header">
              <strong>AI 预分诊建议</strong>
              <div class="ai-card__header-actions">
                <span class="ai-card__confidence">{{ aiConfidenceLabel }}</span>
                <el-tag :type="result ? 'success' : 'info'" effect="plain">{{ result ? 'AI 结果已生成' : '提交后生成' }}</el-tag>
              </div>
            </div>
          </template>
          <div class="ai-card__grid">
            <div class="ai-card__item"><span>推荐科室</span><strong>{{ aiDeptLabel }}</strong></div>
            <div class="ai-card__item">
              <span>风险等级</span>
              <strong>{{ aiRiskLevelLabel }}</strong>
              <small v-if="result?.aiNeedManualReview" class="ai-card__tag">建议人工复核</small>
              <small v-else>{{ result ? '当前无需额外人工复核' : '提交取号后自动判断' }}</small>
            </div>
            <div class="ai-card__item"><span>建议等级</span><strong>{{ aiSuggestedLevelLabel }}</strong><small>{{ aiRiskTagsLabel }}</small></div>
            <div class="ai-card__item"><span>结构化主诉</span><strong>{{ aiStructuredSymptomsLabel }}</strong><small>{{ aiSourceLabel }}</small></div>
            <div class="ai-card__item ai-card__item--advice"><span>AI 建议说明</span><p>{{ result?.aiAdvice || '提交取号后，系统会根据主诉、科室和患者信息生成 AI 预分诊建议。' }}</p></div>
          </div>
        </el-card>

        <div v-if="result" class="result-grid">
          <el-card shadow="hover" class="self-queue-card">
            <template #header><div class="self-queue-card__header self-queue-card__header--stack"><div><strong>就诊摘要</strong><div>展示当前患者、就诊单与排队摘要信息。</div></div></div></template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="患者姓名">{{ result.patientName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="患者编号">{{ result.patientNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="就诊状态">{{ result.visitStatusText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="队列状态">{{ result.queueStatusText || '-' }}</el-descriptions-item>
              <el-descriptions-item label="票号">{{ result.ticketNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="分诊等级">{{ formatLevel(result.triageLevel) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="hover" class="self-queue-card">
            <template #header><div class="self-queue-card__header self-queue-card__header--stack"><div><strong>接诊信息</strong><div>展示科室、诊室与医生信息；若暂未分配，会自动降级显示。</div></div></div></template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="科室">{{ result.deptName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="诊室 / 医生">{{ formatRoom(result.roomName, result.doctorName) }}</el-descriptions-item>
              <el-descriptions-item label="状态提示">{{ result.queueMessage || '当前暂无排队信息' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="hover" class="self-queue-card">
            <template #header><div class="self-queue-card__header self-queue-card__header--stack"><div><strong>时间信息</strong><div>用于查看入队、叫号与完成时间的当前状态。</div></div></div></template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="入队时间">{{ formatDateTime(result.enqueueTime) }}</el-descriptions-item>
              <el-descriptions-item label="叫号时间">{{ formatDateTime(result.callTime) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ formatDateTime(result.completeTime) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="hover" class="self-queue-card self-queue-card--remind">
            <template #header><div class="self-queue-card__header self-queue-card__header--stack"><div><strong>下一步提醒</strong><div>请以现场叫号、护士站和工作人员通知为准。</div></div></div></template>
            <ul class="next-steps">
              <li>候诊时请留意屏幕和广播，不要远离候诊区域。</li>
              <li>若显示“请立即前往诊室”或“已过号”，请尽快联系护士台处理。</li>
              <li>若当前暂无票号或信息异常，请前往服务台人工协助。</li>
            </ul>
          </el-card>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getDeptOptions } from '@/api/clinic'
import { enrollPatientQueue, queryPatientQueue } from '@/api/patient-queue'
import type { ClinicDeptOption } from '@/types/clinic'
import type { PatientQueueView, PatientSelfQueueEnrollDTO, PatientSelfQueueMode } from '@/types/patient-queue'

const EXISTING_MODE: PatientSelfQueueMode = 'EXISTING'
const NEW_MODE: PatientSelfQueueMode = 'NEW'
const ACTIVE_QUEUE_STATUSES = new Set(['WAITING', 'CALLING', 'MISSED'])
const BIRTH_DATE_REGEX = /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/
const ERROR_MESSAGE_HIDE_DELAY_MS = 2000
const ERROR_MESSAGE_FIELDS = ['patientName', 'phoneSuffix', 'phone', 'gender', 'birthDate', 'deptId'] as const

type ErrorMessageField = (typeof ERROR_MESSAGE_FIELDS)[number]

const patientModeOptions = [
  { value: EXISTING_MODE, label: '已有患者取号', description: '输入姓名和手机号后 4 位，快速核验已有档案并直接进入排队流程。' },
  { value: NEW_MODE, label: '新患者建档', description: '补充基础信息后创建患者档案，再自动生成本次就诊排队记录。' }
] as const

const genderOptions = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' }
]

const complaintTags = ['发热咳嗽', '腹痛恶心', '头晕乏力', '胸闷气短', '外伤疼痛', '复诊开药']

const formRef = ref<FormInstance>()
const submitting = ref(false)
const deptLoading = ref(false)
const errorMessage = ref('')
const result = ref<PatientQueueView | null>(null)
const deptOptions = ref<ClinicDeptOption[]>([])
const hadActiveQueueBeforeEnroll = ref(false)
const lastEnrollMode = ref<PatientSelfQueueMode>(EXISTING_MODE)
const fieldErrorVisible = reactive<Record<ErrorMessageField, boolean>>({
  patientName: true,
  phoneSuffix: true,
  phone: true,
  gender: true,
  birthDate: true,
  deptId: true
})
const fieldErrorHideTimers = new Map<ErrorMessageField, ReturnType<typeof setTimeout>>()

const form = reactive<PatientSelfQueueEnrollDTO>({
  patientMode: EXISTING_MODE,
  patientName: '',
  phoneSuffix: '',
  phone: '',
  gender: '',
  birthDate: '',
  idCard: '',
  allergyHistory: '',
  specialTags: '',
  deptId: 0,
  chiefComplaint: ''
})

const isNewPatientMode = computed(() => form.patientMode === NEW_MODE)
const identitySectionTitle = computed(() => (isNewPatientMode.value ? '患者建档' : '身份核验'))
const identitySectionDescription = computed(() =>
  isNewPatientMode.value ? '首次来院患者请补充基础档案信息，系统会自动创建患者档案。' : '系统仅核验患者姓名和手机号后 4 位，不会暴露患者档案是否存在。'
)
const modeTip = computed(() =>
  isNewPatientMode.value ? '系统会自动提取手机号后 4 位作为后续自助查询和核验凭证。出生日期可直接输入，如 1995-05-20。' : '如果是首次来院患者，请切换到“新患者建档”后再办理取号。'
)
const submitButtonText = computed(() => (isNewPatientMode.value ? '确认建档并取号' : '确认取号并查看进度'))
const serviceTip = computed(() =>
  isNewPatientMode.value ? '出生日期支持直接填写；如证件信息暂不确定，可先留空，后续由服务台补充。' : '如未识别到档案或信息有误，请前往导诊台或服务台处理。'
)

function shouldShowFieldMessage(field: ErrorMessageField) {
  return fieldErrorVisible[field]
}

function handleFieldMouseEnter(field: ErrorMessageField) {
  clearFieldErrorHideTimer(field)
  fieldErrorVisible[field] = true
}

function handleFieldMouseLeave(field: ErrorMessageField) {
  clearFieldErrorHideTimer(field)
  fieldErrorHideTimers.set(field, setTimeout(() => {
    fieldErrorVisible[field] = false
    fieldErrorHideTimers.delete(field)
  }, ERROR_MESSAGE_HIDE_DELAY_MS))
}

function clearFieldErrorHideTimer(field: ErrorMessageField) {
  const timer = fieldErrorHideTimers.get(field)
  if (timer) {
    clearTimeout(timer)
    fieldErrorHideTimers.delete(field)
  }
}

function resetFieldErrorVisibility() {
  ERROR_MESSAGE_FIELDS.forEach((field) => {
    clearFieldErrorHideTimer(field)
    fieldErrorVisible[field] = true
  })
}

function failValidation(field: ErrorMessageField, callback: (error?: Error) => void, message: string) {
  handleFieldMouseEnter(field)
  callback(new Error(message))
}

const rules: FormRules<PatientSelfQueueEnrollDTO> = {
  patientName: [{ validator: validatePatientName, trigger: 'blur' }],
  phoneSuffix: [{ validator: validatePhoneSuffix, trigger: 'blur' }],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  gender: [{ validator: validateGender, trigger: 'change' }],
  birthDate: [{ validator: validateBirthDate, trigger: ['blur', 'change'] }],
  deptId: [{ validator: validateDeptId, trigger: 'change' }]
}

const resultHeading = computed(() => {
  const current = result.value
  if (!current) return { title: '办理结果', description: '提交完成后，将在这里展示最新排队信息。' }
  if (hadActiveQueueBeforeEnroll.value && current.hasActiveQueue) {
    return { title: '检测到您已有有效排队记录', description: '系统不会重复取号，以下展示的是当前有效候诊信息。' }
  }
  if (!current.ticketNo) return { title: '当前未生成排队票号', description: '系统已返回当前状态，请根据现场安排或服务台通知继续办理。' }
  if (lastEnrollMode.value === NEW_MODE) {
    return { title: '建档与取号已完成', description: '系统已生成患者编号和本次排队票号，请留意候诊区叫号信息。' }
  }
  return { title: '自助取号已完成', description: '请保留票号，并留意候诊区屏幕和现场广播。' }
})

const aiDeptLabel = computed(() => {
  const target = result.value
  if (target?.aiSuggestedDeptName) return target.aiSuggestedDeptName
  if (target?.aiSuggestedDeptId) return deptOptions.value.find((item) => item.id === target.aiSuggestedDeptId)?.deptName || `科室 ${target.aiSuggestedDeptId}`
  return '待生成'
})
const aiRiskLevelLabel = computed(() => formatAiRiskLevel(result.value?.aiRiskLevel))
const aiSuggestedLevelLabel = computed(() => (!result.value?.aiSuggestedLevel ? '待生成' : `${result.value.aiSuggestedLevel} 级`))
const aiRiskTagsLabel = computed(() => (result.value?.aiRiskTags?.length ? result.value.aiRiskTags.join(' / ') : '提交后生成风险标签'))
const aiStructuredSymptomsLabel = computed(() => (result.value?.aiStructuredSymptoms?.length ? result.value.aiStructuredSymptoms.join(' / ') : '提交后生成结构化主诉'))
const aiSourceLabel = computed(() => {
  const source = result.value?.aiSource
  const modelVersion = result.value?.aiModelVersion
  const sourceLabel = source === 'MOONSHOT' ? 'Moonshot' : source === 'RULE_FALLBACK' ? '本地规则回退' : source || ''
  const parts = [sourceLabel, modelVersion].filter(Boolean)
  return parts.length ? parts.join(' / ') : '来源待生成'
})
const aiConfidenceLabel = computed(() => {
  const value = result.value?.aiConfidence
  if (typeof value === 'number') return `${Math.round(value > 1 ? value : value * 100)}% 置信度`
  return '置信度待生成'
})

async function loadDepts() {
  deptLoading.value = true
  try {
    deptOptions.value = await getDeptOptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室列表失败')
  } finally {
    deptLoading.value = false
  }
}

function validatePatientName(_rule: unknown, value: string | undefined, callback: (error?: Error) => void) {
  if (!normalizeText(value)) return failValidation('patientName', callback, '请输入患者姓名')
  callback()
}
function validatePhoneSuffix(_rule: unknown, value: string | undefined, callback: (error?: Error) => void) {
  if (isNewPatientMode.value) return callback()
  const normalized = value?.trim() || ''
  if (!normalized) return failValidation('phoneSuffix', callback, '请输入手机号后 4 位')
  if (!/^\d{4}$/.test(normalized)) return failValidation('phoneSuffix', callback, '手机号后 4 位应为 4 位数字')
  callback()
}
function validatePhone(_rule: unknown, value: string | undefined, callback: (error?: Error) => void) {
  if (!isNewPatientMode.value) return callback()
  const normalized = value?.trim() || ''
  if (!normalized) return failValidation('phone', callback, '请输入手机号')
  if (!/^1\d{10}$/.test(normalized)) return failValidation('phone', callback, '请输入 11 位手机号')
  callback()
}
function validateGender(_rule: unknown, value: string | undefined, callback: (error?: Error) => void) {
  if (!isNewPatientMode.value) return callback()
  if (!value) return failValidation('gender', callback, '请选择性别')
  callback()
}
function validateBirthDate(_rule: unknown, value: string | undefined, callback: (error?: Error) => void) {
  if (!isNewPatientMode.value) return callback()
  const rawValue = normalizeText(value)
  if (!rawValue) return failValidation('birthDate', callback, '请输入出生日期')
  const normalizedBirthDate = normalizeBirthDate(rawValue)
  if (!normalizedBirthDate) return failValidation('birthDate', callback, '请输入正确的出生日期，如 1995-05-20')
  if (normalizedBirthDate > todayIsoDate()) return failValidation('birthDate', callback, '出生日期不能晚于今天')
  callback()
}
function validateDeptId(_rule: unknown, value: number | undefined, callback: (error?: Error) => void) {
  if (!value) return failValidation('deptId', callback, '请选择就诊科室')
  callback()
}
function normalizeBirthDateField() {
  const normalizedBirthDate = normalizeBirthDate(form.birthDate)
  if (normalizedBirthDate) form.birthDate = normalizedBirthDate
}
function changePatientMode(mode: PatientSelfQueueMode) {
  if (form.patientMode === mode) return
  form.patientMode = mode
  errorMessage.value = ''
  result.value = null
  hadActiveQueueBeforeEnroll.value = false
  if (mode === EXISTING_MODE && !form.phoneSuffix && form.phone?.trim().length === 11) form.phoneSuffix = form.phone.trim().slice(-4)
  resetFieldErrorVisibility()
  formRef.value?.clearValidate()
}
function normalizeText(value?: string) {
  const normalized = value?.trim()
  return normalized || undefined
}
function normalizeBirthDate(value?: string) {
  const normalized = normalizeText(value)
  if (!normalized) return undefined
  const match = normalized.match(BIRTH_DATE_REGEX)
  if (!match) return undefined
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day)
  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day) || Number.isNaN(date.getTime()) || date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return undefined
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
function todayIsoDate() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
function applyComplaintTag(tag: string) {
  form.chiefComplaint = form.chiefComplaint?.trim() ? `${form.chiefComplaint.trim()}，${tag}` : tag
}
async function detectExistingActiveQueue() {
  if (isNewPatientMode.value) return false
  const patientName = normalizeText(form.patientName)
  const phoneSuffix = form.phoneSuffix?.trim() || ''
  if (!patientName || !/^\d{4}$/.test(phoneSuffix)) return false
  try {
    const current = await queryPatientQueue({ patientName, phoneSuffix })
    return current.hasActiveQueue && ACTIVE_QUEUE_STATUSES.has(current.queueStatus || '')
  } catch {
    return false
  }
}
function buildPayload(mode: PatientSelfQueueMode): PatientSelfQueueEnrollDTO {
  const payload: PatientSelfQueueEnrollDTO = { patientMode: mode, patientName: normalizeText(form.patientName), deptId: form.deptId, chiefComplaint: normalizeText(form.chiefComplaint) }
  if (mode === NEW_MODE) {
    const phone = normalizeText(form.phone)
    return { ...payload, phone, phoneSuffix: phone?.slice(-4), gender: form.gender || undefined, birthDate: normalizeBirthDate(form.birthDate), idCard: normalizeText(form.idCard), allergyHistory: normalizeText(form.allergyHistory), specialTags: normalizeText(form.specialTags) }
  }
  return { ...payload, phoneSuffix: form.phoneSuffix?.trim() || '' }
}
async function handleEnroll() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const currentMode = form.patientMode || EXISTING_MODE
  submitting.value = true
  errorMessage.value = ''
  hadActiveQueueBeforeEnroll.value = false
  lastEnrollMode.value = currentMode
  try {
    hadActiveQueueBeforeEnroll.value = await detectExistingActiveQueue()
    result.value = await enrollPatientQueue(buildPayload(currentMode))
    if (hadActiveQueueBeforeEnroll.value && result.value.hasActiveQueue) ElMessage.success('已识别到当前有效排队记录')
    else if (currentMode === NEW_MODE && result.value.ticketNo) ElMessage.success('新患者建档并取号成功')
    else if (result.value.ticketNo) ElMessage.success('自助取号成功，已返回最新排队信息')
    else ElMessage.success('提交成功，已返回当前状态')
  } catch (error) {
    const message = error instanceof Error ? error.message : '自助取号失败，请前往现场服务台处理'
    errorMessage.value = message
    result.value = null
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
function statusTagType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'WAITING': return 'warning'
    case 'CALLING': return 'success'
    case 'MISSED': return 'danger'
    case 'COMPLETED': return 'success'
    case 'CANCELLED': return 'info'
    default: return 'info'
  }
}
function formatLevel(level?: number) { return level ? `${level} 级` : '-' }
function formatAiRiskLevel(riskLevel?: string) {
  switch (riskLevel) {
    case 'CRITICAL':
      return '危急'
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
      return '低风险'
    default:
      return riskLevel || '待生成'
  }
}
function formatRoom(roomName?: string, doctorName?: string) {
  if (!roomName && !doctorName) return '-'
  if (roomName && doctorName) return `${roomName} / ${doctorName}`
  return roomName || doctorName || '-'
}
function formatCount(value?: number) { return typeof value === 'number' && value >= 0 ? String(value) : '-' }
function formatMinutes(value?: number) { return typeof value === 'number' && value >= 0 ? `${value} 分钟` : '-' }
function formatDateTime(value?: string) { return value ? value.replace('T', ' ') : '-' }
onMounted(() => { void loadDepts() })
onBeforeUnmount(() => { resetFieldErrorVisibility() })
</script>
<style scoped>
.self-queue-page {
  min-height: 100vh;
  padding: 28px 20px 56px;
  background: radial-gradient(circle at top left, rgba(15, 118, 110, 0.18), transparent 30%), radial-gradient(circle at top right, rgba(14, 165, 233, 0.16), transparent 28%), linear-gradient(180deg, #f4faf9 0%, #eef5fb 48%, #f9fcff 100%);
}
.self-queue-shell { max-width: 1220px; margin: 0 auto; display: grid; gap: 22px; }
.self-queue-hero { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr); gap: 18px; }
.self-queue-hero__content,
.self-queue-hero__panel {
  position: relative;
  overflow: hidden;
  border-radius: 30px;
  color: #effcf9;
  box-shadow: 0 24px 54px rgba(15, 23, 42, 0.14);
}
.self-queue-hero__content { padding: 34px 36px; background: linear-gradient(135deg, rgba(8, 47, 73, 0.94), rgba(15, 118, 110, 0.92)); }
.self-queue-hero__panel { padding: 28px; background: linear-gradient(160deg, rgba(12, 74, 110, 0.94), rgba(14, 116, 144, 0.92)); }
.self-queue-hero__content::after,
.self-queue-hero__panel::after {
  content: '';
  position: absolute;
  inset: auto -60px -70px auto;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18), transparent 68%);
}
.self-queue-hero__eyebrow { display: inline-block; margin-bottom: 12px; font-size: 12px; letter-spacing: 0.18em; text-transform: uppercase; color: rgba(236, 253, 245, 0.72); }
.self-queue-hero h1 { margin: 0; font-size: 40px; line-height: 1.12; color: #fff; }
.self-queue-hero p { margin: 14px 0 0; max-width: 760px; font-size: 15px; line-height: 1.75; color: rgba(240, 253, 250, 0.92); }
.self-queue-hero__badges { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.self-queue-hero__badges span { padding: 10px 14px; border-radius: 999px; background: rgba(255, 255, 255, 0.12); border: 1px solid rgba(255, 255, 255, 0.14); font-size: 13px; color: #ecfeff; }
.self-queue-hero__panel-label { margin-bottom: 14px; font-size: 12px; letter-spacing: 0.16em; text-transform: uppercase; color: rgba(224, 242, 254, 0.76); }
.self-queue-hero__panel ol { margin: 0; padding-left: 18px; display: grid; gap: 14px; line-height: 1.7; }
.self-queue-main { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr); gap: 20px; align-items: start; }
.self-queue-aside { display: grid; gap: 20px; }
.self-queue-card { border: none; border-radius: 26px; box-shadow: 0 18px 42px rgba(148, 163, 184, 0.12); }
.self-queue-card :deep(.el-card__header) { border-bottom-color: rgba(226, 232, 240, 0.78); }
.self-queue-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.self-queue-card__header--stack { justify-content: flex-start; }
.self-queue-card__header strong { display: block; color: #0f172a; }
.self-queue-card__header > div > div { margin-top: 4px; font-size: 13px; line-height: 1.6; color: #64748b; }
.self-queue-card--form,
.self-queue-card--aside,
.self-queue-card--tone,
.self-queue-card--remind,
.ai-card,
.result-card--status { background: rgba(255, 255, 255, 0.96); }
.self-queue-form-section + .self-queue-form-section { margin-top: 28px; }
.self-queue-section-title { display: flex; gap: 14px; align-items: flex-start; margin-bottom: 18px; }
.self-queue-section-title__index {
  display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 50%;
  background: linear-gradient(135deg, #0f766e, #0ea5e9); color: #fff; font-size: 13px; font-weight: 700; box-shadow: 0 10px 24px rgba(14, 165, 233, 0.26);
}
.self-queue-section-title p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; color: #64748b; }
.mode-switch { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.mode-card {
  padding: 18px; border: 1px solid rgba(148, 163, 184, 0.22); border-radius: 22px; background: linear-gradient(180deg, #fff, #f8fafc);
  text-align: left; cursor: pointer; transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.mode-card strong { display: block; color: #0f172a; font-size: 15px; }
.mode-card span { display: block; margin-top: 8px; font-size: 13px; line-height: 1.65; color: #64748b; }
.mode-card:hover,
.mode-card.is-active { transform: translateY(-1px); border-color: rgba(14, 165, 233, 0.4); box-shadow: 0 18px 32px rgba(14, 165, 233, 0.12); }
.mode-card.is-active { background: linear-gradient(135deg, rgba(20, 184, 166, 0.12), rgba(14, 165, 233, 0.14)); }
.self-queue-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.self-queue-form-grid--single { grid-template-columns: 1fr; }
.mode-tip,
.self-queue-tip,
.complaint-tags__label,
.complaint-tags__hint,
.ai-card__item small { font-size: 13px; line-height: 1.7; color: #64748b; }
.mode-tip { margin-top: 12px; }
.complaint-tags { margin-top: 6px; padding: 18px 18px 16px; border-radius: 20px; background: linear-gradient(135deg, rgba(15, 118, 110, 0.05), rgba(14, 165, 233, 0.05)); border: 1px solid rgba(148, 163, 184, 0.18); }
.complaint-tags__list { display: flex; flex-wrap: wrap; gap: 10px; margin: 12px 0 10px; }
.complaint-tag { padding: 9px 14px; border: none; border-radius: 999px; background: #fff; color: #0f766e; font-size: 13px; cursor: pointer; box-shadow: 0 8px 20px rgba(148, 163, 184, 0.14); }
.complaint-tag:hover { transform: translateY(-1px); background: #ecfeff; }
.self-queue-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 28px; }
.info-list,
.next-steps { margin: 0; padding-left: 18px; display: grid; gap: 12px; color: #334155; line-height: 1.7; }
.suggestion-block { display: grid; gap: 12px; }
.suggestion-block__item { padding: 16px 18px; border-radius: 18px; background: linear-gradient(180deg, #f8fafc, #eff6ff); border: 1px solid rgba(191, 219, 254, 0.6); }
.suggestion-block__item span { display: block; font-size: 12px; letter-spacing: 0.08em; text-transform: uppercase; color: #64748b; }
.suggestion-block__item strong { display: block; margin-top: 8px; color: #0f172a; }
.self-queue-alert { margin-top: -4px; }
.result-dashboard { display: grid; gap: 20px; }
.result-card--status { background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(240, 253, 250, 0.98)); }
.result-status-hero { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.result-status-hero__name { font-size: 30px; line-height: 1.15; font-weight: 700; color: #0f172a; }
.result-status-hero__message { margin-top: 8px; max-width: 760px; color: #475569; line-height: 1.7; }
.result-status-hero__ticket { min-width: 180px; padding: 16px 18px; border-radius: 20px; background: rgba(15, 118, 110, 0.06); border: 1px dashed rgba(15, 118, 110, 0.26); }
.result-status-hero__ticket span { display: block; font-size: 12px; color: #64748b; }
.result-status-hero__ticket strong { display: block; margin-top: 8px; font-size: 28px; letter-spacing: 0.08em; color: #0f172a; }
.result-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-top: 20px; }
.result-metric { padding: 18px 18px 20px; border-radius: 22px; background: linear-gradient(180deg, #f8fafc, #f1f5f9); border: 1px solid rgba(148, 163, 184, 0.14); }
.result-metric--accent { background: linear-gradient(135deg, rgba(20, 184, 166, 0.14), rgba(14, 165, 233, 0.14)); }
.result-metric span { display: block; font-size: 13px; color: #64748b; }
.result-metric strong { display: block; margin-top: 10px; font-size: 30px; line-height: 1.05; color: #0f172a; }
.ai-card { border: 1px solid rgba(15, 118, 110, 0.12); background: linear-gradient(180deg, #f7fdfb, #fff); }
.ai-card__header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ai-card__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-top: 12px; }
.ai-card__item { padding: 16px 20px; border-radius: 18px; background: #fff; border: 1px solid rgba(145, 197, 226, 0.4); }
.ai-card__item span { display: block; font-size: 12px; letter-spacing: 0.16em; text-transform: uppercase; color: #64748b; }
.ai-card__item strong { display: block; margin-top: 6px; font-size: 16px; color: #0f172a; }
.ai-card__item--advice p { margin: 10px 0 0; color: #1e293b; line-height: 1.6; font-size: 14px; }
.ai-card__confidence { font-size: 13px; color: #0f172a; padding: 4px 10px; border-radius: 999px; background: rgba(15, 118, 110, 0.08); }
.ai-card__tag { display: inline-block; margin-top: 6px; padding: 4px 10px; border-radius: 999px; background: rgba(248, 113, 113, 0.12); color: #b91c1c; font-size: 12px; }
.result-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; }
@media (max-width: 1080px) {
  .self-queue-hero,
  .self-queue-main,
  .result-grid { grid-template-columns: 1fr; }
  .result-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .self-queue-page { padding: 18px 14px 42px; }
  .self-queue-hero__content,
  .self-queue-hero__panel { padding: 24px 20px; }
  .self-queue-hero h1 { font-size: 30px; }
  .mode-switch,
  .self-queue-form-grid,
  .result-metrics,
  .result-grid { grid-template-columns: 1fr; }
  .result-status-hero { flex-direction: column; }
  .result-status-hero__ticket { width: 100%; }
}
</style>
