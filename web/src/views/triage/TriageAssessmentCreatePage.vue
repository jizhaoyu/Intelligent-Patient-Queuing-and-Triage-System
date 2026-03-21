<template>
  <div class="triage-workspace">
    <PageHeader title="分诊评估" eyebrow="护士工作台" description="先确认就诊信息，再录入生命体征和症状标签，系统将生成分诊建议。" />

    <section class="overview-hero triage-overview">
      <div class="overview-hero__panel">
        <span class="section-kicker">评估流程</span>
        <h2>先确认患者，再完成评估录入与结果核对。</h2>
        <p>优先关注就诊记录、生命体征和主诉标签，提交后系统会根据规则计算分诊等级、推荐科室和优先分。</p>
      </div>
      <div class="overview-hero__aside">
        <div class="overview-list">
          <div class="overview-list__item">
            <span>当前就诊</span>
            <strong>{{ form.visitId || '待录入' }}</strong>
            <small>请输入就诊记录编号后开始分诊评估。</small>
          </div>
          <div class="overview-list__item">
            <span>评估人员</span>
            <strong>{{ form.assessor || '待确认' }}</strong>
            <small>默认取当前登录账号昵称，可按现场实际情况调整。</small>
          </div>
          <div class="overview-list__item">
            <span>当前状态</span>
            <strong>{{ assessment ? '已生成结果并处理入队' : '待提交评估' }}</strong>
            <small>{{ assessment ? queueSummaryText : '录入完成后提交，系统将立即生成结果。' }}</small>
          </div>
        </div>
      </div>
    </section>

    <div class="triage-grid">
      <el-card>
        <template #header>评估录入</template>
        <el-form label-position="top">
          <div class="triage-form-grid">
            <el-form-item label="就诊记录">
              <el-input v-model="form.visitId" placeholder="请输入就诊记录编号" />
            </el-form-item>
            <el-form-item label="评估人员">
              <el-input v-model="form.assessor" placeholder="默认使用当前账号昵称" />
            </el-form-item>
          </div>

          <el-form-item label="症状标签">
            <el-input v-model="form.symptomTags" placeholder="请输入症状标签，使用逗号分隔" />
          </el-form-item>

          <div class="triage-field-grid">
            <el-form-item label="体温（℃）">
              <el-input-number v-model="form.bodyTemperature" :precision="1" :min="30" :max="45" style="width: 100%" />
            </el-form-item>
            <el-form-item label="心率（次/分）">
              <el-input-number v-model="form.heartRate" :min="0" :max="240" style="width: 100%" />
            </el-form-item>
            <el-form-item label="血压">
              <el-input v-model="form.bloodPressure" placeholder="例如 120/80" />
            </el-form-item>
            <el-form-item label="血氧（%）">
              <el-input-number v-model="form.bloodOxygen" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
            <el-form-item label="年龄">
              <el-input-number v-model="form.age" :min="0" :max="150" style="width: 100%" />
            </el-form-item>
            <el-form-item label="性别">
              <el-select v-model="form.gender" placeholder="请选择性别" clearable style="width: 100%">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
          </div>

          <div class="triage-field-grid">
            <el-form-item label="特殊人群">
              <el-checkbox v-model="form.elderly">老人</el-checkbox>
            </el-form-item>
            <el-form-item label=" ">
              <el-checkbox v-model="form.pregnant">孕妇</el-checkbox>
            </el-form-item>
            <el-form-item label=" ">
              <el-checkbox v-model="form.child">儿童</el-checkbox>
            </el-form-item>
            <el-form-item label=" ">
              <el-checkbox v-model="form.disabled">残障</el-checkbox>
            </el-form-item>
            <el-form-item label=" ">
              <el-checkbox v-model="form.revisit">复诊</el-checkbox>
            </el-form-item>
          </div>

          <el-form-item label="人工加权分">
            <el-input-number v-model="form.manualAdjustScore" :min="-100" :max="100" style="width: 100%" />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="handleSubmit">提交评估</el-button>
            <el-button :disabled="!assessment" :loading="reassessing" @click="handleReassess">重新评估</el-button>
          </div>
        </el-form>
      </el-card>

      <el-card class="triage-result-card">
        <template #header>评估结果</template>
        <div class="triage-result-grid">
          <div class="triage-result-stat triage-result-stat--accent">
            <span class="triage-result-stat__label">分诊等级</span>
            <strong class="triage-result-stat__value">{{ assessment?.triageLevel ?? '-' }}</strong>
          </div>
          <div class="triage-result-stat">
            <span class="triage-result-stat__label">优先分</span>
            <strong class="triage-result-stat__value">{{ assessment?.priorityScore ?? '-' }}</strong>
          </div>
          <div class="triage-result-stat">
            <span class="triage-result-stat__label">建议科室</span>
            <strong class="triage-result-stat__value">{{ assessment?.recommendDeptName || assessment?.recommendDeptId || '-' }}</strong>
          </div>
          <div class="triage-result-stat triage-result-stat--success">
            <span class="triage-result-stat__label">快速通道</span>
            <strong class="triage-result-stat__value">{{ assessment?.fastTrack ? '是' : '否' }}</strong>
          </div>
        </div>
        <div class="triage-result-note">
          {{ assessment ? queueSummaryText : '结果将在提交评估后生成。' }}
        </div>
        <div class="detail-list">
          <div class="detail-list__item">
            <span>就诊记录</span>
            <strong>{{ form.visitId || '待录入' }}</strong>
            <small>确认就诊记录无误后再提交，避免关联错误患者。</small>
          </div>
          <div class="detail-list__item">
            <span>症状标签</span>
            <strong>{{ form.symptomTags || '未录入' }}</strong>
            <small>建议使用现场通用术语，便于后续复核与溯源。</small>
          </div>
          <div class="detail-list__item">
            <span>生命体征提示</span>
            <strong>{{ vitalSignSummary }}</strong>
            <small>体温、心率、血压和血氧可帮助系统判断分诊优先级。</small>
          </div>
          <div class="detail-list__item">
            <span>人群标记</span>
            <strong>{{ specialTagSummary }}</strong>
            <small>年龄、性别和特殊人群信息会参与分诊记录留痕与优先分计算。</small>
          </div>
          <div class="detail-list__item">
            <span>自动入队</span>
            <strong>{{ assessment ? queueResultText : '待生成' }}</strong>
            <small>{{ assessment ? queueLocationText : '提交评估后系统将自动尝试入队。' }}</small>
          </div>
        </div>
      </el-card>

      <el-card class="triage-ai-card">
        <template #header>
          <div class="triage-ai-card__header">
            <strong>AI 分诊参考</strong>
            <span class="triage-ai-card__confidence">{{ aiConfidenceLabel }}</span>
          </div>
        </template>
        <div class="triage-ai-grid">
          <div class="triage-ai-grid__item">
            <span>AI 建议等级</span>
            <strong>{{ aiSuggestedLevelLabel }}</strong>
          </div>
          <div class="triage-ai-grid__item">
            <span>AI 推荐科室</span>
            <strong>{{ aiSuggestedDeptLabel }}</strong>
          </div>
          <div class="triage-ai-grid__item">
            <span>风险标签</span>
            <strong>{{ aiRiskTagsLabel }}</strong>
            <small class="triage-ai-card__tag">{{ aiReviewHint }}</small>
          </div>
          <div class="triage-ai-grid__item">
            <span>与规则差异</span>
            <strong>{{ aiRuleDiffLabel }}</strong>
          </div>
        </div>
        <p class="triage-ai-card__advice">AI 建议：{{ assessment?.aiAdvice || '暂无说明' }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createAssessment, reassess } from '@/api/triage'
import { useAuthStore } from '@/stores/auth'
import type { TriageAssessment } from '@/types/triage'
import { formatQueueStatusCode } from '@/utils/queueStatus'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const reassessing = ref(false)
const assessment = ref<TriageAssessment | null>(null)
const form = reactive({
  visitId: (route.query.visitId as string) || '',
  symptomTags: '',
  bodyTemperature: undefined as number | undefined,
  heartRate: undefined as number | undefined,
  bloodPressure: '',
  bloodOxygen: undefined as number | undefined,
  age: undefined as number | undefined,
  gender: '',
  elderly: false,
  pregnant: false,
  child: false,
  disabled: false,
  revisit: false,
  manualAdjustScore: 0,
  assessor: ''
})

const vitalSignSummary = computed(() => {
  const parts = [
    form.bodyTemperature !== undefined ? `体温 ${form.bodyTemperature}℃` : '体温未录入',
    form.heartRate !== undefined ? `心率 ${form.heartRate} 次/分` : '心率未录入',
    form.bloodPressure ? `血压 ${form.bloodPressure}` : '血压未录入',
    form.bloodOxygen !== undefined ? `血氧 ${form.bloodOxygen}%` : '血氧未录入'
  ]
  return parts.join(' / ')
})

const aiSuggestedLevelLabel = computed(() => {
  if (!assessment.value?.aiSuggestedLevel) {
    return '暂无'
  }
  return `${assessment.value.aiSuggestedLevel} 级`
})

const aiSuggestedDeptLabel = computed(() => {
  if (assessment.value?.aiSuggestedDeptName) {
    return assessment.value.aiSuggestedDeptName
  }
  if (assessment.value?.aiSuggestedDeptId) {
    return `科室 ${assessment.value.aiSuggestedDeptId}`
  }
  return '暂无'
})

const aiRiskTagsLabel = computed(() => {
  const tags = assessment.value?.aiRiskTags
  if (!tags?.length) {
    return '暂无'
  }
  return tags.join(' / ')
})

const aiRuleDiffLabel = computed(() => assessment.value?.aiRuleDiff || '暂无差异')

const aiConfidenceLabel = computed(() => {
  const score = assessment.value?.aiConfidence
  if (typeof score !== 'number') {
    return '置信度未知'
  }
  const normalized = score > 1 ? score : score * 100
  return `${Math.round(normalized)}% 置信度`
})

const aiReviewHint = computed(() => (assessment.value?.aiNeedManualReview ? '建议人工复核' : 'AI 建议可参考'))

const specialTagSummary = computed(() => {
  const tags = [
    form.elderly ? '老人' : '',
    form.pregnant ? '孕妇' : '',
    form.child ? '儿童' : '',
    form.disabled ? '残障' : '',
    form.revisit ? '复诊' : ''
  ].filter(Boolean)
  const profile = [form.age !== undefined ? `${form.age} 岁` : '', form.gender || ''].filter(Boolean).join(' / ')
  return [profile, tags.length ? tags.join('、') : '未标记特殊人群'].filter(Boolean).join(' / ')
})

const queueResultText = computed(() => {
  if (!assessment.value) {
    return '待生成'
  }
  if (!assessment.value.queueCreated) {
    return '未自动入队'
  }
  return `已入队 · ${formatResolvedQueueStatus(assessment.value.queueStatus)}`
})

const queueLocationText = computed(() => {
  if (!assessment.value?.queueCreated) {
    return '请根据提示检查入队条件或联系管理员处理。'
  }
  const deptText = assessment.value.queueDeptName || `科室 ${assessment.value.queueDeptId ?? '-'}`
  const roomText = assessment.value.queueRoomName || (assessment.value.queueRoomId ? `诊室 ${assessment.value.queueRoomId}` : '待分配诊室')
  return `票号 ${assessment.value.queueTicketNo || '-'}，${deptText}，${roomText}`
})

const queueSummaryText = computed(() => {
  if (!assessment.value) {
    return '录入完成后提交，系统将立即生成结果。'
  }
  if (!assessment.value.queueCreated) {
    return '评估已完成，但自动入队未成功，请处理异常后继续。'
  }
  return `评估已完成，系统已自动入队，票号 ${assessment.value.queueTicketNo || '-'}。`
})

function buildPayload() {
  return {
    visitId: Number(form.visitId),
    symptomTags: form.symptomTags || undefined,
    bodyTemperature: form.bodyTemperature,
    heartRate: form.heartRate,
    bloodPressure: form.bloodPressure || undefined,
    bloodOxygen: form.bloodOxygen,
    age: form.age,
    gender: form.gender || undefined,
    elderly: form.elderly,
    pregnant: form.pregnant,
    child: form.child,
    disabled: form.disabled,
    revisit: form.revisit,
    manualAdjustScore: form.manualAdjustScore,
    assessor: form.assessor || authStore.profile?.nickname || authStore.profile?.username || undefined
  }
}

function formatResolvedQueueStatus(status?: string) {
  return formatQueueStatusCode(status)
}

function formatQueueStatus(status?: string) {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '过号',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '-'
}

async function handleSubmit() {
  if (!form.visitId) {
    ElMessage.warning('请输入就诊 ID')
    return
  }

  submitting.value = true
  try {
    assessment.value = await createAssessment(buildPayload())
    ElMessage.success(assessment.value.queueCreated ? '评估提交成功，已自动入队' : '评估提交成功，但自动入队未完成')
    await router.push(`/workstation/triage/assessments/${assessment.value.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交评估失败')
  } finally {
    submitting.value = false
  }
}

async function handleReassess() {
  if (!assessment.value) {
    return
  }

  reassessing.value = true
  try {
    assessment.value = await reassess(assessment.value.id, buildPayload())
    ElMessage.success(assessment.value.queueCreated ? '重新评估成功，排队信息已同步' : '重新评估成功，但自动入队未完成')
    await router.push(`/workstation/triage/assessments/${assessment.value.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新评估失败')
  } finally {
    reassessing.value = false
  }
}


onMounted(() => {
  form.assessor = authStore.profile?.nickname || authStore.profile?.username || ''
})
</script>

<style scoped>
.triage-ai-card {
  border-radius: 22px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  background: linear-gradient(180deg, #f7fdfb, #ffffff);
  box-shadow: 0 14px 30px rgba(15, 118, 110, 0.08);
}

.triage-ai-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.triage-ai-card__confidence {
  font-size: 12px;
  color: #0f172a;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
}

.triage-ai-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.triage-ai-grid__item {
  padding: 14px 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid rgba(15, 118, 110, 0.12);
}

.triage-ai-grid__item span {
  display: block;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--muted-color);
}

.triage-ai-grid__item strong {
  margin-top: 6px;
  display: block;
  font-size: 16px;
  color: #0f172a;
}

.triage-ai-grid__diff {
  font-size: 14px;
}

.triage-ai-card__tag {
  display: inline-flex;
  margin-top: 4px;
  font-size: 12px;
  color: #f97316;
}

.triage-ai-card__advice {
  margin-top: 12px;
  font-size: 14px;
  color: #1e293b;
}
</style>
