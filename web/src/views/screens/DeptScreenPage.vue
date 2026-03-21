<template>
  <div class="screen-page screen-page--dept">
    <header class="screen-masthead">
      <div class="screen-masthead__content">
        <div class="screen-masthead__eyebrow">门诊候诊信息</div>
        <h1>科室候诊大屏</h1>
        <p>请留意患者姓名、票号、所属科室与诊室。大屏已将“候诊中”和“排队中”分开展示，方便患者快速辨认自己当前所处阶段。</p>
      </div>
      <div class="screen-clock">
        <el-select
          v-model="selectedDeptId"
          class="dept-select dept-select--screen"
          popper-class="screen-dept-select-dropdown"
          placeholder="请选择科室"
          :loading="deptOptionsLoading"
          style="width: 220px"
          @change="handleDeptChange"
        >
          <el-option v-for="item in deptSelectOptions" :key="item.id" :label="item.deptName" :value="item.id" />
        </el-select>
        <div class="screen-clock__label">当前科室 {{ scopeLabel }}</div>
        <div class="screen-clock__time">{{ screenTime }}</div>
        <div class="screen-clock__date">{{ screenDate }}</div>
      </div>
    </header>

    <section class="screen-summary-strip">
      <div class="screen-summary-strip__item">
        <span>候诊中</span>
        <strong>{{ consultationTickets.length }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>排队中</span>
        <strong>{{ queueingTickets.length }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>叫号中</span>
        <strong>{{ summary?.callingCount ?? '-' }}</strong>
      </div>
      <div class="screen-summary-strip__item">
        <span>平均等待</span>
        <strong>{{ summary?.averageWaitMinutes ?? '-' }} 分钟</strong>
      </div>
    </section>

    <section class="screen-hospital-board screen-hospital-board--dept">
      <article class="screen-calling-hero" :class="heroCardClass">
        <div class="screen-calling-hero__layout">
          <div class="screen-calling-hero__main">
            <div class="screen-calling-hero__eyebrow">{{ heroTitle }}</div>
            <div class="screen-calling-hero__name">{{ heroName }}</div>
            <div class="screen-calling-hero__ticket">{{ heroTicket }}</div>
            <div v-if="heroTicketData" class="screen-calling-hero__location">
              <span class="screen-calling-hero__dept">{{ heroTicketData.deptName || scopeLabel }}</span>
              <strong class="screen-calling-hero__room">{{ heroTicketData.roomName || (heroTicketData.roomId ? `诊室 ${heroTicketData.roomId}` : '待分配诊室') }}</strong>
            </div>
            <p class="screen-calling-hero__hint">{{ heroHint }}</p>
          </div>

          <div v-if="heroStats.length > 0" class="screen-calling-hero__stats">
            <article v-for="item in heroStats" :key="item.label" class="screen-calling-hero__stat">
              <span class="screen-calling-hero__stat-label">{{ item.label }}</span>
              <strong class="screen-calling-hero__stat-value">{{ item.value }}</strong>
              <small class="screen-calling-hero__stat-hint">{{ item.hint }}</small>
            </article>
          </div>
        </div>

        <div v-if="heroTicketData" class="screen-calling-hero__meta">
          <span class="screen-status-badge" :class="`screen-status-badge--${statusToneByTicket(heroTicketData)}`">
            {{ formatTicketStatus(heroTicketData) }}
          </span>
          <span class="screen-data-badge">科室 {{ heroTicketData.deptName || scopeLabel }}</span>
          <span class="screen-data-badge screen-data-badge--room">{{ heroTicketData.roomName || (heroTicketData.roomId ? `诊室 ${heroTicketData.roomId}` : '待分配诊室') }}</span>
          <span class="screen-data-badge">分诊 {{ formatTriageLevel(heroTicketData.triageLevel) }}</span>
          <span v-if="heroTicketData.rank" class="screen-data-badge">当前排位 {{ heroTicketData.rank }}</span>
          <span class="screen-data-badge">预计等待 {{ heroTicketData.estimatedWaitMinutes ?? 0 }} 分钟</span>
        </div>
      </article>

      <aside class="screen-panel screen-next-panel">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">优先关注</div>
            <div class="screen-panel__caption">这里优先展示叫号中与已进入候诊区的患者，并同步标明科室和诊室。</div>
          </div>
        </div>

        <div v-if="focusTickets.length > 0" class="screen-next-list">
          <article v-for="ticket in focusTickets" :key="ticket.ticketNo" class="screen-next-item">
            <div class="screen-next-item__rank">{{ formatFocusRank(ticket) }}</div>
            <div class="screen-next-item__main">
              <strong>{{ displayPatientName(ticket) }}</strong>
              <div>{{ ticket.ticketNo }} · {{ formatTicketStatus(ticket) }}</div>
              <div class="screen-next-item__location">
                <span>{{ ticket.deptName || scopeLabel }}</span>
                <strong>{{ ticket.roomName || (ticket.roomId ? `诊室 ${ticket.roomId}` : '待分配诊室') }}</strong>
              </div>
            </div>
            <span class="screen-data-badge">{{ formatFocusAction(ticket) }}</span>
          </article>
        </div>

        <article v-else class="screen-focus-item screen-focus-item--empty">
          <span class="screen-focus-item__label">候诊提示</span>
          <strong class="screen-focus-item__title">当前暂无候诊患者</strong>
          <div class="screen-focus-item__meta">新患者入队后，大屏会自动刷新显示。</div>
        </article>
      </aside>
    </section>

    <section class="screen-board screen-board--dept">
      <div v-loading="loading" class="screen-panel screen-panel--board">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">候诊与排队名单</div>
            <div class="screen-panel__caption">名单已按状态分区，患者可先看状态，再核对所属科室与诊室。</div>
          </div>
        </div>

        <div class="screen-board-stacks screen-board-stacks--dept">
          <section class="screen-board-section screen-board-section--consultation">
            <div class="screen-board-section__head">
              <div>
                <div class="screen-board-section__title">候诊区</div>
                <div class="screen-panel__caption">这些患者已进入候诊状态，请按大屏显示的诊室就近等候。</div>
              </div>
              <span class="screen-data-badge">候诊中 {{ consultationTickets.length }} 人</span>
            </div>

            <div class="screen-patient-board screen-patient-board--static">
              <article v-for="ticket in consultationBoardTickets" :key="ticket.ticketNo" class="screen-patient-card">
                <div class="screen-patient-card__rank">{{ formatBoardRank(ticket) }}</div>
                <div class="screen-patient-card__main">
                  <strong class="screen-patient-card__name">{{ displayPatientName(ticket) }}</strong>
                  <div class="screen-patient-card__ticket">{{ ticket.ticketNo }}</div>
                  <div class="screen-patient-card__location">
                    <span>{{ ticket.deptName || scopeLabel }}</span>
                    <strong>{{ ticket.roomName || (ticket.roomId ? `诊室 ${ticket.roomId}` : '待分配诊室') }}</strong>
                  </div>
                </div>
                <div class="screen-patient-card__meta">
                  <span class="screen-status-badge" :class="`screen-status-badge--${statusToneByTicket(ticket)}`">
                    {{ formatTicketStatus(ticket) }}
                  </span>
                  <span class="screen-data-badge">{{ formatTriageLevel(ticket.triageLevel) }}</span>
                  <span class="screen-data-badge">预计 {{ ticket.estimatedWaitMinutes ?? '-' }} 分钟</span>
                </div>
              </article>

              <article v-if="consultationBoardTickets.length === 0" class="screen-patient-card screen-patient-card--empty">
                <div class="screen-patient-card__main">
                  <strong class="screen-patient-card__name">当前暂无候诊患者</strong>
                  <div class="screen-patient-card__ticket">进入候诊区后会优先显示在这里，并同步标注诊室。</div>
                </div>
              </article>
            </div>

            <div v-if="hiddenConsultationCount > 0" class="screen-board-section__more">
              另有 {{ hiddenConsultationCount }} 位候诊患者，请继续留意叫号与现场广播。
            </div>
          </section>

          <section class="screen-board-section screen-board-section--queueing">
            <div class="screen-board-section__head">
              <div>
                <div class="screen-board-section__title">排队区</div>
                <div class="screen-panel__caption">这些患者尚未进入候诊状态，请根据票号、科室与诊室继续留意广播和大屏。</div>
              </div>
              <span class="screen-data-badge">排队中 {{ visibleQueueingTickets.length }} 人</span>
            </div>

            <div v-if="visibleQueueingTickets.length > 0" class="screen-queue-scroll">
              <div
                class="screen-queue-scroll__track"
                :class="{ 'screen-queue-scroll__track--animated': queueShouldScroll }"
                :style="queueScrollStyle"
              >
                <article
                  v-for="(ticket, index) in queueingScrollTickets"
                  :key="`${ticket.ticketNo}-${index}`"
                  class="screen-patient-card screen-patient-card--queueing"
                >
                  <div class="screen-patient-card__rank">{{ formatQueueOrder(index) }}</div>
                  <div class="screen-patient-card__main">
                    <strong class="screen-patient-card__name">{{ displayPatientName(ticket) }}</strong>
                    <div class="screen-patient-card__ticket">{{ ticket.ticketNo }}</div>
                    <div class="screen-patient-card__location">
                      <span>{{ ticket.deptName || scopeLabel }}</span>
                      <strong>{{ ticket.roomName || (ticket.roomId ? `诊室 ${ticket.roomId}` : '待分配诊室') }}</strong>
                    </div>
                  </div>
                  <div class="screen-patient-card__meta">
                    <span class="screen-status-badge" :class="`screen-status-badge--${statusToneByTicket(ticket)}`">
                      {{ formatTicketStatus(ticket) }}
                    </span>
                    <span class="screen-data-badge">{{ formatTriageLevel(ticket.triageLevel) }}</span>
                    <span class="screen-data-badge">预计 {{ ticket.estimatedWaitMinutes ?? '-' }} 分钟</span>
                  </div>
                </article>
              </div>
            </div>

            <article v-else class="screen-patient-card screen-patient-card--empty">
              <div class="screen-patient-card__main">
                <strong class="screen-patient-card__name">当前暂无排队患者</strong>
                <div class="screen-patient-card__ticket">如有新患者取号，将按队列顺序显示在这里。</div>
              </div>
            </article>

            <div v-if="queueShouldScroll" class="screen-board-section__more">
              排队区正在自动滚动，请按顺序查看票号、姓名和对应诊室。
            </div>
            <div v-else-if="hiddenQueueingCount > 0" class="screen-board-section__more">
              其余 {{ hiddenQueueingCount }} 位排队患者将在前方患者进入候诊后自动补充显示。
            </div>
          </section>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDeptSummary } from '@/api/dashboard'
import { getDeptWaiting } from '@/api/queue'
import { useDeptScope } from '@/composables/useDeptScope'
import type { DeptDashboardSummary, DeptQueueSummary, QueueTicket } from '@/types/queue'
import { formatQueueStatus, getQueueDisplayStatus } from '@/utils/queueStatus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const summary = ref<DeptDashboardSummary | null>(null)
const deptQueue = ref<DeptQueueSummary | null>(null)
const screenTime = ref(formatScreenTime())
const screenDate = ref(formatScreenDate())
const {
  deptSelectOptions,
  loading: deptOptionsLoading,
  selectedDeptId,
  scopeLabel,
  loadDeptOptions,
  setSelectedDeptId
} = useDeptScope({
  initialDeptId: Number(route.params.deptId)
})

let clockTimer: number | undefined
let refreshTimer: number | undefined
const BOARD_VISIBLE_COUNT = 4
const CONSULTATION_STATIC_COUNT = BOARD_VISIBLE_COUNT
const QUEUE_DISPLAY_LIMIT = 6
const QUEUE_SCROLL_VISIBLE_COUNT = BOARD_VISIBLE_COUNT
const QUEUE_SCROLL_CARD_HEIGHT = 96
const QUEUE_SCROLL_GAP = 10

const routeDeptId = computed(() => Number(route.params.deptId))
const callingTickets = computed(() => deptQueue.value?.callingTickets || [])
const waitingTickets = computed(() => deptQueue.value?.waitingTickets || [])
const consultationTickets = computed(() =>
  waitingTickets.value.filter((ticket) => getQueueDisplayStatus(ticket) === 'WAITING_FOR_CONSULTATION')
)
const queueingTickets = computed(() =>
  waitingTickets.value.filter((ticket) => getQueueDisplayStatus(ticket) === 'QUEUEING')
)
const consultationBoardTickets = computed(() => consultationTickets.value.slice(0, CONSULTATION_STATIC_COUNT))
const hiddenConsultationCount = computed(() => Math.max(consultationTickets.value.length - consultationBoardTickets.value.length, 0))
const visibleQueueingTickets = computed(() => queueingTickets.value.slice(0, QUEUE_DISPLAY_LIMIT))
const hiddenQueueingCount = computed(() => Math.max(queueingTickets.value.length - visibleQueueingTickets.value.length, 0))
const queueShouldScroll = computed(() => visibleQueueingTickets.value.length > QUEUE_SCROLL_VISIBLE_COUNT)
const queueingScrollTickets = computed(() => {
  if (visibleQueueingTickets.value.length === 0) {
    return []
  }
  return queueShouldScroll.value ? [...visibleQueueingTickets.value, ...visibleQueueingTickets.value] : visibleQueueingTickets.value
})
const queueScrollStyle = computed(() => {
  if (!queueShouldScroll.value) {
    return undefined
  }
  const durationSeconds = Math.max(visibleQueueingTickets.value.length * 4.2, 24)
  const distance = visibleQueueingTickets.value.length * (QUEUE_SCROLL_CARD_HEIGHT + QUEUE_SCROLL_GAP)
  return {
    '--queue-scroll-duration': `${durationSeconds}s`,
    '--queue-scroll-distance': `${distance}px`
  }
})
const heroTicketData = computed(() => callingTickets.value[0] || consultationTickets.value[0] || queueingTickets.value[0] || null)
const focusTickets = computed(() => [...callingTickets.value.slice(0, 2), ...consultationTickets.value.slice(0, 1)].slice(0, 2))

const heroTitle = computed(() => {
  if (callingTickets.value.length > 0) {
    return '当前正在叫号'
  }
  if (consultationTickets.value.length > 0) {
    return '候诊区请做好准备'
  }
  if (queueingTickets.value.length > 0) {
    return '排队队列正在推进'
  }
  return '候诊区当前暂无患者'
})

const heroName = computed(() => {
  if (!heroTicketData.value) {
    return '当前暂无候诊患者'
  }
  return displayPatientName(heroTicketData.value)
})

const heroTicket = computed(() => heroTicketData.value?.ticketNo || '--')

const heroHint = computed(() => {
  if (!heroTicketData.value) {
    return '大屏会自动刷新最新候诊信息，请留意现场广播通知。'
  }

  const location = formatTicketLocation(heroTicketData.value)
  const displayStatus = getQueueDisplayStatus(heroTicketData.value)
  if (displayStatus === 'CALLING') {
    return `请 ${displayPatientName(heroTicketData.value)} 尽快前往 ${location} 报到，并提前准备检查资料。`
  }
  if (displayStatus === 'WAITING_FOR_CONSULTATION') {
    return `请 ${displayPatientName(heroTicketData.value)} 在 ${location} 附近候诊，留意下一次叫号。`
  }
  return `请 ${displayPatientName(heroTicketData.value)} 继续关注 ${location} 的队列进度，等待进入候诊状态。`
})

const heroStats = computed(() => {
  if (!heroTicketData.value) {
    return []
  }

  return [
    {
      label: '当前状态',
      value: formatTicketStatus(heroTicketData.value),
      hint: heroStatusHint(heroTicketData.value)
    },
    {
      label: '接诊医生',
      value: heroTicketData.value.doctorName || '待安排',
      hint: heroTicketData.value.roomName || (heroTicketData.value.roomId ? `诊室 ${heroTicketData.value.roomId}` : '待分配诊室')
    },
    {
      label: '已等候',
      value: formatWaitDuration(heroTicketData.value.waitedMinutes),
      hint: heroTicketData.value.rank ? `当前排位 ${heroTicketData.value.rank}` : '已进入优先处理区'
    },
    {
      label: '优先信息',
      value: `L${heroTicketData.value.triageLevel ?? '-'}`,
      hint: `优先分 ${heroTicketData.value.priorityScore ?? '-'}`
    }
  ]
})

const heroCardClass = computed(() => {
  const displayStatus = getQueueDisplayStatus(heroTicketData.value)
  if (displayStatus === 'CALLING') {
    return 'screen-calling-hero--calling'
  }
  if (displayStatus === 'WAITING_FOR_CONSULTATION') {
    return 'screen-calling-hero--waiting'
  }
  if (displayStatus === 'QUEUEING') {
    return 'screen-calling-hero--queueing'
  }
  return 'screen-calling-hero--idle'
})

async function loadScreen() {
  if (!routeDeptId.value || Number.isNaN(routeDeptId.value)) {
    summary.value = null
    deptQueue.value = null
    return
  }

  loading.value = true
  try {
    const [deptSummary, waiting] = await Promise.all([getDeptSummary(routeDeptId.value), getDeptWaiting(routeDeptId.value)])
    summary.value = deptSummary
    deptQueue.value = waiting
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室大屏数据失败')
  } finally {
    loading.value = false
  }
}

async function handleDeptChange(deptId: number) {
  if (!deptId || deptId === routeDeptId.value) {
    return
  }
  await router.replace(`/screen/dept/${deptId}`)
}

onMounted(async () => {
  try {
    await loadDeptOptions()
    setSelectedDeptId(routeDeptId.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室列表失败')
  }
  await loadScreen()
  clockTimer = window.setInterval(() => {
    screenTime.value = formatScreenTime()
    screenDate.value = formatScreenDate()
  }, 1000)
  refreshTimer = window.setInterval(() => {
    void loadScreen()
  }, 15000)
})

watch(routeDeptId, (deptId) => {
  setSelectedDeptId(deptId)
  void loadScreen()
})

onUnmounted(() => {
  if (clockTimer) {
    window.clearInterval(clockTimer)
  }
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatTicketStatus(ticket?: QueueTicket | null) {
  return formatQueueStatus(ticket)
}

function statusToneByTicket(ticket?: QueueTicket | null) {
  switch (getQueueDisplayStatus(ticket)) {
    case 'CALLING':
      return 'success'
    case 'MISSED':
      return 'warn'
    case 'WAITING_FOR_CONSULTATION':
      return 'info'
    default:
      return 'neutral'
  }
}

function displayPatientName(ticket?: QueueTicket | null) {
  if (!ticket) {
    return '请留意广播'
  }
  return ticket.patientName?.trim() || `${ticket.ticketNo} 号患者`
}

function formatTicketLocation(ticket?: QueueTicket | null) {
  if (!ticket) {
    return '--'
  }
  const deptName = ticket.deptName?.trim() || scopeLabel.value || (routeDeptId.value ? `科室 ${routeDeptId.value}` : '当前科室')
  const roomName = ticket.roomName?.trim() || (ticket.roomId ? `诊室 ${ticket.roomId}` : '待分配诊室')
  return `${deptName} · ${roomName}`
}

function formatBoardRank(ticket?: QueueTicket | null) {
  if (getQueueDisplayStatus(ticket) === 'WAITING_FOR_CONSULTATION') {
    return '候诊'
  }
  if (!ticket?.rank || ticket.rank <= 0) {
    return '--'
  }
  return String(ticket.rank).padStart(2, '0')
}

function formatQueueOrder(index: number) {
  const queueSize = visibleQueueingTickets.value.length || 1
  return String((index % queueSize) + 1)
}

function formatFocusRank(ticket?: QueueTicket | null) {
  const displayStatus = getQueueDisplayStatus(ticket)
  if (displayStatus === 'CALLING') {
    return '叫号'
  }
  if (displayStatus === 'WAITING_FOR_CONSULTATION') {
    return '候诊'
  }
  return formatBoardRank(ticket)
}

function formatFocusAction(ticket?: QueueTicket | null) {
  const displayStatus = getQueueDisplayStatus(ticket)
  if (displayStatus === 'CALLING') {
    return '请前往对应诊室'
  }
  if (displayStatus === 'WAITING_FOR_CONSULTATION') {
    return '请在候诊区等待'
  }
  return `预计 ${ticket?.estimatedWaitMinutes ?? '-'} 分钟`
}

function heroStatusHint(ticket?: QueueTicket | null) {
  const displayStatus = getQueueDisplayStatus(ticket)
  if (displayStatus === 'CALLING') {
    return '请尽快前往诊室报到'
  }
  if (displayStatus === 'WAITING_FOR_CONSULTATION') {
    return '请在候诊区留意下一次叫号'
  }
  return '请继续关注队列进度'
}

function formatWaitDuration(minutes?: number | null) {
  if (minutes === null || minutes === undefined || Number.isNaN(minutes)) {
    return '--'
  }

  if (minutes < 60) {
    return `${minutes} 分钟`
  }

  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return remainMinutes > 0 ? `${hours}小时${remainMinutes}分` : `${hours}小时`
}

function formatScreenTime() {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date())
}

function formatScreenDate() {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(new Date())
}
</script>
