<template>
  <div class="admin-overview">
    <PageHeader title="医院运营总览" eyebrow="管理后台" description="聚焦当前科室候诊压力、接诊节奏与高优先级处置状态。">
      <template #actions>
        <el-tag effect="plain">所属科室 {{ authStore.profile?.deptId ?? '-' }}</el-tag>
        <el-tag effect="plain">岗位 {{ authStore.profile?.roleCode ?? '-' }}</el-tag>
      </template>
    </PageHeader>

    <section class="overview-hero">
      <div class="overview-hero__panel">
        <span class="section-kicker">实时态势</span>
        <h2>当前候诊 {{ summary?.waitingCount ?? 0 }} 人，平均等待 {{ summary?.averageWaitMinutes ?? 0 }} 分钟。</h2>
        <p>当高优先级超时数量上升时，优先检查分诊窗口与诊室叫号节奏，避免重点患者滞留。</p>
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
        <span class="metric-card__hint">建议保持分诊窗口与诊室叫号节奏同步。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">叫号中</span>
        <strong class="metric-card__value">{{ summary?.callingCount ?? 0 }}</strong>
        <span class="metric-card__hint">实时反映诊室接诊吞吐与现场疏导压力。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">已完成</span>
        <strong class="metric-card__value">{{ summary?.completedCount ?? 0 }}</strong>
        <span class="metric-card__hint">可结合时段统计评估接诊效率与波峰变化。</span>
      </article>
      <article class="metric-card">
        <span class="metric-card__label">高优先级超时</span>
        <strong class="metric-card__value">{{ summary?.timeoutHighPriorityCount ?? 0 }}</strong>
        <span class="metric-card__hint">此指标异常时需优先干预现场流程与资源分配。</span>
      </article>
    </section>

    <section class="content-grid">
      <el-card class="panel-card">
        <template #header>候诊队列概览</template>
        <el-table v-loading="loading" :data="waitingTickets">
          <el-table-column prop="ticketNo" label="票号" min-width="140" />
          <el-table-column label="分诊等级" width="140">
            <template #default="{ row }">
              <span class="data-pill">{{ formatTriageLevel(row.triageLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="plain">{{ formatStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="排位" width="120">
            <template #default="{ row }">
              <span class="queue-rank-chip">{{ row.rank ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="等待人数" width="140">
            <template #default="{ row }">
              {{ row.waitingCount ?? '-' }}
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDeptSummary } from '@/api/dashboard'
import { getDeptWaiting } from '@/api/queue'
import { useAuthStore } from '@/stores/auth'
import type { DeptDashboardSummary, QueueTicket } from '@/types/queue'

const authStore = useAuthStore()
const loading = ref(false)
const summary = ref<DeptDashboardSummary | null>(null)
const waitingTickets = ref<QueueTicket[]>([])

const managementSignals = computed(() => [
  {
    label: '监控科室',
    value: `${authStore.profile?.deptId ?? '-'}`,
    hint: '当前账号所属科室范围'
  },
  {
    label: '监控诊室',
    value: authStore.profile?.roomId ? `${authStore.profile.roomId}` : '未绑定',
    hint: '用于联动定位具体接诊点位'
  },
  {
    label: '高优先级超时',
    value: `${summary.value?.timeoutHighPriorityCount ?? 0} 人`,
    hint: '重点关注急重患者的等待时长'
  }
])

const recommendations = computed(() => {
  const result: string[] = []
  const waitingCount = summary.value?.waitingCount ?? 0
  const averageWaitMinutes = summary.value?.averageWaitMinutes ?? 0
  const timeoutCount = summary.value?.timeoutHighPriorityCount ?? 0

  if (waitingCount >= 12) {
    result.push('当前候诊压力偏高，建议增开分诊窗口或协调诊室加快叫号。')
  } else {
    result.push('当前候诊规模处于可控区间，可维持既有分诊与叫号节奏。')
  }

  if (averageWaitMinutes >= 20) {
    result.push('平均等待时间较长，建议优先检查瓶颈诊室与过号患者回流情况。')
  } else {
    result.push('平均等待时长平稳，建议继续保持号源释放与现场引导同步。')
  }

  if (timeoutCount > 0) {
    result.push(`存在 ${timeoutCount} 名高优先级患者超时，建议立即核查分诊与接诊优先级。`)
  } else {
    result.push('当前无高优先级超时患者，重点关注波峰时段的动态变化。')
  }

  return result
})

async function loadDashboard() {
  const deptId = authStore.profile?.deptId
  if (!deptId) {
    return
  }

  loading.value = true
  try {
    const [deptSummary, waiting] = await Promise.all([getDeptSummary(deptId), getDeptWaiting(deptId)])
    summary.value = deptSummary
    waitingTickets.value = waiting.waitingTickets || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取工作台数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
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
</script>
