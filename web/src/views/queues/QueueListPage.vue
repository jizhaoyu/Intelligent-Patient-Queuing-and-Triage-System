<template>
  <div>
    <PageHeader title="候诊队列" description="查看候诊列表、排位与状态" />

    <el-card class="mb-16">
      <div class="toolbar">
        <el-input-number v-model="deptId" :min="1" controls-position="right" placeholder="科室 ID" />
        <el-button type="primary" :loading="loading" @click="loadQueue">查询候诊</el-button>
      </div>
    </el-card>

    <div class="content-grid">
      <el-card>
        <template #header>候诊列表</template>
        <el-table v-loading="loading" :data="queueList" @row-click="handleSelectTicket">
          <el-table-column prop="ticketNo" label="票号" />
          <el-table-column prop="patientId" label="患者 ID" />
          <el-table-column prop="triageLevel" label="等级" />
          <el-table-column prop="priorityScore" label="优先分" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="rank" label="排位" />
        </el-table>
      </el-card>

      <el-card>
        <template #header>票号详情</template>
        <el-descriptions v-if="ticketDetail" :column="1" border>
          <el-descriptions-item label="票号">{{ ticketDetail.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ ticketDetail.status }}</el-descriptions-item>
          <el-descriptions-item label="患者 ID">{{ ticketDetail.patientId }}</el-descriptions-item>
          <el-descriptions-item label="就诊 ID">{{ ticketDetail.visitId }}</el-descriptions-item>
          <el-descriptions-item label="排位 / 候诊人数">{{ rankInfo?.rank ?? ticketDetail.rank ?? '-' }} / {{ rankInfo?.waitingCount ?? ticketDetail.waitingCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="预计等待">{{ rankInfo?.estimatedWaitMinutes ?? ticketDetail.estimatedWaitMinutes ?? '-' }} 分钟</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="点击左侧票据查看详情" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDeptWaiting, getRank, getTicket } from '@/api/queue'
import { useAuthStore } from '@/stores/auth'
import type { QueueRank, QueueTicket } from '@/types/queue'

const authStore = useAuthStore()
const loading = ref(false)
const deptId = ref(authStore.profile?.deptId || 1)
const queueList = ref<QueueTicket[]>([])
const ticketDetail = ref<QueueTicket | null>(null)
const rankInfo = ref<QueueRank | null>(null)

async function loadQueue() {
  loading.value = true
  try {
    const summary = await getDeptWaiting(deptId.value)
    queueList.value = summary.waitingTickets || []
    ticketDetail.value = null
    rankInfo.value = null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取候诊队列失败')
  } finally {
    loading.value = false
  }
}

async function handleSelectTicket(row: QueueTicket) {
  try {
    const [ticket, rank] = await Promise.all([getTicket(row.ticketNo), getRank(row.ticketNo)])
    ticketDetail.value = ticket
    rankInfo.value = rank
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取票据详情失败')
  }
}

onMounted(loadQueue)
</script>
