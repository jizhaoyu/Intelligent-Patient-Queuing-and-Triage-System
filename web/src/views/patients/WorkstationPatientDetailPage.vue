<template>
  <div class="patient-workstation-detail-page">
    <PageHeader title="患者状态详情" eyebrow="流程状态页" description="查看患者当前就诊、分诊与排队状态，并承接下一步操作。">
      <template #actions>
        <el-button @click="goList">返回患者查询</el-button>
      </template>
    </PageHeader>

    <el-skeleton :loading="loading" animated>
      <template #default>
        <template v-if="patient">
          <section class="hero-grid">
            <el-card class="hero-card">
              <div class="hero-card__eyebrow">当前患者</div>
              <div class="hero-card__name">{{ patient.patientName || patient.patientNo || '-' }}</div>
              <div class="hero-card__meta">
                <span class="pill">编号 {{ patient.patientNo || '-' }}</span>
                <span class="pill">{{ formatVisitStatus(patient.currentStatus) }}</span>
                <span class="pill">{{ formatDeptRoom(patient.currentDeptId, patient.currentRoomId) }}</span>
              </div>
              <p class="hero-card__desc">{{ workflowHint }}</p>
              <div class="hero-card__actions">
                <el-button type="primary" @click="goVisitCreate">去建档</el-button>
                <el-button :disabled="!patient.currentVisitId" @click="goVisitDetail">查看就诊详情</el-button>
                <el-button :disabled="!patient.currentVisitId" @click="goAssessment">去分诊评估</el-button>
              </div>
            </el-card>

            <el-card>
              <template #header>当前流程摘要</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="当前就诊单">{{ patient.currentVisitNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前状态">{{ formatVisitStatus(patient.currentStatus) }}</el-descriptions-item>
                <el-descriptions-item label="当前科室 / 诊室">
                  {{ formatDeptRoom(patient.currentDeptId, patient.currentRoomId) }}
                </el-descriptions-item>
                <el-descriptions-item label="状态更新时间">{{ patient.statusUpdatedTime || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ patient.phone || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </section>

          <div class="detail-grid">
            <el-card>
              <template #header>患者基本信息</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="患者编号">{{ patient.patientNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ patient.patientName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ patient.gender || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ patient.phone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{ patient.birthDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="证件号">{{ patient.idCard || '-' }}</el-descriptions-item>
                <el-descriptions-item label="过敏史" :span="2">{{ patient.allergyHistory || '未登记' }}</el-descriptions-item>
                <el-descriptions-item label="特殊标签" :span="2">{{ patient.specialTags || '无' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <div class="side-grid">
              <el-card>
                <template #header>推荐下一步</template>
                <div class="action-list">
                  <div v-for="item in nextActions" :key="item.title" class="action-item">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.description }}</span>
                  </div>
                </div>
              </el-card>

              <el-card>
                <template #header>当前排队状态（只读）</template>
                <el-skeleton :loading="queueLoading" animated>
                  <template #default>
                    <el-descriptions v-if="queueTicket" :column="1" border>
                      <el-descriptions-item label="票号">{{ queueTicket.ticketNo }}</el-descriptions-item>
                      <el-descriptions-item label="状态">{{ formatTicketStatus(queueTicket) }}</el-descriptions-item>
                      <el-descriptions-item label="分诊等级">{{ formatTriageLevel(queueTicket.triageLevel) }}</el-descriptions-item>
                      <el-descriptions-item label="排位 / 前方人数">
                        {{ queueTicket.rank ?? '-' }} / {{ queueTicket.waitingCount ?? '-' }}
                      </el-descriptions-item>
                      <el-descriptions-item label="已等待">{{ formatMinutes(queueTicket.waitedMinutes) }}</el-descriptions-item>
                      <el-descriptions-item label="科室 / 诊室">
                        {{ queueTicket.deptName || `科室 ${queueTicket.deptId}` }} /
                        {{ queueTicket.roomName || (queueTicket.roomId ? `诊室 ${queueTicket.roomId}` : '待分配诊室') }}
                      </el-descriptions-item>
                    </el-descriptions>
                    <el-empty v-else description="当前未读取到排队票据；如患者已进入医生接诊链路，请以状态信息为准。" />
                  </template>
                </el-skeleton>
              </el-card>
            </div>
          </div>
        </template>

        <el-empty v-else description="未找到患者信息" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientById } from '@/api/patient'
import { getActiveTickets } from '@/api/queue'
import type { Patient } from '@/types/patient'
import type { QueueTicket } from '@/types/queue'
import { formatQueueStatus as formatDisplayQueueStatus } from '@/utils/queueStatus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const queueLoading = ref(false)
const patient = ref<Patient | null>(null)
const queueTicket = ref<QueueTicket | null>(null)

const workflowHint = computed(() => {
  if (!patient.value?.currentVisitId) {
    return '当前患者尚未生成就诊记录，建议先完成建档，再进入分诊与排队链路。'
  }
  if (patient.value.currentStatus === 'REGISTERED' || patient.value.currentStatus === 'ARRIVED') {
    return '当前患者已建档但尚未完成分诊，建议先查看就诊详情，再进入分诊评估。'
  }
  if (patient.value.currentStatus === 'TRIAGED' || patient.value.currentStatus === 'QUEUING') {
    return '当前患者已进入分诊或排队链路，本页仅做状态承接，不在此处执行叫号。'
  }
  if (patient.value.currentStatus === 'IN_TREATMENT') {
    return '当前患者已进入医生接诊链路，接诊动作统一在诊室叫号页执行。'
  }
  return '请根据当前状态选择下一步操作。'
})

const nextActions = computed(() => {
  if (!patient.value) {
    return []
  }

  if (!patient.value.currentVisitId) {
    return [
      {
        title: '去建档',
        description: '当前尚无就诊记录，应先创建就诊单，后续才能进入分诊和排队流程。'
      }
    ]
  }

  const actions = [
    {
      title: '查看就诊详情',
      description: '核对主诉、到诊信息与当前就诊状态。'
    },
    {
      title: '去分诊评估',
      description: '录入生命体征和症状标签，生成分诊结果并自动尝试入队。'
    }
  ]

  if (patient.value.currentStatus === 'QUEUING' || patient.value.currentStatus === 'IN_TREATMENT') {
    actions.unshift({
      title: '查看流程状态',
      description: '患者已进入排队或接诊链路，本页仅展示只读状态，不提供叫号操作。'
    })
  }

  return actions
})

function goList() {
  void router.push('/workstation/patients')
}

function goVisitCreate() {
  if (!patient.value) {
    return
  }
  void router.push({
    path: '/workstation/visits/new',
    query: { patientId: String(patient.value.id) }
  })
}

function goVisitDetail() {
  if (!patient.value?.currentVisitId) {
    return
  }
  void router.push(`/workstation/visits/${patient.value.currentVisitId}`)
}

function goAssessment() {
  if (!patient.value?.currentVisitId) {
    return
  }
  void router.push({
    path: '/workstation/triage/assessments/new',
    query: { visitId: String(patient.value.currentVisitId) }
  })
}

async function loadPatient() {
  loading.value = true
  try {
    patient.value = await getPatientById(String(route.params.id || ''))
    await loadQueueTicket()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者详情失败')
  } finally {
    loading.value = false
  }
}

async function loadQueueTicket() {
  queueTicket.value = null
  if (!patient.value?.currentVisitId || (!patient.value.currentDeptId && !patient.value.currentRoomId)) {
    return
  }

  queueLoading.value = true
  try {
    const tickets = await getActiveTickets(patient.value.currentDeptId ?? undefined, patient.value.currentRoomId ?? undefined)
    queueTicket.value =
      tickets.find((item) => item.visitId === patient.value?.currentVisitId) ||
      tickets.find((item) => item.patientId === patient.value?.id) ||
      null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取排队状态失败')
  } finally {
    queueLoading.value = false
  }
}

function formatVisitStatus(status?: string) {
  const map: Record<string, string> = {
    REGISTERED: '已登记',
    ARRIVED: '已到诊',
    TRIAGED: '已分诊',
    QUEUING: '排队中',
    IN_TREATMENT: '就诊中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '未就诊'
}

function formatTicketStatus(ticket?: QueueTicket | null) {
  return formatDisplayQueueStatus(ticket)
}

function formatQueueStatus(status?: string) {
  const map: Record<string, string> = {
    WAITING: '候诊中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '已过号',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '-'
}

function formatDeptRoom(deptId?: number, roomId?: number) {
  const deptText = deptId ? `科室 ${deptId}` : '未分配科室'
  const roomText = roomId ? `诊室 ${roomId}` : '未分配诊室'
  return `${deptText} / ${roomText}`
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

onMounted(() => {
  void loadPatient()
})
</script>

<style scoped>
.patient-workstation-detail-page {
  display: grid;
  gap: 20px;
}

.patient-workstation-detail-page :deep(.el-card) {
  border: none;
  border-radius: 26px;
  overflow: hidden;
  box-shadow: 0 22px 48px rgba(6, 95, 70, 0.08);
}

.patient-workstation-detail-page :deep(.el-card__header) {
  padding: 20px 22px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(247, 253, 250, 0.98), rgba(255, 255, 255, 0.82));
}

.patient-workstation-detail-page :deep(.el-card__body) {
  padding: 22px;
}

.patient-workstation-detail-page :deep(.el-descriptions__body .el-descriptions__table) {
  overflow: hidden;
  border-radius: 20px;
}

.patient-workstation-detail-page :deep(.el-descriptions__label.el-descriptions__cell) {
  min-width: 120px;
  background: rgba(236, 253, 245, 0.84);
  color: var(--muted-color);
  font-weight: 700;
}

.patient-workstation-detail-page :deep(.el-descriptions__content.el-descriptions__cell) {
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-color);
}

.hero-grid,
.detail-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 18px;
}

.hero-card {
  position: relative;
  overflow: hidden;
  color: #f8fafc;
  background:
    radial-gradient(circle at top right, rgba(34, 211, 238, 0.24), transparent 14rem),
    linear-gradient(155deg, rgba(6, 95, 70, 0.96), rgba(8, 47, 73, 0.94));
}

.hero-card::after {
  content: "";
  position: absolute;
  inset: auto -48px -56px auto;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.16), transparent 72%);
}

.hero-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  padding: 26px;
}

.hero-card__eyebrow,
.hero-card__desc,
.action-item span {
  color: rgba(226, 232, 240, 0.8);
}

.hero-card__name {
  margin-top: 8px;
  font-size: clamp(30px, 3vw, 38px);
  line-height: 1.06;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #f8fafc;
}

.hero-card__meta,
.hero-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.hero-card__desc {
  margin: 16px 0 0;
  line-height: 1.7;
}

.pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: #ecfeff;
  font-size: 13px;
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.side-grid,
.action-list {
  display: grid;
  gap: 16px;
}

.action-item {
  position: relative;
  overflow: hidden;
  padding: 16px 18px 16px 22px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 1));
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.action-item::before {
  content: "";
  position: absolute;
  top: 14px;
  bottom: 14px;
  left: 0;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #059669, #22d3ee);
}

.action-item strong,
.action-item span {
  display: block;
}

.action-item strong {
  color: var(--title-color);
}

.action-item span {
  margin-top: 6px;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .hero-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .patient-workstation-detail-page :deep(.el-card__body),
  .patient-workstation-detail-page :deep(.el-card__header) {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>
