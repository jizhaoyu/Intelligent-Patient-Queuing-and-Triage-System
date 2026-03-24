<template>
  <div class="patient-queue-page">
    <div class="patient-queue-shell">
      <section class="patient-queue-hero">
        <div>
          <div class="patient-queue-hero__eyebrow">院内患者排队查询</div>
          <h1>查询当前排队进度</h1>
          <p>
            适用于院内自助机取号或护士分诊后的结果查询。请输入患者编号与手机号后 4 位，系统会返回当前候诊状态、票号、排位、预计等待时间，以及现在该做什么。
          </p>
        </div>
        <el-tag type="info" round effect="plain">仅支持查询本人当前就诊结果</el-tag>
      </section>

      <el-card shadow="hover" class="patient-queue-card">
        <template #header>
          <div class="patient-queue-card__header">
            <div>
              <strong>查询凭证</strong>
              <div>使用患者编号 + 手机号后 4 位进行校验</div>
            </div>
          </div>
        </template>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent
        >
          <div class="patient-queue-form-grid">
            <el-form-item label="患者编号" prop="patientNo">
              <el-input
                v-model="form.patientNo"
                placeholder="例如 P1234567890"
                clearable
              />
            </el-form-item>
            <el-form-item label="手机号后 4 位" prop="phoneSuffix">
              <el-input
                v-model="form.phoneSuffix"
                maxlength="4"
                placeholder="例如 1234"
                clearable
              />
            </el-form-item>
          </div>

          <div class="patient-queue-actions">
            <el-button type="primary" :loading="loading" @click="handleQuery">
              查询排队进度
            </el-button>
            <span class="patient-queue-tip">
              查询失败时不会提示患者是否存在，请核对信息后重试。
            </span>
          </div>
        </el-form>
      </el-card>

      <el-alert
        v-if="errorMessage"
        class="patient-queue-alert"
        type="error"
        :closable="false"
        :title="errorMessage"
        show-icon
      />

      <template v-if="result">
        <PatientNextStepCard
          v-if="result.nextStep"
          :next-step="result.nextStep"
        />

        <section class="patient-queue-panels">
          <el-card shadow="hover" class="patient-queue-card patient-queue-card--status">
            <template #header>
              <div class="patient-queue-card__header">
                <div>
                  <strong>当前状态</strong>
                  <div>展示本次就诊的状态与排队提示</div>
                </div>
                <el-tag :type="statusTagType(result.queueStatus)">
                  {{ result.queueStatusText || '暂无排队状态' }}
                </el-tag>
              </div>
            </template>

            <div class="patient-queue-summary">
              <div class="patient-queue-name">{{ result.patientName }}</div>
              <div class="patient-queue-message">
                {{ result.queueMessage || '当前暂无排队信息' }}
              </div>
            </div>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="当前就诊状态">
                {{ result.visitStatusText || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="当前排队状态">
                {{ result.queueStatusText || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="票号">
                {{ result.ticketNo || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="分诊等级">
                {{ formatLevel(result.triageLevel) }}
              </el-descriptions-item>
              <el-descriptions-item label="科室">
                {{ result.deptName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="诊室">
                {{ formatRoom(result.roomName, result.doctorName) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="hover" class="patient-queue-card patient-queue-card--progress">
            <template #header>
              <div class="patient-queue-card__header">
                <div>
                  <strong>排队进度</strong>
                  <div>仅供参考，请以现场叫号与工作人员通知为准</div>
                </div>
              </div>
            </template>

            <div v-if="result.ticketNo" class="patient-queue-progress-grid">
              <article class="patient-queue-progress-item patient-queue-progress-item--highlight">
                <span>当前排位</span>
                <strong>{{ formatCount(result.rank) }}</strong>
              </article>
              <article class="patient-queue-progress-item">
                <span>前方人数（当前诊室）</span>
                <strong>{{ formatCount(result.roomWaitingCount ?? result.waitingCount) }}</strong>
              </article>
              <article class="patient-queue-progress-item">
                <span>预计等待（当前诊室）</span>
                <strong>{{ formatMinutes(result.roomEstimatedWaitMinutes ?? result.estimatedWaitMinutes) }}</strong>
              </article>
              <article class="patient-queue-progress-item">
                <span>已等待</span>
                <strong>{{ formatMinutes(result.waitedMinutes) }}</strong>
              </article>
            </div>
            <el-empty
              v-else
              description="当前暂未生成排队票据"
              :image-size="88"
            />
          </el-card>
        </section>
      </template>

      <el-empty
        v-else-if="queried && !loading && !errorMessage"
        class="patient-queue-empty"
        description="当前暂无可展示的排队信息"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { queryPatientQueue } from '@/api/patient-queue'
import PatientNextStepCard from '@/components/patient/PatientNextStepCard.vue'
import type { PatientQueueQueryDTO, PatientQueueView } from '@/types/patient-queue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const queried = ref(false)
const errorMessage = ref('')
const result = ref<PatientQueueView | null>(null)

const form = reactive<PatientQueueQueryDTO>({
  patientNo: '',
  phoneSuffix: ''
})

const rules: FormRules<PatientQueueQueryDTO> = {
  patientNo: [{ required: true, message: '请输入患者编号', trigger: 'blur' }],
  phoneSuffix: [
    { required: true, message: '请输入手机号后 4 位', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '手机号后 4 位应为 4 位数字', trigger: 'blur' }
  ]
}

async function handleQuery() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  loading.value = true
  queried.value = true
  errorMessage.value = ''
  result.value = null

  try {
    result.value = await queryPatientQueue({
      patientNo: form.patientNo?.trim() || '',
      phoneSuffix: form.phoneSuffix.trim()
    })
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '查询排队进度失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function statusTagType(status?: string) {
  switch (status) {
    case 'WAITING':
      return 'warning'
    case 'CALLING':
      return 'success'
    case 'MISSED':
      return 'danger'
    case 'COMPLETED':
      return 'success'
    case 'CANCELLED':
      return 'info'
    default:
      return 'info'
  }
}

function formatLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatRoom(roomName?: string, doctorName?: string) {
  if (!roomName && !doctorName) {
    return '-'
  }
  if (roomName && doctorName) {
    return `${roomName} / ${doctorName}`
  }
  return roomName || doctorName || '-'
}

function formatCount(value?: number) {
  if (typeof value !== 'number' || value < 0) {
    return '-'
  }
  return String(value)
}

function formatMinutes(value?: number) {
  if (typeof value !== 'number' || value < 0) {
    return '-'
  }
  return `${value} 分钟`
}
</script>

<style scoped>
.patient-queue-page {
  min-height: 100vh;
  padding: 32px 20px 56px;
  background:
    radial-gradient(circle at top left, rgba(34, 211, 238, 0.14), transparent 24rem),
    radial-gradient(circle at 84% 12%, rgba(16, 185, 129, 0.12), transparent 24rem),
    linear-gradient(180deg, #f3fcfd 0%, #eef7fb 48%, #f8fbfd 100%);
}

.patient-queue-shell {
  max-width: 1120px;
  margin: 0 auto;
  display: grid;
  gap: 20px;
}

.patient-queue-hero {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 32px;
  border-radius: 32px;
  border: 1px solid rgba(103, 232, 249, 0.16);
  background:
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.24), transparent 14rem),
    linear-gradient(135deg, rgba(8, 47, 73, 0.96), rgba(15, 118, 110, 0.92));
  color: #f8fafc;
  box-shadow: 0 24px 60px rgba(8, 47, 73, 0.18);
}

.patient-queue-hero::after {
  content: "";
  position: absolute;
  inset: auto -56px -80px auto;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.16), transparent 70%);
  pointer-events: none;
}

.patient-queue-hero > * {
  position: relative;
  z-index: 1;
}

.patient-queue-hero__eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  margin-bottom: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(240, 253, 250, 0.74);
}

.patient-queue-hero h1 {
  margin: 0;
  font-size: clamp(32px, 4vw, 42px);
  line-height: 1.12;
  letter-spacing: -0.04em;
}

.patient-queue-hero p {
  margin: 14px 0 0;
  max-width: 760px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(240, 253, 250, 0.9);
}

.patient-queue-card {
  border: none;
  border-radius: 28px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 24px 54px rgba(8, 47, 73, 0.09);
}

.patient-queue-card--status {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 253, 250, 0.96));
}

.patient-queue-card--progress {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 249, 255, 0.96));
}

.patient-queue-card :deep(.el-card__header) {
  padding: 20px 24px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(248, 252, 253, 0.98), rgba(255, 255, 255, 0.78));
}

.patient-queue-card :deep(.el-card__body) {
  display: grid;
  gap: 20px;
  padding: 24px;
}

.patient-queue-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.patient-queue-card :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--title-color);
}

.patient-queue-card :deep(.el-input__wrapper) {
  min-height: 50px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.2);
}

.patient-queue-card :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 4px rgba(34, 211, 238, 0.12),
    inset 0 0 0 1px rgba(8, 145, 178, 0.38);
}

.patient-queue-card :deep(.el-descriptions__body .el-descriptions__table) {
  overflow: hidden;
  border-radius: 22px;
}

.patient-queue-card :deep(.el-descriptions__label.el-descriptions__cell) {
  min-width: 124px;
  background: rgba(240, 249, 255, 0.9);
  color: var(--muted-color);
  font-weight: 700;
}

.patient-queue-card :deep(.el-descriptions__content.el-descriptions__cell) {
  background: rgba(255, 255, 255, 0.86);
  color: var(--text-color);
}

.patient-queue-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.patient-queue-card__header strong {
  display: block;
  font-size: 16px;
  color: var(--title-color);
}

.patient-queue-card__header div:last-child,
.patient-queue-card__header > div > div {
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted-color);
}

.patient-queue-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.patient-queue-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  justify-content: space-between;
}

.patient-queue-tip {
  max-width: 44rem;
  font-size: 13px;
  line-height: 1.7;
  color: var(--muted-color);
}

.patient-queue-alert {
  margin-top: -4px;
  border-radius: 18px;
}

.patient-queue-alert :deep(.el-alert) {
  border-radius: 18px;
  border: 1px solid rgba(248, 113, 113, 0.18);
  background: rgba(254, 242, 242, 0.92);
}

.patient-queue-panels {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.patient-queue-summary {
  display: grid;
  gap: 10px;
  padding: 20px 22px;
  border-radius: 22px;
  background:
    linear-gradient(135deg, rgba(34, 211, 238, 0.1), rgba(16, 185, 129, 0.08));
  border: 1px solid rgba(8, 145, 178, 0.12);
}

.patient-queue-name {
  font-size: clamp(28px, 3vw, 34px);
  line-height: 1.12;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--title-color);
}

.patient-queue-message {
  color: var(--text-color);
  line-height: 1.8;
}

.patient-queue-progress-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.patient-queue-progress-item {
  position: relative;
  overflow: hidden;
  padding: 20px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(239, 246, 255, 0.9));
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.patient-queue-progress-item::after {
  content: "";
  position: absolute;
  inset: auto -24px -28px auto;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.18), transparent 72%);
}

.patient-queue-progress-item span {
  display: block;
  font-size: 13px;
  color: var(--muted-color);
}

.patient-queue-progress-item strong {
  display: block;
  margin-top: 10px;
  font-size: clamp(28px, 3vw, 34px);
  line-height: 1.05;
  color: var(--title-color);
}

.patient-queue-progress-item--highlight {
  background:
    linear-gradient(135deg, rgba(20, 184, 166, 0.16), rgba(14, 165, 233, 0.16)),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 253, 250, 0.94));
  border-color: rgba(8, 145, 178, 0.18);
}

.patient-queue-empty {
  padding: 16px 0 0;
}

@media (max-width: 960px) {
  .patient-queue-panels {
    grid-template-columns: 1fr;
  }

  .patient-queue-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .patient-queue-page {
    padding: 20px 14px 36px;
  }

  .patient-queue-hero {
    padding: 24px 20px;
    flex-direction: column;
  }

  .patient-queue-hero h1 {
    font-size: 28px;
  }

  .patient-queue-card :deep(.el-card__header),
  .patient-queue-card :deep(.el-card__body) {
    padding-left: 18px;
    padding-right: 18px;
  }

  .patient-queue-form-grid,
  .patient-queue-progress-grid {
    grid-template-columns: 1fr;
  }
}
</style>
