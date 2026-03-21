<template>
  <div>
    <PageHeader title="评估详情" description="查看分诊结果与评估明细" />
    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-card v-if="assessment">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="评估编号">{{ assessment.id }}</el-descriptions-item>
            <el-descriptions-item label="就诊编号">{{ assessment.visitId }}</el-descriptions-item>
            <el-descriptions-item label="分诊等级">{{ assessment.triageLevel }}</el-descriptions-item>
            <el-descriptions-item label="推荐科室">{{ assessment.recommendDeptName || assessment.recommendDeptId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="优先级分数">{{ assessment.priorityScore }}</el-descriptions-item>
            <el-descriptions-item label="快速通道">{{ assessment.fastTrack ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="年龄">{{ assessment.age ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ assessment.gender || '-' }}</el-descriptions-item>
            <el-descriptions-item label="症状标签" :span="2">{{ assessment.symptomTags || '-' }}</el-descriptions-item>
            <el-descriptions-item label="特殊人群" :span="2">{{ specialTagSummary }}</el-descriptions-item>
            <el-descriptions-item label="评估人">{{ assessment.assessor || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生命体征">
              体温 {{ assessment.bodyTemperature ?? '-' }} / 心率 {{ assessment.heartRate ?? '-' }} / 血压 {{ assessment.bloodPressure || '-' }} / 血氧 {{ assessment.bloodOxygen ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="评估时间" :span="2">{{ assessment.assessedTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="自动入队状态">{{ assessment.queueCreated ? formatResolvedQueueStatus(assessment.queueStatus) : '未自动入队' }}</el-descriptions-item>
            <el-descriptions-item label="排队票号">{{ assessment.queueTicketNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="排队科室">{{ assessment.queueDeptName || assessment.queueDeptId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="排队诊室">{{ assessment.queueRoomName || assessment.queueRoomId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 建议等级">{{ assessment.aiSuggestedLevel ? `${assessment.aiSuggestedLevel} 级` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 风险等级">{{ assessment.aiRiskLevel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 推荐科室">{{ assessment.aiSuggestedDeptName || assessment.aiSuggestedDeptId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 建议优先分">{{ assessment.aiPriorityScore ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="风险标签" :span="2">{{ aiRiskTagsLabel }}</el-descriptions-item>
            <el-descriptions-item label="AI 建议说明" :span="2">{{ assessment.aiAdvice || '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 置信度">{{ aiConfidenceLabel }}</el-descriptions-item>
            <el-descriptions-item label="AI 建议来源">{{ aiSourceLabel }}</el-descriptions-item>
            <el-descriptions-item label="规则差异" :span="2">{{ assessment.aiRuleDiff || '-' }}</el-descriptions-item>
            <el-descriptions-item label="人工复核">{{ assessment.aiNeedManualReview ? '建议复核' : '无需额外复核' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-empty v-else description="未找到评估记录" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getAssessmentById } from '@/api/triage'
import type { TriageAssessment } from '@/types/triage'
import { formatQueueStatusCode } from '@/utils/queueStatus'

const route = useRoute()
const loading = ref(false)
const assessment = ref<TriageAssessment | null>(null)

const specialTagSummary = computed(() => {
  if (!assessment.value) {
    return '-'
  }
  const tags = [
    assessment.value.elderly ? '老人' : '',
    assessment.value.pregnant ? '孕妇' : '',
    assessment.value.child ? '儿童' : '',
    assessment.value.disabled ? '残障' : '',
    assessment.value.revisit ? '复诊' : ''
  ].filter(Boolean)
  return tags.length ? tags.join('、') : '未标记'
})

const aiRiskTagsLabel = computed(() => {
  const tags = assessment.value?.aiRiskTags
  return tags?.length ? tags.join(' / ') : '-'
})

const aiConfidenceLabel = computed(() => {
  const score = assessment.value?.aiConfidence
  if (typeof score !== 'number') {
    return '-'
  }
  const normalized = score > 1 ? score : score * 100
  return `${Math.round(normalized)}%`
})

const aiSourceLabel = computed(() => {
  if (!assessment.value) {
    return '-'
  }
  const parts = [assessment.value.aiSource, assessment.value.aiModelVersion].filter(Boolean)
  return parts.length ? parts.join(' / ') : '-'
})

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

async function loadAssessment() {
  loading.value = true
  try {
    assessment.value = await getAssessmentById(route.params.id as string)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取评估详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadAssessment)
</script>
