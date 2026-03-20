<template>
  <div>
    <PageHeader title="评估详情" description="查看分诊结果与评估明细" />
    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-card v-if="assessment">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="评估 ID">{{ assessment.id }}</el-descriptions-item>
            <el-descriptions-item label="就诊 ID">{{ assessment.visitId }}</el-descriptions-item>
            <el-descriptions-item label="分诊等级">{{ assessment.triageLevel }}</el-descriptions-item>
            <el-descriptions-item label="推荐科室 ID">{{ assessment.recommendDeptId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="优先级分数">{{ assessment.priorityScore }}</el-descriptions-item>
            <el-descriptions-item label="快速通道">{{ assessment.fastTrack ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="症状标签">{{ assessment.symptomTags || '-' }}</el-descriptions-item>
            <el-descriptions-item label="评估人">{{ assessment.assessor || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生命体征" :span="2">
              体温 {{ assessment.bodyTemperature ?? '-' }} / 心率 {{ assessment.heartRate ?? '-' }} / 血压 {{ assessment.bloodPressure || '-' }} / 血氧 {{ assessment.bloodOxygen ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="评估时间" :span="2">{{ assessment.assessedTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-empty v-else description="未找到评估记录" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getAssessmentById } from '@/api/triage'
import type { TriageAssessment } from '@/types/triage'

const route = useRoute()
const loading = ref(false)
const assessment = ref<TriageAssessment | null>(null)

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
