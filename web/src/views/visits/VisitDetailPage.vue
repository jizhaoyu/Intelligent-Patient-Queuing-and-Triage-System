<template>
  <div>
    <PageHeader title="就诊详情" description="查看当前就诊状态与处理流转" />
    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-card v-if="visit">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="就诊号">{{ visit.visitNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ visit.status }}</el-descriptions-item>
            <el-descriptions-item label="患者 ID">{{ visit.patientId }}</el-descriptions-item>
            <el-descriptions-item label="当前科室 ID">{{ visit.currentDeptId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前诊室 ID">{{ visit.currentRoomId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="登记时间">{{ visit.registerTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="到诊时间">{{ visit.arrivalTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主诉" :span="2">{{ visit.chiefComplaint || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="form-actions" style="margin-top: 16px">
            <el-button v-if="visit.status !== 'ARRIVED'" :loading="arriving" @click="handleArrive">登记到诊</el-button>
            <el-button type="primary" @click="goAssessment">去分诊评估</el-button>
          </div>
        </el-card>
        <el-empty v-else description="未找到就诊记录" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { arriveVisit, getVisitById } from '@/api/visit'
import type { Visit } from '@/types/visit'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const arriving = ref(false)
const visit = ref<Visit | null>(null)

function goAssessment() {
  router.push({
    path: '/workstation/triage/assessments/new',
    query: { visitId: route.params.id as string }
  })
}

async function handleArrive() {
  if (!visit.value) {
    return
  }

  arriving.value = true
  try {
    visit.value = await arriveVisit(visit.value.id)
    ElMessage.success('已登记到诊')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '到诊登记失败')
  } finally {
    arriving.value = false
  }
}

async function loadVisit() {
  loading.value = true
  try {
    visit.value = await getVisitById(route.params.id as string)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取就诊详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadVisit)
</script>
