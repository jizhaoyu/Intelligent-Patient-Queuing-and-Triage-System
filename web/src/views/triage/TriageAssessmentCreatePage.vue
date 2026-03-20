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
            <strong>{{ assessment ? '已生成结果' : '待提交评估' }}</strong>
            <small>{{ assessment ? '可继续重新评估并进入详情页。' : '录入完成后提交，系统将立即生成结果。' }}</small>
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
            <strong class="triage-result-stat__value">{{ assessment?.recommendDeptId ?? '-' }}</strong>
          </div>
          <div class="triage-result-stat triage-result-stat--success">
            <span class="triage-result-stat__label">快速通道</span>
            <strong class="triage-result-stat__value">{{ assessment?.fastTrack ? '是' : '否' }}</strong>
          </div>
        </div>
        <div class="triage-result-note">
          {{ assessment ? '评估已完成，请核对结果后继续后续处置。' : '结果将在提交评估后生成。' }}
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
        </div>
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

function buildPayload() {
  return {
    visitId: Number(form.visitId),
    symptomTags: form.symptomTags || undefined,
    bodyTemperature: form.bodyTemperature,
    heartRate: form.heartRate,
    bloodPressure: form.bloodPressure || undefined,
    bloodOxygen: form.bloodOxygen,
    manualAdjustScore: form.manualAdjustScore,
    assessor: form.assessor || authStore.profile?.nickname || authStore.profile?.username || undefined
  }
}

async function handleSubmit() {
  if (!form.visitId) {
    ElMessage.warning('请输入就诊 ID')
    return
  }

  submitting.value = true
  try {
    assessment.value = await createAssessment(buildPayload())
    ElMessage.success('评估提交成功')
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
    ElMessage.success('重新评估成功')
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
