<template>
  <div class="admin-overview">
    <PageHeader title="医院运营总览" eyebrow="管理后台" description="查看全院或指定科室的真实候诊、叫号和接诊数据。">
      <template #actions>
        <el-select v-model="selectedDeptId" class="dept-select" placeholder="请选择查看范围" :loading="deptOptionsLoading" style="width: 220px">
          <el-option v-for="item in deptSelectOptions" :key="item.id" :label="item.deptName" :value="item.id" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新数据</el-button>
        <el-tag effect="plain">当前范围：{{ scopeLabel }}</el-tag>
        <el-tag effect="plain">岗位 {{ authStore.profile?.roleCode ?? '-' }}</el-tag>
      </template>
    </PageHeader>

    <section class="overview-hero">
      <div class="overview-hero__panel">
        <span class="section-kicker">实时态势</span>
        <h2>{{ headlineText }}</h2>
        <p>{{ focusText }}</p>
      </div>
      <div class="overview-hero__aside">
        <div class="overview-list">
          <div v-for="item in managementSignals" :key="item.label" class="overview-list__item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card metric-card--accent">
        <span class="metric-card__label">当前候诊</span>
        <strong class="metric-card__value">{{ summary?.waitingCount ?? 0 }}</strong>
        <span class="metric-card__hint">排队中的患者数量，实时反映门诊拥堵情况。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">叫号中</span>
        <strong class="metric-card__value">{{ summary?.callingCount ?? 0 }}</strong>
        <span class="metric-card__hint">诊室当前正在接诊或广播中的患者。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">已完成</span>
        <strong class="metric-card__value">{{ summary?.completedCount ?? 0 }}</strong>
        <span class="metric-card__hint">当前统计范围内已完成接诊的患者数量。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">高优先级超时</span>
        <strong class="metric-card__value">{{ summary?.timeoutHighPriorityCount ?? 0 }}</strong>
        <span class="metric-card__hint">超过 30 分钟仍未处理的高优先级患者。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">分诊未入队异常</span>
        <strong class="metric-card__value">{{ summary?.unqueuedTriagedCount ?? 0 }}</strong>
        <span class="metric-card__hint">用于观察已分诊但尚未入队的患者数量。</span>
      </article>
    </section>

    <section class="content-grid">
      <el-card class="panel-card">
        <template #header>实时队列概览</template>
        <el-table v-loading="loading" :data="queueSnapshot">
          <el-table-column prop="ticketNo" label="票号" min-width="150" />
          <el-table-column label="患者姓名" min-width="130">
            <template #default="{ row }">
              <strong>{{ row.patientName || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="所属科室" min-width="130">
            <template #default="{ row }">
              {{ row.deptName || `科室 ${row.deptId}` }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="statusTypeByTicket(row)" effect="plain">{{ formatTicketStatus(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分诊等级" width="120">
            <template #default="{ row }">
              <span class="data-pill">{{ formatTriageLevel(row.triageLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="排位" width="110">
            <template #default="{ row }">
              <span class="queue-rank-chip">{{ row.rank && row.rank > 0 ? row.rank : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预计等待" width="120">
            <template #default="{ row }">
              {{ row.estimatedWaitMinutes ?? '-' }} 分
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="panel-card">
        <template #header>运营建议</template>
        <div class="room-list">
          <div v-for="item in recommendations" :key="item" class="room-card">{{ item }}</div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDashboardSummary } from '@/api/dashboard'
import { getActiveTickets } from '@/api/queue'
import { useDeptScope } from '@/composables/useDeptScope'
import { useAuthStore } from '@/stores/auth'
import type { DeptDashboardSummary, QueueTicket } from '@/types/queue'
import { formatQueueStatus, getQueueStatusTagType } from '@/utils/queueStatus'

const authStore = useAuthStore()
const loading = ref(false)
const deptScopeInitialized = ref(false)
const summary = ref<DeptDashboardSummary | null>(null)
const activeTickets = ref<QueueTicket[]>([])
const {
  deptSelectOptions,
  loading: deptOptionsLoading,
  selectedDeptId,
  scopeLabel,
  loadDeptOptions
} = useDeptScope({
  allowAll: true,
  initialDeptId: authStore.profile?.deptId ?? 0
})
const callingTickets = computed(() => activeTickets.value.filter((ticket) => ticket.status === 'CALLING'))
const waitingTickets = computed(() => activeTickets.value.filter((ticket) => ticket.status === 'WAITING'))
const queueSnapshot = computed(() => activeTickets.value.slice(0, 8))
const leadTicket = computed(() => callingTickets.value[0] || waitingTickets.value[0] || null)

const headlineText = computed(() => {
  const waitingCount = summary.value?.waitingCount ?? 0
  const averageWaitMinutes = summary.value?.averageWaitMinutes ?? 0
  return `${scopeLabel.value}当前候诊 ${waitingCount} 人，平均等待 ${averageWaitMinutes} 分钟。`
})

const focusText = computed(() => {
  if (leadTicket.value?.patientName) {
    return `当前最需要关注的是 ${leadTicket.value.patientName}，状态为${formatTicketStatus(leadTicket.value)}，请结合诊室节奏与现场广播同步跟进。`
  }
  return '当前没有活跃候诊患者，请继续留意新建档、分诊和入队流转。'
})

const managementSignals = computed(() => [
  {
    label: '统计范围',
    value: scopeLabel.value,
    hint: '管理员可切换全院或单科室视角'
  },
  {
    label: '当前叫号',
    value: `${summary.value?.callingCount ?? 0} 人`,
    hint: callingTickets.value[0]?.patientName ? `首位叫号患者：${callingTickets.value[0].patientName}` : '当前没有叫号患者'
  },
  {
    label: '异常待处理',
    value: `${summary.value?.unqueuedTriagedCount ?? 0} 条`,
    hint: (summary.value?.unqueuedTriagedCount ?? 0) > 0 ? '建议优先核查分诊完成后尚未入队的患者记录' : '当前没有已分诊未入队异常'
  },
  {
    label: '下一位患者',
    value: waitingTickets.value[0]?.patientName || '暂无',
    hint: waitingTickets.value[0]?.deptName ? `候诊科室：${waitingTickets.value[0].deptName}` : '当前暂无候诊患者'
  }
])

const recommendations = computed(() => {
  const result: string[] = []
  const waitingCount = summary.value?.waitingCount ?? 0
  const averageWaitMinutes = summary.value?.averageWaitMinutes ?? 0
  const timeoutCount = summary.value?.timeoutHighPriorityCount ?? 0

  if (callingTickets.value.length === 0 && waitingCount > 0) {
    result.push('存在候诊患者但当前没有叫号中的诊室，建议优先核查诊室接诊状态。')
  } else {
    result.push(`当前共有 ${callingTickets.value.length} 名患者处于叫号中，建议同步关注广播与诊室进度。`)
  }

  if (averageWaitMinutes >= 20) {
    result.push('平均等待时间偏高，建议优先检查瓶颈科室、复呼患者与过号回流情况。')
  } else {
    result.push('平均等待时间处于可控区间，可继续保持当前分诊和叫号节奏。')
  }

  if (timeoutCount > 0) {
    result.push(`有 ${timeoutCount} 名高优先级患者超时等待，应立即干预并优先安排接诊。`)
  } else {
    result.push('当前没有高优先级超时患者，可重点关注就诊高峰时段的动态波动。')
  }

  if (waitingTickets.value[0]?.patientName) {
    result.push(`下一位候诊患者为 ${waitingTickets.value[0].patientName}，建议提前提醒患者在候诊区就位。`)
  }

  return result
})

async function loadDashboard() {
  loading.value = true
  try {
    const deptId = selectedDeptId.value && selectedDeptId.value > 0 ? selectedDeptId.value : undefined
    const [dashboardSummary, queueTickets] = await Promise.all([getDashboardSummary(deptId), getActiveTickets(deptId)])
    summary.value = dashboardSummary
    activeTickets.value = queueTickets
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取管理看板数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await loadDeptOptions()
    deptScopeInitialized.value = true
    await loadDashboard()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室列表失败')
  }
})

watch(selectedDeptId, () => {
  if (!deptScopeInitialized.value) {
    return
  }
  void loadDashboard()
})

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatTicketStatus(ticket?: QueueTicket | null) {
  return formatQueueStatus(ticket)
}

function formatStatus(status?: string) {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '过号',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '-'
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
