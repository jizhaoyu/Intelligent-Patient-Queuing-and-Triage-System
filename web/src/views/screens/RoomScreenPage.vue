<template>
  <div class="screen-page screen-page--room">
    <header class="screen-masthead">
      <div class="screen-masthead__content">
        <div class="screen-masthead__eyebrow">诊室候诊信息</div>
        <h1>诊室叫号屏</h1>
        <p>请根据屏幕提示进入对应诊室，未被叫到前请继续在候诊区等候。</p>
      </div>
      <div class="screen-clock">
        <div class="screen-clock__label">当前诊室 {{ roomId }}</div>
        <div class="screen-clock__time">{{ screenTime }}</div>
        <div class="screen-clock__date">{{ screenDate }}</div>
      </div>
    </header>

    <section class="screen-room-hero">
      <div class="screen-room-hero__room">诊室 {{ roomId }}</div>
      <div class="screen-room-hero__ticket">{{ current?.ticketNo || '--' }}</div>
      <div class="screen-room-hero__patient">{{ patientLine }}</div>
      <div class="screen-room-hero__meta">
        <span class="screen-status-badge" :class="`screen-status-badge--${statusTone}`">{{ currentStatusText }}</span>
        <span class="screen-data-badge">分诊 {{ current?.triageLevel ?? '-' }} 级</span>
        <span class="screen-data-badge">优先分 {{ current?.priorityScore ?? '-' }}</span>
      </div>
    </section>

    <section class="screen-room-grid">
      <div class="screen-panel">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">当前接诊信息</div>
            <div class="screen-panel__caption">请以现场工作人员引导为准</div>
          </div>
        </div>
        <div class="screen-guidance">
          <div class="screen-guidance__item">
            <span>患者姓名</span>
            <strong>{{ current?.patientName || '--' }}</strong>
          </div>
          <div class="screen-guidance__item">
            <span>当前票号</span>
            <strong>{{ current?.ticketNo || '--' }}</strong>
          </div>
          <div class="screen-guidance__item">
            <span>接诊状态</span>
            <strong>{{ currentStatusText }}</strong>
          </div>
          <div class="screen-guidance__item">
            <span>就诊提醒</span>
            <strong>{{ current?.ticketNo ? '请按提示进入诊室' : '请继续候诊' }}</strong>
          </div>
        </div>
      </div>

      <div class="screen-panel">
        <div class="screen-panel__head">
          <div>
            <div class="screen-panel__title">候诊须知</div>
            <div class="screen-panel__caption">保持就诊秩序与信息准备</div>
          </div>
        </div>
        <div class="screen-notice">
          <p>请在听到广播或看到屏幕提示后进入诊室。</p>
          <p>如暂时离开候诊区，请留意再次叫号或联系导诊台。</p>
          <p>如需协助，请向护士站或导诊人员说明情况。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRoomCurrent } from '@/api/dashboard'
import type { RoomCurrent } from '@/types/queue'
import { formatQueueStatusCode } from '@/utils/queueStatus'

const route = useRoute()
const roomId = Number(route.params.roomId)
const current = ref<RoomCurrent | null>(null)
const screenTime = ref(formatScreenTime())
const screenDate = ref(formatScreenDate())
let clockTimer: number | undefined
let refreshTimer: number | undefined

const currentStatusText = computed(() => {
  if (!current.value?.status) {
    return '待叫号'
  }
  if (current.value.status === 'CALLING') {
    return '请进入诊室'
  }
  if (current.value.status === 'COMPLETED') {
    return '本轮已结束'
  }
  if (current.value.status === 'MISSED') {
    return '请联系导诊台'
  }
  return formatQueueStatusCode(current.value.status)
})

const patientLine = computed(() => {
  if (!current.value?.ticketNo) {
    return '当前暂无叫号，请留意后续提示'
  }
  if (current.value.patientName) {
    return `请 ${current.value.patientName}（${current.value.ticketNo}）前往诊室`
  }
  return `请 ${current.value.ticketNo} 号患者前往诊室`
})

const statusTone = computed(() => {
  switch (current.value?.status) {
    case 'CALLING':
      return 'success'
    case 'MISSED':
      return 'warn'
    case 'COMPLETED':
      return 'neutral'
    default:
      return 'info'
  }
})

async function loadRoomCurrent() {
  try {
    current.value = await getRoomCurrent(roomId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取诊室屏数据失败')
  }
}

onMounted(() => {
  void loadRoomCurrent()
  clockTimer = window.setInterval(() => {
    screenTime.value = formatScreenTime()
    screenDate.value = formatScreenDate()
  }, 1000)
  refreshTimer = window.setInterval(() => {
    void loadRoomCurrent()
  }, 15000)
})

onUnmounted(() => {
  if (clockTimer) {
    window.clearInterval(clockTimer)
  }
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
})

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
