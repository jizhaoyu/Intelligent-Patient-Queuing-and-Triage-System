<template>
  <div class="queue-call-page">
    <PageHeader
      title="诊室叫号"
      eyebrow="工作台主链路"
      description="默认按当前诊室加载待诊患者，支持叫下一位、复呼、过号和完成接诊。"
    >
      <template #actions>
        <div class="header-actions">
          <el-tag effect="plain">当前诊室 {{ roomLabel }}</el-tag>
          <el-tag effect="plain">所属科室 {{ deptLabel }}</el-tag>
          <el-button :loading="loading" @click="loadWorkbench(selectedTicket?.ticketNo)">刷新</el-button>
        </div>
      </template>
    </PageHeader>

    <el-alert
      v-if="!roomId"
      class="mb-16"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号未绑定诊室，无法执行叫号操作；系统已降级为本科室排队概览视图。"
    />

    <el-card class="mb-16 control-card">
      <div class="toolbar toolbar--split">
        <div class="toolbar__group">
          <el-input
            v-model="keyword"
            placeholder="姓名 / 患者编号 / 票号"
            style="width: 320px"
            clearable
          />
          <el-button @click="keyword = ''">清空</el-button>
        </div>
        <div class="toolbar__group toolbar__group--meta">
          <el-radio-group v-model="statusFilter" size="small">
            <el-radio-button value="ALL">全部</el-radio-button>
            <el-radio-button value="WAITING_FOR_CONSULTATION">候诊中</el-radio-button>
            <el-radio-button value="QUEUEING">排队中</el-radio-button>
            <el-radio-button value="CALLING">叫号中</el-radio-button>
            <el-radio-button value="MISSED">过号</el-radio-button>
          </el-radio-group>
          <span class="toolbar__timestamp">最近刷新 {{ lastRefreshTime || '--' }}</span>
        </div>
      </div>
      <div class="toolbar-foot">
        <el-tag effect="plain">队列范围：{{ roomId ? '当前诊室优先' : '当前科室概览' }}</el-tag>
        <el-tag effect="plain">当前焦点：{{ focusTicket?.ticketNo || '未选择' }}</el-tag>
      </div>
    </el-card>

    <section class="summary-grid">
      <article class="summary-card summary-card--waiting">
        <span>候诊中</span>
        <strong>{{ stats.waitingForConsultation }}</strong>
        <small>当前诊室门口候诊</small>
      </article>
      <article class="summary-card summary-card--calling">
        <span>排队中</span>
        <strong>{{ stats.queueing }}</strong>
        <small>同诊室其余等待患者</small>
      </article>
      <article class="summary-card summary-card--missed">
        <span>叫号中</span>
        <strong>{{ stats.calling }}</strong>
        <small>正在报到或接诊</small>
      </article>
      <article class="summary-card summary-card--accent">
        <span>当前焦点</span>
        <strong>{{ focusTicket?.patientName || focusTicket?.ticketNo || '--' }}</strong>
        <small>{{ formatTicketQueueStatus(focusTicket, '暂无叫号焦点') }}</small>
      </article>
    </section>

    <section class="hero-grid">
      <el-card class="focus-card">
        <div class="focus-card__layout">
          <div class="focus-card__main">
            <div class="focus-card__topbar">
              <div>
                <div class="focus-card__eyebrow">当前接诊焦点</div>
                <div class="focus-card__ticket">{{ focusTicket?.ticketNo || '--' }}</div>
              </div>
              <div class="focus-card__status">
                <span class="status-dot" :class="statusPillClassByTicket(focusTicket)"></span>
                <span>{{ formatTicketQueueStatus(focusTicket, '待叫号') }}</span>
              </div>
            </div>
            <div class="focus-card__name">
              {{ focusTicket?.patientName || focusTicket?.patientNo || '当前暂无可处理患者' }}
            </div>
            <div class="focus-card__meta">
              <span class="status-pill" :class="statusPillClassByTicket(focusTicket)">
                {{ formatTicketQueueStatus(focusTicket, '待叫号') }}
              </span>
              <span class="data-pill">分诊 {{ formatTriageLevel(focusTicket?.triageLevel) }}</span>
              <span class="data-pill">前方 {{ focusTicket?.waitingCount ?? '-' }} 人</span>
              <span class="data-pill">已等待 {{ formatMinutes(focusTicket?.waitedMinutes) }}</span>
            </div>
            <div class="focus-card__complaint">
              <span>主诉 / 主要不适</span>
              <p>{{ focusTicket?.chiefComplaint || '暂未记录主诉，建议结合现场问诊进一步确认。' }}</p>
            </div>
            <p class="focus-card__desc">
              {{
                focusTicket
                  ? `当前排位 ${focusTicket.rank ?? '-'}，请先核对患者身份，再执行叫号或完成接诊。`
                  : '点击“叫下一位”后，系统会在这里显示当前诊室的接诊焦点。'
              }}
            </p>
            <div class="focus-card__actions">
              <el-button type="primary" size="large" :disabled="!roomId" :loading="calling" @click="handleCallNext">
                叫下一位
              </el-button>
              <el-button size="large" :disabled="!canRecall" :loading="recalling" @click="handleRecall">
                复呼
              </el-button>
              <el-button type="warning" size="large" :disabled="!canMarkMissed" :loading="markingMissed" @click="handleMissed">
                标记过号
              </el-button>
              <el-button type="success" size="large" :disabled="!canComplete" :loading="completing" @click="handleComplete">
                完成接诊
              </el-button>
            </div>
          </div>

          <div class="focus-card__aside">
            <div class="focus-panel">
              <div class="focus-panel__header">患者核对</div>
              <div v-if="focusTicket" class="focus-kv-grid">
                <div class="focus-kv-item">
                  <span>患者编号</span>
                  <strong>{{ focusTicket.patientNo || focusPatient?.patientNo || '-' }}</strong>
                </div>
                <div class="focus-kv-item">
                  <span>联系电话</span>
                  <strong>{{ focusPatient?.phone || '-' }}</strong>
                </div>
                <div class="focus-kv-item">
                  <span>当前就诊号</span>
                  <strong>{{ focusPatient?.currentVisitNo || focusVisitNo }}</strong>
                </div>
                <div class="focus-kv-item">
                  <span>诊室 / 医生</span>
                  <strong>{{ formatRoomDoctor(focusTicket.roomName, focusTicket.doctorName, focusTicket.roomId) }}</strong>
                </div>
              </div>
              <el-empty v-else description="暂无待核对患者" :image-size="72" />
            </div>

            <div class="focus-panel">
              <div class="focus-panel__header">接诊进度</div>
              <div class="focus-metric-grid">
                <article class="focus-metric-card">
                  <span>当前排位</span>
                  <strong>{{ focusTicket?.rank ?? '-' }}</strong>
                  <small>队列中的实时顺位</small>
                </article>
                <article class="focus-metric-card">
                  <span>预计等待</span>
                  <strong>{{ formatMinutes(focusTicket?.estimatedWaitMinutes) }}</strong>
                  <small>供护士与患者沟通参考</small>
                </article>
                <article class="focus-metric-card">
                  <span>复呼次数</span>
                  <strong>{{ focusTicket?.recallCount ?? 0 }} 次</strong>
                  <small>连续未到可考虑过号</small>
                </article>
                <article class="focus-metric-card">
                  <span>优先分</span>
                  <strong>{{ focusTicket?.priorityScore ?? '-' }}</strong>
                  <small>{{ focusQueueStrategyLabel }}</small>
                </article>
              </div>
            </div>

            <div class="focus-panel">
              <div class="focus-panel__header">本次处理提示</div>
              <div class="focus-checklist">
                <div class="focus-checklist__item" :class="{ 'is-active': !!focusTicket }">
                  <span class="focus-checklist__dot"></span>
                  <div>
                    <strong>确认患者身份</strong>
                    <p>{{ focusTicket ? '核对姓名、患者编号与就诊号后再执行接诊操作。' : '等待叫号后开始核对患者身份。' }}</p>
                  </div>
                </div>
                <div class="focus-checklist__item" :class="{ 'is-active': canRecall || canMarkMissed || canComplete }">
                  <span class="focus-checklist__dot"></span>
                  <div>
                    <strong>按状态执行操作</strong>
                    <p>{{ focusActionHint }}</p>
                  </div>
                </div>
                <div class="focus-checklist__item" :class="{ 'is-active': !!priorityReasonLabel || !!aiAdviceLabel }">
                  <span class="focus-checklist__dot"></span>
                  <div>
                    <strong>关注优先策略</strong>
                    <p>{{ focusStrategyHint }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="preview-card">
        <template #header>本科室排队概览</template>
        <div class="preview-stats">
          <article>
            <span>候诊中</span>
            <strong>{{ queuePreview?.waitingCount ?? 0 }}</strong>
          </article>
          <article>
            <span>叫号中</span>
            <strong>{{ queuePreview?.callingTickets?.length ?? 0 }}</strong>
          </article>
          <article>
            <span>队首患者</span>
            <strong>{{ queuePreviewLead }}</strong>
          </article>
        </div>
        <div class="preview-list">
          <div v-if="queuePreviewTickets.length === 0" class="preview-item">当前本科室暂无候诊患者。</div>
          <div v-for="ticket in queuePreviewTickets" :key="ticket.ticketNo" class="preview-item">
            <strong>{{ ticket.patientName || ticket.patientNo || ticket.ticketNo }}</strong>
            <span>{{ ticket.ticketNo }} / {{ ticket.roomName || formatRoomLabel(ticket.roomId) }}</span>
          </div>
        </div>
      </el-card>
    </section>

    <div class="content-grid">
      <el-card>
        <template #header>
          <div class="card-header-inline">
            <span>当前待处理队列</span>
            <div class="card-header-inline__meta">
              <el-tag effect="plain">{{ filteredTickets.length }} 人</el-tag>
              <span class="table-hint">单击行可查看右侧接诊摘要</span>
            </div>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="filteredTickets"
          row-key="ticketNo"
          highlight-current-row
          :current-row-key="selectedTicket?.ticketNo"
          @row-click="handleSelectTicket"
        >
          <el-table-column prop="ticketNo" label="票号" min-width="160" />
          <el-table-column label="患者" min-width="180">
            <template #default="{ row }">
              <div class="patient-cell">
                <strong>{{ row.patientName || row.patientNo || row.patientId }}</strong>
                <span>{{ row.patientNo || '未登记患者编号' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="queueStatusTypeByTicket(row)" effect="plain">
                {{ formatTicketQueueStatus(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分诊等级" width="120">
            <template #default="{ row }">{{ formatTriageLevel(row.triageLevel) }}</template>
          </el-table-column>
          <el-table-column label="诊室" min-width="140">
            <template #default="{ row }">{{ row.roomName || formatRoomLabel(row.roomId) }}</template>
          </el-table-column>
          <el-table-column label="排位" width="100">
            <template #default="{ row }">{{ row.rank && row.rank > 0 ? `#${row.rank}` : '-' }}</template>
          </el-table-column>
          <el-table-column label="已等待" width="120">
            <template #default="{ row }">{{ formatMinutes(row.waitedMinutes) }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="side-grid">
        <el-card>
          <template #header>接诊摘要</template>
          <el-descriptions v-if="selectedTicket" :column="1" border>
            <el-descriptions-item label="票号">{{ selectedTicket.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="患者">{{ selectedTicket.patientName || selectedTicket.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">{{ formatTicketQueueStatus(selectedTicket) }}</el-descriptions-item>
            <el-descriptions-item label="排位 / 前方人数">
              {{ selectedTicket.rank ?? '-' }} / {{ selectedTicket.waitingCount ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="预计等待">{{ formatMinutes(selectedTicket.estimatedWaitMinutes) }}</el-descriptions-item>
            <el-descriptions-item label="诊室 / 医生">
              {{ formatRoomDoctor(selectedTicket.roomName, selectedTicket.doctorName, selectedTicket.roomId) }}
            </el-descriptions-item>
            <el-descriptions-item label="分诊等级 / 优先分">
              {{ formatTriageLevel(selectedTicket.triageLevel) }} / {{ selectedTicket.priorityScore ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="主诉">
              {{ selectedTicket.chiefComplaint || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="队列来源">
              {{ formatSource(selectedTicket.sourceType, selectedTicket.sourceRemark) }}
            </el-descriptions-item>
            <el-descriptions-item label="最近调整原因">
              {{ selectedTicket.lastAdjustReason || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="入队时间">{{ selectedTicket.enqueueTime || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="请选择待处理患者" />
        </el-card>

        <el-card>
          <template #header>患者信息摘要</template>
          <el-descriptions v-if="focusPatient" :column="1" border>
            <el-descriptions-item label="患者编号">{{ focusPatient.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ focusPatient.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ focusPatient.gender || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ focusPatient.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="过敏史">{{ focusPatient.allergyHistory || '-' }}</el-descriptions-item>
            <el-descriptions-item label="特殊标签">{{ focusPatient.specialTags || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前就诊号">{{ focusPatient.currentVisitNo || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="当前未加载患者详情" />
        </el-card>

        <el-card>
          <template #header>最近操作</template>
          <div class="operation-list">
            <div v-if="operations.length === 0" class="operation-item">尚无操作记录，叫号后会在这里生成时间线。</div>
            <div v-for="item in operations" :key="item" class="operation-item">{{ item }}</div>
          </div>
        </el-card>

        <el-card>
          <template #header>优先策略解读</template>
          <div v-if="selectedTicket" class="strategy-list">
            <div class="strategy-item">
              <span>当前策略</span>
              <strong>{{ queueStrategyLabel }}</strong>
            </div>
            <div class="strategy-item">
              <span>优先说明</span>
              <strong>{{ priorityReasonLabel }}</strong>
            </div>
            <div class="strategy-tags">
              <el-tag v-if="surgeTag" type="warning" effect="plain">{{ surgeTag }}</el-tag>
              <el-tag v-if="agingTag" type="success" effect="plain">{{ agingTag }}</el-tag>
              <el-tag type="info" effect="plain">{{ aiPriorityAdviceLabel }}</el-tag>
            </div>
            <div class="strategy-item">
              <span>AI 分诊参考</span>
              <strong>{{ aiAssessmentSummaryLabel }}</strong>
            </div>
            <div class="strategy-item">
              <span>AI 建议说明</span>
              <strong>{{ aiAdviceLabel }}</strong>
            </div>
          </div>
          <el-empty v-else description="先选择一位患者即可查看队列策略" />
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientById } from '@/api/patient'
import {
  callNext,
  completeTicket,
  getActiveTickets,
  getTicket,
  getWaitingSummary,
  markMissed,
  recall
} from '@/api/queue'
import { useAuthStore } from '@/stores/auth'
import type { Patient } from '@/types/patient'
import type { DeptQueueSummary, QueueTicket } from '@/types/queue'
import {
  formatQueueStatus as formatDisplayQueueStatus,
  getQueueDisplayStatus,
  getQueueStatusTagType
} from '@/utils/queueStatus'

const authStore = useAuthStore()

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<'ALL' | 'WAITING' | 'WAITING_FOR_CONSULTATION' | 'QUEUEING' | 'CALLING' | 'MISSED'>('ALL')
const lastRefreshTime = ref('')
const tickets = ref<QueueTicket[]>([])
const queuePreview = ref<DeptQueueSummary | null>(null)
const selectedTicket = ref<QueueTicket | null>(null)
const currentTicket = ref<QueueTicket | null>(null)
const patientDetail = ref<Patient | null>(null)
const operations = ref<string[]>([])
const calling = ref(false)
const recalling = ref(false)
const markingMissed = ref(false)
const completing = ref(false)

const deptId = computed(() => authStore.profile?.deptId ?? null)
const roomId = computed(() => authStore.profile?.roomId ?? null)
const deptLabel = computed(() => (deptId.value ? `科室 ${deptId.value}` : '未绑定'))
const roomLabel = computed(() => (roomId.value ? `诊室 ${roomId.value}` : '未绑定'))
const focusTicket = computed(() => currentTicket.value || selectedTicket.value)
const focusPatient = computed(() => {
  if (!focusTicket.value || !selectedTicket.value || focusTicket.value.ticketNo !== selectedTicket.value.ticketNo) {
    return null
  }
  return patientDetail.value
})
const focusVisitNo = computed(() => focusPatient.value?.currentVisitNo || (focusTicket.value?.visitId ? String(focusTicket.value.visitId) : '-'))
const focusQueueStrategyLabel = computed(() => {
  const mode = focusTicket.value?.queueStrategyMode
  if (!mode) {
    return '默认'
  }
  if (mode === 'SURGE') {
    return '高峰策略'
  }
  return '常规策略'
})
const focusActionHint = computed(() => {
  if (!focusTicket.value) {
    return '当前暂无接诊焦点，可先执行“叫下一位”。'
  }
  if (focusTicket.value.status === 'CALLING') {
    return '患者已被叫号，可继续复呼、标记过号，或在接诊完成后点击“完成接诊”。'
  }
  if (focusTicket.value.status === 'MISSED') {
    return '当前患者处于过号状态，如患者到场可直接执行复呼。'
  }
  return '当前患者仍在等待中，确认信息后可继续叫号流转。'
})
const focusStrategyHint = computed(() => {
  if (!focusTicket.value) {
    return '当前无优先策略提示。'
  }
  return focusTicket.value.priorityReason || focusTicket.value.aiAdvice || focusTicket.value.aiPriorityAdvice || '当前按常规队列策略处理。'
})
const canRecall = computed(() => ['CALLING', 'MISSED'].includes(focusTicket.value?.status || ''))
const canMarkMissed = computed(() => focusTicket.value?.status === 'CALLING')
const canComplete = computed(() => focusTicket.value?.status === 'CALLING')

const filteredTickets = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  return tickets.value.filter((ticket) => {
    const matchesKeyword =
      !normalized ||
      [ticket.patientName, ticket.patientNo, ticket.ticketNo]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalized))
    const displayStatus = getQueueDisplayStatus(ticket)
    const matchesStatus =
      statusFilter.value === 'ALL' ||
      (statusFilter.value === 'WAITING'
        ? displayStatus === 'WAITING_FOR_CONSULTATION' || displayStatus === 'QUEUEING'
        : displayStatus === statusFilter.value)
    return matchesKeyword && matchesStatus
  })
})

const stats = computed(() => ({
  waitingForConsultation: tickets.value.filter((ticket) => getQueueDisplayStatus(ticket) === 'WAITING_FOR_CONSULTATION').length,
  queueing: tickets.value.filter((ticket) => getQueueDisplayStatus(ticket) === 'QUEUEING').length,
  calling: tickets.value.filter((ticket) => ticket.status === 'CALLING').length,
  missed: tickets.value.filter((ticket) => ticket.status === 'MISSED').length
}))

const queuePreviewLead = computed(() => {
  const leadTicket = queuePreview.value?.callingTickets?.[0] || queuePreview.value?.waitingTickets?.[0]
  return leadTicket?.patientName || leadTicket?.ticketNo || '暂无'
})

const queuePreviewTickets = computed(() => {
  return [...(queuePreview.value?.callingTickets || []), ...(queuePreview.value?.waitingTickets || [])].slice(0, 5)
})

const priorityReasonLabel = computed(() => selectedTicket.value?.priorityReason || '未见优先说明')
const queueStrategyLabel = computed(() => {
  const mode = selectedTicket.value?.queueStrategyMode
  if (!mode) {
    return '默认'
  }
  if (mode === 'SURGE') {
    return '高峰策略'
  }
  return '常规策略'
})
const surgeTag = computed(() => (selectedTicket.value?.surgePriorityApplied ? '高峰加权' : ''))
const agingTag = computed(() => (selectedTicket.value?.agingBoostApplied ? '等待老化' : ''))
const aiPriorityAdviceLabel = computed(() => selectedTicket.value?.aiPriorityAdvice || '暂无AI补充')
const aiAssessmentSummaryLabel = computed(() => {
  if (!selectedTicket.value) {
    return '暂无AI分诊信息'
  }
  const parts: string[] = []
  if (selectedTicket.value.aiSuggestedLevel) {
    parts.push(`建议 ${selectedTicket.value.aiSuggestedLevel} 级`)
  }
  if (selectedTicket.value.aiRiskLevel) {
    parts.push(`风险 ${formatAiRiskLevel(selectedTicket.value.aiRiskLevel)}`)
  }
  if (selectedTicket.value.aiNeedManualReview) {
    parts.push('建议人工复核')
  }
  return parts.length ? parts.join(' / ') : '暂无AI分诊信息'
})
const aiAdviceLabel = computed(() => selectedTicket.value?.aiAdvice || '当前队列详情还没有可展示的 AI 说明')

async function loadWorkbench(preferredTicketNo?: string) {
  loading.value = true
  try {
    const [activeTickets, preview] = await Promise.all([
      getActiveTickets(deptId.value ?? undefined, roomId.value ?? undefined),
      deptId.value ? getWaitingSummary(deptId.value) : Promise.resolve(null)
    ])
    tickets.value = activeTickets
    queuePreview.value = preview
    currentTicket.value = tickets.value.find((ticket) => ticket.status === 'CALLING') || null
    lastRefreshTime.value = new Date().toLocaleTimeString()

    const targetTicketNo =
      preferredTicketNo ||
      currentTicket.value?.ticketNo ||
      (selectedTicket.value && tickets.value.some((ticket) => ticket.ticketNo === selectedTicket.value?.ticketNo)
        ? selectedTicket.value.ticketNo
        : tickets.value[0]?.ticketNo)

    if (!targetTicketNo) {
      selectedTicket.value = null
      patientDetail.value = null
      return
    }

    await hydrateTicket(targetTicketNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取叫号工作台数据失败')
  } finally {
    loading.value = false
  }
}

async function hydrateTicket(ticketNo: string) {
  const baseTicket = tickets.value.find((item) => item.ticketNo === ticketNo)
  if (!baseTicket) {
    selectedTicket.value = null
    patientDetail.value = null
    return
  }

  try {
    const [ticketDetail, fetchedPatientDetail] = await Promise.all([
      getTicket(ticketNo),
      baseTicket.patientId ? getPatientById(baseTicket.patientId).catch(() => null) : Promise.resolve(null)
    ])
    selectedTicket.value = ticketDetail
    patientDetail.value = fetchedPatientDetail
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取接诊摘要失败')
  }
}

function handleSelectTicket(ticket: QueueTicket) {
  void hydrateTicket(ticket.ticketNo)
}

function appendOperation(action: string, ticket?: QueueTicket | null) {
  const ticketNo = ticket?.ticketNo || focusTicket.value?.ticketNo || '-'
  operations.value.unshift(`${new Date().toLocaleString()} ${action} ${ticketNo}`)
  operations.value = operations.value.slice(0, 8)
}

async function handleCallNext() {
  if (!roomId.value) {
    ElMessage.warning('当前账号未绑定诊室，无法执行叫号')
    return
  }

  calling.value = true
  try {
    const ticket = await callNext(roomId.value)
    appendOperation('叫号', ticket)
    ElMessage.success('已成功叫下一位')
    await loadWorkbench(ticket.ticketNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '叫号失败')
  } finally {
    calling.value = false
  }
}

async function handleRecall() {
  if (!focusTicket.value) {
    return
  }

  recalling.value = true
  try {
    const ticket = await recall(focusTicket.value.ticketNo)
    appendOperation('复呼', ticket)
    ElMessage.success('已完成复呼')
    await loadWorkbench(ticket.ticketNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复呼失败')
  } finally {
    recalling.value = false
  }
}

async function handleMissed() {
  if (!focusTicket.value) {
    return
  }

  markingMissed.value = true
  try {
    const ticket = await markMissed(focusTicket.value.ticketNo)
    appendOperation('过号', ticket)
    ElMessage.success('已标记为过号')
    await loadWorkbench(ticket.ticketNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '标记过号失败')
  } finally {
    markingMissed.value = false
  }
}

async function handleComplete() {
  if (!focusTicket.value) {
    return
  }

  completing.value = true
  try {
    const ticket = await completeTicket(focusTicket.value.ticketNo)
    appendOperation('完成接诊', ticket)
    ElMessage.success('已完成接诊')
    await loadWorkbench()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '完成接诊失败')
  } finally {
    completing.value = false
  }
}

function formatQueueStatus(status?: string, fallback = '-') {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '已过号',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : fallback
}

function queueStatusType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
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

function statusPillClass(status?: string) {
  switch (status) {
    case 'CALLING':
      return 'status-pill--success'
    case 'MISSED':
      return 'status-pill--warn'
    case 'COMPLETED':
      return 'status-pill--neutral'
    default:
      return ''
  }
}

function formatTicketQueueStatus(ticket?: QueueTicket | null, fallback = '-') {
  return formatDisplayQueueStatus(ticket, fallback)
}

function queueStatusTypeByTicket(ticket?: QueueTicket | null): 'success' | 'warning' | 'info' | 'danger' {
  return getQueueStatusTagType(ticket)
}

function statusPillClassByTicket(ticket?: QueueTicket | null) {
  switch (getQueueDisplayStatus(ticket)) {
    case 'CALLING':
      return 'status-pill--success'
    case 'WAITING_FOR_CONSULTATION':
      return 'status-pill--waiting'
    case 'MISSED':
      return 'status-pill--warn'
    case 'COMPLETED':
      return 'status-pill--neutral'
    default:
      return ''
  }
}

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatMinutes(value?: number) {
  if (typeof value !== 'number' || value < 0) {
    return '-'
  }
  return `${value} 分钟`
}

function formatRoomLabel(targetRoomId?: number | null) {
  return targetRoomId ? `诊室 ${targetRoomId}` : '未分配诊室'
}

function formatRoomDoctor(roomName?: string, doctorName?: string, targetRoomId?: number | null) {
  const roomText = roomName || formatRoomLabel(targetRoomId)
  return doctorName ? `${roomText} / ${doctorName}` : roomText
}

function formatAiRiskLevel(riskLevel?: string) {
  switch (riskLevel) {
    case 'CRITICAL':
      return '危急'
    case 'HIGH':
      return '高'
    case 'MEDIUM':
      return '中'
    case 'LOW':
      return '低'
    default:
      return riskLevel || '-'
  }
}

function formatSource(sourceType?: string, sourceRemark?: string) {
  if (!sourceType && !sourceRemark) {
    return '-'
  }
  const labelMap: Record<string, string> = {
    TRIAGE_AUTO: '分诊自动入队',
    KIOSK: '院内自助机取号',
    MANUAL_REPAIR: '异常补录/管理员修复'
  }
  const sourceText = sourceType ? labelMap[sourceType] || sourceType : '-'
  return sourceRemark ? `${sourceText} / ${sourceRemark}` : sourceText
}

onMounted(() => {
  void loadWorkbench()
})
</script>

<style scoped>
.queue-call-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions,
.toolbar,
.toolbar__group,
.toolbar-foot,
.focus-card__meta,
.focus-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.toolbar--split {
  justify-content: space-between;
}

.toolbar__group--meta {
  justify-content: flex-end;
}

.toolbar__timestamp,
.table-hint,
.summary-card span,
.summary-card small,
.focus-card__eyebrow,
.focus-card__desc,
.preview-item span,
.operation-item {
  color: var(--muted-color);
}

.control-card {
  border: 1px solid rgba(8, 145, 178, 0.14);
}

.toolbar-foot {
  margin-top: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.9) 0%, rgba(255, 255, 255, 1) 100%);
  border: 1px solid rgba(34, 211, 238, 0.22);
}

.summary-card strong {
  display: block;
  margin: 10px 0 6px;
  color: #164e63;
  font-size: 24px;
  line-height: 1.2;
}

.summary-card--accent {
  background: linear-gradient(180deg, rgba(8, 145, 178, 0.12) 0%, rgba(236, 254, 255, 0.95) 100%);
  border-color: rgba(8, 145, 178, 0.22);
}

.hero-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
}

.focus-card__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.9fr);
  gap: 20px;
  align-items: stretch;
}

.focus-card__main,
.focus-card__aside {
  display: flex;
  flex-direction: column;
}

.focus-card__main {
  gap: 14px;
}

.focus-card__aside {
  gap: 12px;
}

.focus-panel {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(248, 250, 252, 0.92);
}

.focus-panel__header {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.focus-kv-grid,
.focus-metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.focus-kv-item,
.focus-metric-card {
  padding: 12px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.focus-kv-item span,
.focus-metric-card span,
.focus-metric-card small,
.focus-checklist__item p {
  color: var(--muted-color);
}

.focus-kv-item span,
.focus-metric-card span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
}

.focus-kv-item strong,
.focus-metric-card strong,
.focus-checklist__item strong {
  color: #0f172a;
}

.focus-metric-card strong {
  display: block;
  margin-bottom: 4px;
  font-size: 18px;
}

.focus-checklist {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.focus-checklist__item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.focus-checklist__item.is-active {
  border-color: rgba(8, 145, 178, 0.28);
  background: linear-gradient(180deg, rgba(236, 254, 255, 0.88) 0%, rgba(255, 255, 255, 1) 100%);
}

.focus-checklist__dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #cbd5e1;
}

.focus-checklist__item.is-active .focus-checklist__dot {
  background: #0891b2;
}

.focus-checklist__item p {
  margin: 4px 0 0;
  line-height: 1.6;
}

.focus-card__topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.focus-card__ticket {
  margin-top: 8px;
  font-size: 32px;
  line-height: 1.1;
  font-weight: 700;
}

.focus-card__status {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.focus-card__name {
  margin-top: 8px;
  font-size: 26px;
  line-height: 1.2;
  font-weight: 700;
}

.focus-card__complaint {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(240, 253, 250, 0.92), rgba(255, 255, 255, 0.98));
  border: 1px solid rgba(20, 184, 166, 0.14);
}

.focus-card__complaint span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0f766e;
}

.focus-card__complaint p {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f59e0b;
}

.status-pill,
.data-pill {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 13px;
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
}

.status-pill--success,
.status-pill--success.status-dot {
  background: #16a34a;
  color: #fff;
}

.status-pill--waiting,
.status-pill--waiting.status-dot {
  background: #f59e0b;
  color: #fff;
}

.status-pill--warn,
.status-pill--warn.status-dot {
  background: #f97316;
  color: #fff;
}

.status-pill--neutral,
.status-pill--neutral.status-dot {
  background: #64748b;
  color: #fff;
}

.preview-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.preview-stats article,
.preview-item,
.operation-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.preview-stats strong,
.preview-item strong {
  display: block;
  margin-top: 6px;
}

.preview-list,
.operation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-list {
  margin-top: 16px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.side-grid,
.patient-cell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.patient-cell span {
  color: var(--muted-color);
  font-size: 13px;
}

.strategy-list {
  display: grid;
  gap: 10px;
}

.strategy-item span {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--muted-color);
}

.strategy-item strong {
  font-size: 15px;
  color: #0f172a;
}

.strategy-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.card-header-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header-inline__meta {
  display: inline-flex;
  gap: 10px;
  align-items: center;
}

@media (max-width: 1200px) {
  .summary-grid,
  .hero-grid,
  .content-grid,
  .preview-stats,
  .focus-card__layout,
  .focus-kv-grid,
  .focus-metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
