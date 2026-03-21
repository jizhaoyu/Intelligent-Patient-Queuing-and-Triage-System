<template>
  <div class="patient-queue-page">
    <div class="patient-queue-shell">
      <section class="patient-queue-hero">
        <div>
          <div class="patient-queue-hero__eyebrow">院内患者排队查询</div>
          <h1>查询当前排队进度</h1>
          <p>
            适用于院内自助机取号或护士分诊后的结果查询。请输入患者编号与手机号后 4 位，查看当前候诊状态、票号、排位与预计等待时间。
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
                <span>前方人数</span>
                <strong>{{ formatCount(result.waitingCount) }}</strong>
              </article>
              <article class="patient-queue-progress-item">
                <span>预计等待</span>
                <strong>{{ formatMinutes(result.estimatedWaitMinutes) }}</strong>
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
  padding: 32px 20px 48px;
}

.patient-queue-shell {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.patient-queue-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 32px;
  border-radius: 28px;
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.92), rgba(14, 116, 144, 0.92));
  color: #f8fafc;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.14);
}

.patient-queue-hero__eyebrow {
  margin-bottom: 10px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(240, 253, 250, 0.74);
}

.patient-queue-hero h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
}

.patient-queue-hero p {
  margin: 12px 0 0;
  max-width: 720px;
  color: rgba(240, 253, 250, 0.9);
}

.patient-queue-card {
  border: none;
  border-radius: 24px;
}

.patient-queue-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.patient-queue-card__header strong {
  display: block;
  color: #0f172a;
}

.patient-queue-card__header div:last-child,
.patient-queue-card__header > div > div {
  font-size: 13px;
  color: #64748b;
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
}

.patient-queue-tip {
  font-size: 13px;
  color: #64748b;
}

.patient-queue-alert {
  margin-top: -4px;
}

.patient-queue-panels {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}

.patient-queue-summary {
  margin-bottom: 18px;
}

.patient-queue-name {
  font-size: 28px;
  line-height: 1.2;
  font-weight: 700;
  color: #0f172a;
}

.patient-queue-message {
  margin-top: 8px;
  color: #475569;
}

.patient-queue-progress-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.patient-queue-progress-item {
  padding: 20px;
  border-radius: 20px;
  background: linear-gradient(180deg, #f8fafc, #eef6f4);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.patient-queue-progress-item span {
  display: block;
  font-size: 13px;
  color: #64748b;
}

.patient-queue-progress-item strong {
  display: block;
  margin-top: 10px;
  font-size: 30px;
  line-height: 1.1;
  color: #0f172a;
}

.patient-queue-progress-item--highlight {
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.14), rgba(14, 165, 233, 0.14));
}

.patient-queue-empty {
  padding: 16px 0 0;
}

@media (max-width: 960px) {
  .patient-queue-panels {
    grid-template-columns: 1fr;
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

  .patient-queue-form-grid,
  .patient-queue-progress-grid {
    grid-template-columns: 1fr;
  }
}
</style>
