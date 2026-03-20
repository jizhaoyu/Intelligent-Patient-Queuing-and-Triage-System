<template>
  <div class="queue-call-page">
    <PageHeader title="诊室叫号工作台" eyebrow="医生工作台" description="优先关注当前患者、接诊状态和主操作，减少重复判断与误操作。">
      <template #actions>
        <el-tag effect="plain">当前诊室 {{ authStore.profile?.roomId ?? '-' }}</el-tag>
        <el-tag effect="plain">值班 {{ authStore.profile?.nickname || authStore.profile?.username || '-' }}</el-tag>
      </template>
    </PageHeader>

    <section class="call-command">
      <div class="call-command__panel">
        <div class="current-patient">
          <span class="current-patient__label">当前候诊焦点</span>
          <div class="current-patient__ticket">{{ currentTicket?.ticketNo || '--' }}</div>
          <div class="current-patient__name">{{ currentTicket ? `患者编号 ${currentTicket.patientId}` : '当前暂无待接诊患者' }}</div>
          <div class="call-command__meta">
            <span class="status-pill" :class="statusPillClass">{{ currentStatusText }}</span>
            <span class="data-pill">分诊等级 {{ currentTicket?.triageLevel ?? '-' }}</span>
            <span class="data-pill">优先分 {{ currentTicket?.priorityScore ?? '-' }}</span>
          </div>
          <div class="current-patient__support">{{ currentTicket ? patientSummary : '点击“叫下一位”后，系统将在此展示当前接诊患者。' }}</div>
        </div>
      </div>

      <div class="call-command__actions">
        <div class="call-command__primary">
          <el-button type="primary" size="large" :loading="calling" @click="handleCallNext">叫下一位</el-button>
          <p class="call-command__hint">主操作优先放大展示，便于医生在接诊过程中快速连续叫号。</p>
        </div>
        <div class="call-command__secondary">
          <el-button size="large" :disabled="!currentTicket" :loading="recalling" @click="handleRecall">重呼</el-button>
          <el-button type="warning" size="large" :disabled="!currentTicket" :loading="markingMissed" @click="handleMissed">标记过号</el-button>
          <el-button type="success" size="large" :disabled="!currentTicket" :loading="completing" @click="handleComplete">完成接诊</el-button>
        </div>
      </div>
    </section>

    <div class="content-grid">
      <el-card class="panel-card">
        <template #header>接诊摘要</template>
        <div v-if="currentTicket" class="detail-list">
          <div class="detail-list__item">
            <span>患者与票号</span>
            <strong>患者编号 {{ currentTicket.patientId }} · {{ currentTicket.ticketNo }}</strong>
            <small>请先核对患者身份，再继续后续接诊流程。</small>
          </div>
          <div class="detail-list__item">
            <span>当前状态</span>
            <strong>{{ currentStatusText }}</strong>
            <small>排位 {{ currentTicket.rank ?? '-' }}，前方候诊 {{ currentTicket.waitingCount ?? '-' }} 人。</small>
          </div>
          <div class="detail-list__item">
            <span>分诊信息</span>
            <strong>{{ currentTicket.triageLevel }} 级 · 优先分 {{ currentTicket.priorityScore }}</strong>
            <small>科室 {{ currentTicket.deptId }}，诊室 {{ currentTicket.roomId ?? '-' }}。</small>
          </div>
          <div class="detail-list__item">
            <span>接诊记录</span>
            <strong>重呼 {{ currentTicket.recallCount }} 次</strong>
            <small>就诊记录 {{ currentTicket.visitId }}，请结合现场情况完成处置。</small>
          </div>
        </div>
        <el-empty v-else description="暂无当前叫号票据" />
      </el-card>

      <el-card class="panel-card">
        <template #header>接诊提醒</template>
        <div class="room-list">
          <div class="room-card">先确认患者身份和分诊等级，再执行重呼、过号或完成接诊。</div>
          <div class="room-card">若患者暂未到诊，建议先重呼，再根据现场情况决定是否过号。</div>
          <div class="room-card">最近已记录 {{ operations.length }} 条操作，请保持诊室处置链路连续、清晰可追溯。</div>
        </div>
      </el-card>
    </div>

    <el-card class="panel-card">
      <template #header>最近操作</template>
      <div class="room-list">
        <div v-if="operations.length === 0" class="room-card">尚无操作记录，完成叫号后会在此生成处置时间线。</div>
        <div v-for="item in operations" :key="item" class="room-card room-card--timeline">{{ item }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { callNext, completeTicket, markMissed, recall } from '@/api/queue'
import { useAuthStore } from '@/stores/auth'
import type { QueueTicket } from '@/types/queue'

const authStore = useAuthStore()
const currentTicket = ref<QueueTicket | null>(null)
const operations = ref<string[]>([])
const calling = ref(false)
const recalling = ref(false)
const markingMissed = ref(false)
const completing = ref(false)

const currentStatusText = computed(() => {
  if (!currentTicket.value?.status) {
    return '待叫号'
  }

  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '过号'
  }

  return map[currentTicket.value.status] || currentTicket.value.status
})

const statusPillClass = computed(() => {
  switch (currentTicket.value?.status) {
    case 'CALLING':
      return 'status-pill--success'
    case 'MISSED':
      return 'status-pill--warn'
    case 'COMPLETED':
      return 'status-pill--neutral'
    default:
      return ''
  }
})

const patientSummary = computed(() => {
  if (!currentTicket.value) {
    return ''
  }
  return `前方候诊 ${currentTicket.value.waitingCount ?? '-'} 人，当前排位 ${currentTicket.value.rank ?? '-'}，请结合现场状态完成接诊。`
})

function appendLog(action: string, ticket?: QueueTicket | null) {
  const ticketNo = ticket?.ticketNo || currentTicket.value?.ticketNo || '-'
  operations.value.unshift(`${new Date().toLocaleString()} ${action} ${ticketNo}`)
  operations.value = operations.value.slice(0, 8)
}

async function handleCallNext() {
  if (!authStore.profile?.roomId) {
    ElMessage.warning('当前账号未绑定诊室')
    return
  }

  calling.value = true
  try {
    currentTicket.value = await callNext(authStore.profile.roomId)
    appendLog('叫号', currentTicket.value)
    ElMessage.success('已叫下一位')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '叫号失败')
  } finally {
    calling.value = false
  }
}

async function handleRecall() {
  if (!currentTicket.value) {
    return
  }

  recalling.value = true
  try {
    currentTicket.value = await recall(currentTicket.value.ticketNo)
    appendLog('重呼', currentTicket.value)
    ElMessage.success('已重呼')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重呼失败')
  } finally {
    recalling.value = false
  }
}

async function handleMissed() {
  if (!currentTicket.value) {
    return
  }

  markingMissed.value = true
  try {
    currentTicket.value = await markMissed(currentTicket.value.ticketNo)
    appendLog('过号', currentTicket.value)
    ElMessage.success('已标记过号')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '标记过号失败')
  } finally {
    markingMissed.value = false
  }
}

async function handleComplete() {
  if (!currentTicket.value) {
    return
  }

  completing.value = true
  try {
    currentTicket.value = await completeTicket(currentTicket.value.ticketNo)
    appendLog('完成接诊', currentTicket.value)
    ElMessage.success('已完成接诊')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '完成接诊失败')
  } finally {
    completing.value = false
  }
}
</script>
