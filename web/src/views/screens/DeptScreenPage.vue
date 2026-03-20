<template>
  <div class="screen-page screen-page--dept">
    <header class="screen-masthead">
      <div class="screen-masthead__content">
        <div class="screen-masthead__eyebrow">门诊候诊信息</div>
        <h1>科室候诊大屏</h1>
        <p>请留意屏幕票号和现场广播，提前准备就诊材料，并按工作人员指引前往对应诊室。</p>
      </div>
      <div class="screen-clock">
        <div class="screen-clock__label">当前科室 {{ deptId }}</div>
        <div class="screen-clock__time">{{ screenTime }}</div>
      </div>
    </header>

    <section class="screen-summary-strip">
      <div class="screen-summary-strip__item">
        <span>候诊总数</span>
        <strong>{{ summary?.waitingCount ?? '-' }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>叫号中</span>
        <strong>{{ summary?.callingCount ?? '-' }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>已完成</span>
        <strong>{{ summary?.completedCount ?? '-' }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>平均等待</span>
        <strong>{{ summary?.averageWaitMinutes ?? '-' }} 分</strong>
      </div>
    </section>

    <section class="screen-priority-board">
      <article class="screen-priority-main">
        <div class="screen-masthead__eyebrow">当前优先关注</div>
        <div class="screen-priority-main__ticket">{{ primaryTicket?.ticketNo || '--' }}</div>
        <p class="screen-priority-main__hint">{{ primaryTicket ? `请留意排位 ${primaryTicket.rank ?? '-'}，并听从现场广播与导诊安排。` : '当前暂无候诊数据，请留意后续叫号提示。' }}</p>
        <div v-if="primaryTicket" class="screen-priority-main__meta">
          <span class="screen-status-badge" :class="`screen-status-badge--${statusTone(primaryTicket.status)}`">{{ formatStatus(primaryTicket.status) }}</span>
          <span class="screen-data-badge">分诊 {{ formatTriageLevel(primaryTicket.triageLevel) }}</span>
          <span class="screen-data-badge">预计等待 {{ primaryTicket.estimatedWaitMinutes ?? '-' }} 分钟</span>
        </div>
      </article>

      <aside class="screen-focus-list">
        <article v-for="ticket in secondaryTickets" :key="ticket.ticketNo" class="screen-focus-item">
          <span class="screen-focus-item__label">候诊票号</span>
          <strong class="screen-focus-item__title">{{ ticket.ticketNo }}</strong>
          <div class="screen-focus-item__meta">排位 {{ ticket.rank ?? '-' }} · {{ formatTriageLevel(ticket.triageLevel) }} · {{ formatStatus(ticket.status) }}</div>
        </article>
        <article v-if="secondaryTickets.length === 0" class="screen-focus-item screen-focus-item--empty">
          <span class="screen-focus-item__label">候诊提示</span>
          <strong class="screen-focus-item__title">请关注后续广播</strong>
          <div class="screen-focus-item__meta">系统将在有新的候诊信息时继续更新。</div>
        </article>
      </aside>
    </section>

    <section class="screen-board">
      <div class="screen-panel">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">候诊队列</div>
            <div class="screen-panel__caption">仅展示当前科室的实时排队信息</div>
          </div>
        </div>
        <el-table v-loading="loading" :data="queueList" height="500">
          <el-table-column label="排位" width="120">
            <template #default="{ row }">
              <span class="screen-data-badge">{{ row.rank ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="ticketNo" label="票号" min-width="180" />
          <el-table-column label="分诊等级" width="160">
            <template #default="{ row }">
              <span class="screen-data-badge">{{ formatTriageLevel(row.triageLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="160">
            <template #default="{ row }">
              <span class="screen-status-badge" :class="`screen-status-badge--${statusTone(row.status)}`">{{ formatStatus(row.status) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <aside class="screen-panel">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">候诊提示</div>
            <div class="screen-panel__caption">请按现场引导有序候诊</div>
          </div>
        </div>
        <div class="screen-guidance">
          <div class="screen-guidance__item">
            <span>平均等待</span>
            <strong>{{ summary?.averageWaitMinutes ?? '-' }} 分钟</strong>
          </div>
          <div class="screen-guidance__item">
            <span>高优先级提醒</span>
            <strong>{{ summary?.timeoutHighPriorityCount ?? '-' }} 人</strong>
          </div>
          <div class="screen-guidance__item">
            <span>候诊秩序</span>
            <strong>{{ queueList.length > 0 ? '请按票号顺序候诊' : '当前暂无排队' }}</strong>
          </div>
        </div>
        <div class="screen-notice">
          <p>请提前准备医保卡、挂号信息与检查资料。</p>
          <p>屏幕票号与广播叫号同步更新，请勿远离候诊区。</p>
          <p>如需帮助，请联系导诊台或分诊护士。</p>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDeptSummary } from '@/api/dashboard'
import { getDeptWaiting } from '@/api/queue'
import type { DeptDashboardSummary, QueueTicket } from '@/types/queue'

const route = useRoute()
const deptId = Number(route.params.deptId)
const loading = ref(false)
const summary = ref<DeptDashboardSummary | null>(null)
const queueList = ref<QueueTicket[]>([])
const screenTime = ref(formatScreenTime())
let timer: number | undefined

const primaryTicket = computed(() => queueList.value[0] || null)
const secondaryTickets = computed(() => queueList.value.slice(1, 4))

async function loadScreen() {
  loading.value = true
  try {
    const [deptSummary, waiting] = await Promise.all([getDeptSummary(deptId), getDeptWaiting(deptId)])
    summary.value = deptSummary
    queueList.value = waiting.waitingTickets || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室大屏数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadScreen()
  timer = window.setInterval(() => {
    screenTime.value = formatScreenTime()
  }, 1000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatStatus(status?: string) {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '过号'
  }
  return status ? map[status] || status : '-'
}

function statusTone(status?: string) {
  switch (status) {
    case 'CALLING':
      return 'success'
    case 'WAITING':
      return 'info'
    case 'MISSED':
      return 'warn'
    default:
      return 'neutral'
  }
}

function formatScreenTime() {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date())
}
</script>
