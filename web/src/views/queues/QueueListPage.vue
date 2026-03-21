<template>
  <div>
    <PageHeader
      title="候诊队列"
      description="查看全院或单科室的实时候诊、叫号状态与患者信息"
    />

    <el-card class="mb-16">
      <div class="toolbar">
        <el-select
          v-model="deptId"
          class="dept-select"
          placeholder="请选择查看范围"
          :loading="deptOptionsLoading"
          style="width: 220px"
        >
          <el-option
            v-for="item in deptSelectOptions"
            :key="item.id"
            :label="item.deptName"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadQueue">
          查询候诊
        </el-button>
        <el-tag effect="plain">当前范围：{{ scopeLabel }}</el-tag>
      </div>
    </el-card>

    <div class="content-grid">
      <el-card>
        <template #header>实时队列</template>
        <el-table
          v-loading="loading"
          :data="queueList"
          @row-click="handleSelectTicket"
        >
          <el-table-column prop="ticketNo" label="票号" min-width="160" />
          <el-table-column label="患者姓名" min-width="140">
            <template #default="{ row }">
              <strong>{{ row.patientName || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="所属科室" min-width="130">
            <template #default="{ row }">
              {{ row.deptName || `科室 ${row.deptId}` }}
            </template>
          </el-table-column>
          <el-table-column label="来源" width="160">
            <template #default="{ row }">
              <el-tag effect="plain">{{ formatSourceType(row.sourceType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分诊等级" width="120">
            <template #default="{ row }">
              <span class="data-pill">
                {{ formatTriageLevel(row.triageLevel) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="当前状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusTypeByTicket(row)" effect="plain">
                {{ formatTicketStatus(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="排位" width="110">
            <template #default="{ row }">
              {{ row.rank && row.rank > 0 ? `#${row.rank}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="已等待" width="120">
            <template #default="{ row }">
              {{ row.waitedMinutes ?? '-' }} 分钟
            </template>
          </el-table-column>
          <el-table-column label="预计等待" width="120">
            <template #default="{ row }">
              {{ row.estimatedWaitMinutes ?? '-' }} 分钟
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card>
        <template #header>票号详情</template>
        <el-descriptions v-if="ticketDetail" :column="1" border>
          <el-descriptions-item label="票号">
            {{ ticketDetail.ticketNo }}
          </el-descriptions-item>
          <el-descriptions-item label="患者姓名">
            {{ ticketDetail.patientName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="所属科室">
            {{ ticketDetail.deptName || `科室 ${ticketDetail.deptId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="诊室 / 医生">
            {{ formatRoom(ticketDetail.roomName, ticketDetail.doctorName, ticketDetail.roomId) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ formatTicketStatus(ticketDetail) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源">
            {{ formatSourceType(ticketDetail.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源说明" :span="2">
            {{ ticketDetail.sourceRemark || ticketDetail.lastAdjustReason || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="患者编号">
            {{ ticketDetail.patientNo || ticketDetail.patientId }}
          </el-descriptions-item>
          <el-descriptions-item label="就诊号">
            {{ ticketDetail.visitId }}
          </el-descriptions-item>
          <el-descriptions-item label="排位 / 候诊人数">
            {{ rankInfo?.rank ?? ticketDetail.rank ?? '-' }} /
            {{ rankInfo?.waitingCount ?? ticketDetail.waitingCount ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="预计等待">
            {{ rankInfo?.estimatedWaitMinutes ?? ticketDetail.estimatedWaitMinutes ?? '-' }}
            分钟
          </el-descriptions-item>
          <el-descriptions-item label="已等待">
            {{ ticketDetail.waitedMinutes ?? '-' }} 分钟
          </el-descriptions-item>
          <el-descriptions-item label="入队时间">
            {{ ticketDetail.enqueueTime || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty
          v-else
          description="点击左侧票号查看详情"
        />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getActiveTickets, getRank, getTicket } from '@/api/queue'
import { useDeptScope } from '@/composables/useDeptScope'
import { useAuthStore } from '@/stores/auth'
import type { QueueRank, QueueTicket } from '@/types/queue'
import { formatQueueStatus, getQueueStatusTagType } from '@/utils/queueStatus'

const authStore = useAuthStore()
const loading = ref(false)
const deptScopeInitialized = ref(false)
const queueList = ref<QueueTicket[]>([])
const ticketDetail = ref<QueueTicket | null>(null)
const rankInfo = ref<QueueRank | null>(null)
const {
  deptSelectOptions,
  loading: deptOptionsLoading,
  selectedDeptId: deptId,
  scopeLabel,
  loadDeptOptions
} = useDeptScope({
  allowAll: true,
  initialDeptId: authStore.profile?.deptId ?? 0
})

async function loadQueue() {
  loading.value = true
  try {
    const currentDeptId = deptId.value
    queueList.value = await getActiveTickets(
      currentDeptId && currentDeptId > 0 ? currentDeptId : undefined
    )
    ticketDetail.value = null
    rankInfo.value = null
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : '获取候诊队列失败'
    )
  } finally {
    loading.value = false
  }
}

async function handleSelectTicket(row: QueueTicket) {
  try {
    const [ticket, rank] = await Promise.all([
      getTicket(row.ticketNo),
      getRank(row.ticketNo)
    ])
    ticketDetail.value = ticket
    rankInfo.value = rank
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : '获取票据详情失败'
    )
  }
}

onMounted(async () => {
  try {
    await loadDeptOptions()
    deptScopeInitialized.value = true
    await loadQueue()
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : '获取科室列表失败'
    )
  }
})

watch(deptId, () => {
  if (!deptScopeInitialized.value) {
    return
  }
  ticketDetail.value = null
  rankInfo.value = null
  void loadQueue()
})

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatStatus(status?: string) {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '已过号',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '-'
}

function formatTicketStatus(ticket?: QueueTicket | null) {
  return formatQueueStatus(ticket)
}

function formatSourceType(sourceType?: string) {
  const map: Record<string, string> = {
    TRIAGE_AUTO: '分诊自动入队',
    KIOSK: '院内自助机',
    MANUAL_REPAIR: '异常补录'
  }
  return sourceType ? map[sourceType] || sourceType : '未标记'
}

function formatRoom(roomName?: string, doctorName?: string, roomId?: number) {
  if (roomName && doctorName) {
    return `${roomName} / ${doctorName}`
  }
  if (roomName) {
    return roomName
  }
  if (roomId) {
    return `诊室 ${roomId}`
  }
  return '-'
}

function statusType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'CALLING':
      return 'success'
    case 'WAITING':
      return 'warning'
    case 'MISSED':
      return 'danger'
    default:
      return 'info'
  }
}

function statusTypeByTicket(ticket?: QueueTicket | null): 'success' | 'warning' | 'info' | 'danger' {
  return getQueueStatusTagType(ticket)
}
</script>
