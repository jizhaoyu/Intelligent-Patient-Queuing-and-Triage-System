<template>
  <div class="queue-ticket-detail-page">
    <PageHeader
      title="排队详情"
      description="查看票号、诊室流转、来源审计与当前排位信息"
    >
      <template #actions>
        <el-button @click="goBack">返回候诊队列</el-button>
        <el-button type="danger" :disabled="!canCancel" :loading="cancelling" @click="handleCancelTicket">
          取消排队
        </el-button>
      </template>
    </PageHeader>

    <el-skeleton :loading="loading" animated>
      <template #default>
        <template v-if="ticket">
          <section class="summary-grid">
            <el-card class="summary-card summary-card--accent" shadow="hover">
              <span>当前状态</span>
              <strong>{{ formatTicketStatus(ticket) }}</strong>
              <el-tag :type="statusTypeByTicket(ticket)" effect="plain">
                {{ formatSourceType(ticket.sourceType) }}
              </el-tag>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <span>当前排位</span>
              <strong>{{ formatRank(rank?.rank ?? ticket.rank) }}</strong>
              <small>前方 {{ rank?.waitingCount ?? ticket.waitingCount ?? 0 }} 人</small>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <span>预计等待</span>
              <strong>{{ formatMinutes(rank?.estimatedWaitMinutes ?? ticket.estimatedWaitMinutes) }}</strong>
              <small>已等待 {{ formatMinutes(ticket.waitedMinutes) }}</small>
            </el-card>
            <el-card class="summary-card" shadow="hover">
              <span>诊室安排</span>
              <strong>{{ formatRoom(ticket.roomName, ticket.doctorName, ticket.roomId) }}</strong>
              <small>{{ ticket.deptName || formatDept(ticket.deptId) }}</small>
            </el-card>
          </section>

          <section class="detail-grid">
            <el-card shadow="hover">
              <template #header>基础信息</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="票号">
                  {{ ticket.ticketNo }}
                </el-descriptions-item>
                <el-descriptions-item label="状态">
                  {{ formatTicketStatus(ticket) }}
                </el-descriptions-item>
                <el-descriptions-item label="患者姓名">
                  {{ ticket.patientName || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="患者编号">
                  {{ ticket.patientNo || ticket.patientId || '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="就诊 ID">
                  {{ ticket.visitId }}
                </el-descriptions-item>
                <el-descriptions-item label="评估 ID">
                  {{ ticket.assessmentId }}
                </el-descriptions-item>
                <el-descriptions-item label="科室">
                  {{ ticket.deptName || formatDept(ticket.deptId) }}
                </el-descriptions-item>
                <el-descriptions-item label="诊室 / 医生">
                  {{ formatRoom(ticket.roomName, ticket.doctorName, ticket.roomId) }}
                </el-descriptions-item>
                <el-descriptions-item label="分诊等级">
                  {{ formatTriageLevel(ticket.triageLevel) }}
                </el-descriptions-item>
                <el-descriptions-item label="优先分">
                  {{ ticket.priorityScore ?? '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="来源">
                  {{ formatSourceType(ticket.sourceType) }}
                </el-descriptions-item>
                <el-descriptions-item label="来源说明">
                  {{ ticket.sourceRemark || '-' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="hover">
              <template #header>时间与修正</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="入队时间">
                  {{ formatDateTime(ticket.enqueueTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="叫号时间">
                  {{ formatDateTime(ticket.callTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="完成时间">
                  {{ formatDateTime(ticket.completeTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="当前排位">
                  {{ formatRank(rank?.rank ?? ticket.rank) }}
                </el-descriptions-item>
                <el-descriptions-item label="候诊人数">
                  {{ rank?.waitingCount ?? ticket.waitingCount ?? 0 }}
                </el-descriptions-item>
                <el-descriptions-item label="最后调整原因">
                  {{ ticket.lastAdjustReason || '-' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </section>

          <el-card shadow="hover">
            <template #header>流转记录</template>
            <el-table v-if="events.length" :data="events" row-key="id">
              <el-table-column prop="ticketNo" label="票号" min-width="160" />
              <el-table-column label="事件类型" width="140">
                <template #default="{ row }">
                  {{ formatEventType(row.eventType) }}
                </template>
              </el-table-column>
              <el-table-column label="状态流转" min-width="200">
                <template #default="{ row }">
                  {{ formatStatusFlow(row) }}
                </template>
              </el-table-column>
              <el-table-column label="来源" width="150">
                <template #default="{ row }">
                  {{ formatSourceType(row.sourceType) }}
                </template>
              </el-table-column>
              <el-table-column prop="sourceRemark" label="来源说明" min-width="180" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="操作人" width="140" />
              <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
              <el-table-column label="时间" min-width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdTime) }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="当前票号暂无事件记录" />
          </el-card>
        </template>

        <el-empty v-else description="未找到对应的排队票号" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { cancelTicket, getQueueEvents, getRank, getTicket } from '@/api/queue'
import type { QueueEventLog, QueueRank, QueueTicket } from '@/types/queue'
import { formatQueueStatus, formatQueueStatusCode, getQueueStatusTagType } from '@/utils/queueStatus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const cancelling = ref(false)
const ticket = ref<QueueTicket | null>(null)
const rank = ref<QueueRank | null>(null)
const events = ref<QueueEventLog[]>([])
let requestId = 0
const canCancel = computed(() => {
  const status = ticket.value?.status
  return !!ticket.value?.ticketNo && status !== 'COMPLETED' && status !== 'CANCELLED'
})

async function loadTicketDetail(ticketNo: string) {
  const currentRequestId = ++requestId
  loading.value = true
  ticket.value = null
  rank.value = null
  events.value = []

  try {
    const ticketData = await getTicket(ticketNo)
    if (currentRequestId !== requestId) {
      return
    }

    ticket.value = ticketData

    const [rankResult, eventResult] = await Promise.allSettled([
      getRank(ticketNo),
      getQueueEvents({ ticketNo })
    ])

    if (currentRequestId !== requestId) {
      return
    }

    rank.value = rankResult.status === 'fulfilled' ? rankResult.value : null
    events.value = eventResult.status === 'fulfilled' ? eventResult.value : []
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }

    ElMessage.error(
      error instanceof Error ? error.message : '获取排队详情失败'
    )
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

function goBack() {
  void router.push('/admin/queues')
}

async function handleCancelTicket() {
  if (!ticket.value?.ticketNo || !canCancel.value) {
    return
  }

  cancelling.value = true
  try {
    await cancelTicket(ticket.value.ticketNo)
    ElMessage.success('已取消当前排队')
    await loadTicketDetail(ticket.value.ticketNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消排队失败')
  } finally {
    cancelling.value = false
  }
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

function formatSourceType(sourceType?: string) {
  const map: Record<string, string> = {
    TRIAGE_AUTO: '分诊自动入队',
    KIOSK: '院内自助机',
    MANUAL_REPAIR: '异常补录'
  }
  return sourceType ? map[sourceType] || sourceType : '未标记'
}

function formatEventType(eventType?: string) {
  const map: Record<string, string> = {
    ENQUEUE: '入队',
    CALL_NEXT: '叫号',
    RECALL: '复呼',
    MISSED: '过号',
    COMPLETE: '完成',
    CANCEL: '取消',
    MANUAL_ADJUST: '人工调整'
  }
  return eventType ? map[eventType] || eventType : '-'
}

function formatStatusFlow(event: QueueEventLog) {
  if (event.fromStatus && event.toStatus) {
    return `${formatRawStatus(event.fromStatus)} -> ${formatRawStatus(event.toStatus)}`
  }
  if (event.toStatus) {
    return `开始 -> ${formatRawStatus(event.toStatus)}`
  }
  return '-'
}

function formatDept(deptId?: number) {
  return deptId ? `科室 ${deptId}` : '-'
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

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatRank(value?: number) {
  if (typeof value !== 'number' || value <= 0) {
    return '-'
  }
  return `#${value}`
}

function formatMinutes(value?: number) {
  if (typeof value !== 'number' || value < 0) {
    return '-'
  }
  return `${value} 分钟`
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

function formatTicketStatus(ticket?: QueueTicket | null) {
  return formatQueueStatus(ticket)
}

function formatRawStatus(status?: string) {
  return formatQueueStatusCode(status)
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

<style scoped>
.queue-ticket-detail-page {
  display: grid;
  gap: 20px;
}

.queue-ticket-detail-page :deep(.el-card) {
  border: none;
  border-radius: 26px;
  overflow: hidden;
  box-shadow: 0 22px 48px rgba(8, 47, 73, 0.08);
}

.queue-ticket-detail-page :deep(.el-card__header) {
  padding: 20px 22px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(248, 252, 253, 0.98), rgba(255, 255, 255, 0.82));
}

.queue-ticket-detail-page :deep(.el-card__body) {
  padding: 22px;
}

.queue-ticket-detail-page :deep(.el-descriptions__body .el-descriptions__table) {
  overflow: hidden;
  border-radius: 20px;
}

.queue-ticket-detail-page :deep(.el-descriptions__label.el-descriptions__cell) {
  min-width: 118px;
  background: rgba(240, 249, 255, 0.88);
  color: var(--muted-color);
  font-weight: 700;
}

.queue-ticket-detail-page :deep(.el-descriptions__content.el-descriptions__cell) {
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-color);
}

.queue-ticket-detail-page :deep(.el-table) {
  border-radius: 22px;
  overflow: hidden;
  background: transparent;
}

.queue-ticket-detail-page :deep(.el-table th.el-table__cell) {
  background: rgba(240, 249, 255, 0.9);
  color: var(--muted-color);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.queue-ticket-detail-page :deep(.el-table__row:hover > td.el-table__cell) {
  background: rgba(34, 211, 238, 0.06);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  position: relative;
  overflow: hidden;
  border: none;
  border-radius: 22px;
}

.summary-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-card::after {
  content: "";
  position: absolute;
  inset: auto -24px -28px auto;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.14), transparent 72%);
}

.summary-card span,
.summary-card small {
  color: var(--muted-color);
}

.summary-card strong {
  font-size: 30px;
  line-height: 1.08;
  color: var(--title-color);
}

.summary-card--accent {
  background: linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(14, 165, 233, 0.14));
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .queue-ticket-detail-page :deep(.el-card__body),
  .queue-ticket-detail-page :deep(.el-card__header) {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>
