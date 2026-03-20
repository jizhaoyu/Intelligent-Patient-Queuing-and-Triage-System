<template>
  <div>
    <PageHeader title="排队详情" description="查看票号排位与流转信息" />
    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-card v-if="ticket">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="票号">{{ ticket.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ ticket.status }}</el-descriptions-item>
            <el-descriptions-item label="患者 ID">{{ ticket.patientId }}</el-descriptions-item>
            <el-descriptions-item label="就诊 ID">{{ ticket.visitId }}</el-descriptions-item>
            <el-descriptions-item label="分诊等级">{{ ticket.triageLevel }}</el-descriptions-item>
            <el-descriptions-item label="优先分">{{ ticket.priorityScore }}</el-descriptions-item>
            <el-descriptions-item label="当前排位">{{ rank?.rank ?? ticket.rank ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="候诊人数">{{ rank?.waitingCount ?? ticket.waitingCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="预计等待">{{ rank?.estimatedWaitMinutes ?? ticket.estimatedWaitMinutes ?? '-' }} 分钟</el-descriptions-item>
            <el-descriptions-item label="入队时间">{{ ticket.enqueueTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-empty v-else description="未找到票据信息" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getRank, getTicket } from '@/api/queue'
import type { QueueRank, QueueTicket } from '@/types/queue'

const route = useRoute()
const loading = ref(false)
const ticket = ref<QueueTicket | null>(null)
const rank = ref<QueueRank | null>(null)
let requestId = 0

async function loadTicketDetail(ticketNo: string) {
  const currentRequestId = ++requestId
  loading.value = true
  ticket.value = null
  rank.value = null

  try {
    const [ticketData, rankData] = await Promise.all([getTicket(ticketNo), getRank(ticketNo)])

    if (currentRequestId !== requestId) {
      return
    }

    ticket.value = ticketData
    rank.value = rankData
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }

    ElMessage.error(error instanceof Error ? error.message : '获取排队详情失败')
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

watch(
  () => route.params.ticketNo,
  (ticketNo) => {
    if (typeof ticketNo === 'string' && ticketNo) {
      void loadTicketDetail(ticketNo)
    }
  },
  { immediate: true }
)
</script>
