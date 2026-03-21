<template>
  <div class="patient-workstation-page">
    <PageHeader title="患者查询" eyebrow="工作流承接" description="快速检索患者，读取当前状态，并跳转到建档、就诊详情或分诊评估。" />

    <el-card class="mb-16">
      <div class="toolbar toolbar--split">
        <div class="toolbar__group">
          <el-input
            v-model="keyword"
            placeholder="姓名 / 手机号 / 证件号"
            style="width: 320px"
            clearable
            @keyup.enter="loadPatients"
          />
          <el-button type="primary" :loading="loading" @click="loadPatients">查询</el-button>
        </div>
        <div class="toolbar__group toolbar__group--meta">
          <el-tag effect="plain">当前选中 {{ selectedPatient?.patientName || '未选择' }}</el-tag>
          <el-tag effect="plain">就诊建档 → 分诊评估 → 叫号</el-tag>
        </div>
      </div>
    </el-card>

    <section class="summary-grid">
      <article class="summary-card">
        <span>检索结果</span>
        <strong>{{ patients.length }}</strong>
        <small>可承接后续操作</small>
      </article>
      <article class="summary-card">
        <span>待建档</span>
        <strong>{{ noVisitCount }}</strong>
        <small>尚未生成当前就诊单</small>
      </article>
      <article class="summary-card">
        <span>排队中</span>
        <strong>{{ queueingCount }}</strong>
        <small>可只读查看当前排队状态</small>
      </article>
      <article class="summary-card summary-card--accent">
        <span>就诊中</span>
        <strong>{{ inTreatmentCount }}</strong>
        <small>医生链路已接管</small>
      </article>
    </section>

    <div class="content-grid">
      <el-card>
        <template #header>
          <div class="card-header-inline">
            <span>患者查询结果</span>
            <div class="card-header-inline__meta">
              <el-tag effect="plain">{{ patients.length }} 人</el-tag>
              <span class="table-hint">单击一行可查看右侧状态承接摘要</span>
            </div>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="patients"
          row-key="id"
          highlight-current-row
          :current-row-key="selectedPatient?.id"
          @row-click="handleSelectPatient"
        >
          <el-table-column prop="patientNo" label="编号" min-width="150" />
          <el-table-column prop="patientName" label="姓名" min-width="120" />
          <el-table-column prop="gender" label="性别" width="90" />
          <el-table-column label="当前状态" min-width="180">
            <template #default="{ row }">
              <div class="status-stack">
                <el-tag :type="visitStatusType(row.currentStatus)" effect="plain">
                  {{ formatVisitStatus(row.currentStatus) }}
                </el-tag>
                <small class="muted-meta">{{ row.currentVisitNo ? `就诊单 ${row.currentVisitNo}` : '尚未建档' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="当前科室/诊室" min-width="170">
            <template #default="{ row }">
              <div class="status-stack">
                <span>{{ row.currentDeptId ? `科室 ${row.currentDeptId}` : '未分配科室' }}</span>
                <small class="muted-meta">{{ row.currentRoomId ? `诊室 ${row.currentRoomId}` : '未分配诊室' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="280" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" @click.stop="goVisitCreate(row)">去建档</el-button>
                <el-button link type="primary" :disabled="!row.currentVisitId" @click.stop="goVisitDetail(row)">就诊详情</el-button>
                <el-button link type="primary" :disabled="!row.currentVisitId" @click.stop="goAssessment(row)">分诊评估</el-button>
                <el-button link @click.stop="goDetail(row)">状态详情</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="side-grid">
        <el-card>
          <template #header>当前患者状态</template>
          <el-descriptions v-if="selectedPatient" :column="1" border>
            <el-descriptions-item label="患者">{{ selectedPatient.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="患者编号">{{ selectedPatient.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">{{ formatVisitStatus(selectedPatient.currentStatus) }}</el-descriptions-item>
            <el-descriptions-item label="当前就诊单">{{ selectedPatient.currentVisitNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前科室 / 诊室">
              {{ formatDeptRoom(selectedPatient.currentDeptId, selectedPatient.currentRoomId) }}
            </el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ selectedPatient.phone || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="请选择患者" />
        </el-card>

        <el-card>
          <template #header>推荐下一步</template>
          <div class="action-list">
            <div v-if="!selectedPatient" class="action-item">选中患者后，系统会给出当前链路建议。</div>
            <div v-for="item in nextStepSuggestions" :key="item.title" class="action-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
            </div>
          </div>
        </el-card>

        <el-card>
          <template #header>当前排队状态（只读）</template>
          <el-skeleton :loading="queueLoading" animated>
            <template #default>
              <el-descriptions v-if="selectedQueueTicket" :column="1" border>
                <el-descriptions-item label="票号">{{ selectedQueueTicket.ticketNo }}</el-descriptions-item>
                <el-descriptions-item label="队列状态">{{ formatTicketStatus(selectedQueueTicket) }}</el-descriptions-item>
                <el-descriptions-item label="分诊等级">{{ formatTriageLevel(selectedQueueTicket.triageLevel) }}</el-descriptions-item>
                <el-descriptions-item label="当前排位 / 前方人数">
                  {{ selectedQueueTicket.rank ?? '-' }} / {{ selectedQueueTicket.waitingCount ?? '-' }}
                </el-descriptions-item>
                <el-descriptions-item label="已等待">{{ formatMinutes(selectedQueueTicket.waitedMinutes) }}</el-descriptions-item>
                <el-descriptions-item label="科室 / 诊室">
                  {{ selectedQueueTicket.deptName || `科室 ${selectedQueueTicket.deptId}` }} /
                  {{ selectedQueueTicket.roomName || (selectedQueueTicket.roomId ? `诊室 ${selectedQueueTicket.roomId}` : '待分配诊室') }}
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="当前未读取到排队票据，可进入状态详情查看流程状态。" />
            </template>
          </el-skeleton>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientList } from '@/api/patient'
import { getActiveTickets } from '@/api/queue'
import type { Patient } from '@/types/patient'
import type { QueueTicket } from '@/types/queue'
import { formatQueueStatus as formatDisplayQueueStatus } from '@/utils/queueStatus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const queueLoading = ref(false)
const keyword = ref((route.query.keyword as string) || '')
const patients = ref<Patient[]>([])
const selectedPatient = ref<Patient | null>(null)
const selectedQueueTicket = ref<QueueTicket | null>(null)

const noVisitCount = computed(() => patients.value.filter((item) => !item.currentVisitId).length)
const queueingCount = computed(() => patients.value.filter((item) => item.currentStatus === 'QUEUING').length)
const inTreatmentCount = computed(() => patients.value.filter((item) => item.currentStatus === 'IN_TREATMENT').length)

const nextStepSuggestions = computed(() => {
  if (!selectedPatient.value) {
    return []
  }

  if (!selectedPatient.value.currentVisitId) {
    return [
      {
        title: '优先去建档',
        description: '当前患者尚未生成就诊记录，应先完成建档后再进入后续分诊流程。'
      },
      {
        title: '补充身份与风险信息',
        description: '建档前请再次核对联系方式、过敏史和特殊标签，避免后续承接信息缺失。'
      }
    ]
  }

  if (selectedPatient.value.currentStatus === 'REGISTERED' || selectedPatient.value.currentStatus === 'ARRIVED') {
    return [
      {
        title: '查看就诊详情',
        description: '当前已生成就诊记录，建议先确认主诉和到诊状态。'
      },
      {
        title: '进入分诊评估',
        description: '完成生命体征和症状标签录入后，系统会生成分诊结果并尝试自动入队。'
      }
    ]
  }

  if (selectedPatient.value.currentStatus === 'TRIAGED' || selectedPatient.value.currentStatus === 'QUEUING') {
    return [
      {
        title: '查看只读排队状态',
        description: '患者已进入分诊或排队链路，可在本页右侧查看当前排队状态，不在此页执行叫号动作。'
      },
      {
        title: '必要时回看就诊详情',
        description: '如需核对主诉、到诊或分诊前置状态，可进入就诊详情页继续查看。'
      }
    ]
  }

  return [
    {
      title: '查看当前流程状态',
      description: '当前患者已进入后续链路，建议以状态详情页为主进行只读核查。'
    }
  ]
})

function goVisitCreate(patient: Patient) {
  void router.push({
    path: '/workstation/visits/new',
    query: { patientId: String(patient.id) }
  })
}

function goVisitDetail(patient: Patient) {
  if (!patient.currentVisitId) {
    return
  }
  void router.push(`/workstation/visits/${patient.currentVisitId}`)
}

function goAssessment(patient: Patient) {
  if (!patient.currentVisitId) {
    return
  }
  void router.push({
    path: '/workstation/triage/assessments/new',
    query: { visitId: String(patient.currentVisitId) }
  })
}

function goDetail(patient: Patient) {
  void router.push(`/workstation/patients/${patient.id}`)
}

async function loadPatients() {
  loading.value = true
  try {
    patients.value = await getPatientList(keyword.value || undefined)
    const preferredId = selectedPatient.value?.id
    const nextSelected =
      patients.value.find((item) => item.id === preferredId) ||
      patients.value.find((item) => String(item.id) === String(route.query.patientId || '')) ||
      patients.value[0] ||
      null
    await handleSelectPatient(nextSelected)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者列表失败')
  } finally {
    loading.value = false
  }
}

async function loadQueueSnapshot(patient: Patient | null) {
  selectedQueueTicket.value = null
  if (!patient?.currentVisitId || (!patient.currentDeptId && !patient.currentRoomId)) {
    return
  }

  queueLoading.value = true
  try {
    const tickets = await getActiveTickets(patient.currentDeptId ?? undefined, patient.currentRoomId ?? undefined)
    selectedQueueTicket.value =
      tickets.find((item) => item.visitId === patient.currentVisitId) ||
      tickets.find((item) => item.patientId === patient.id) ||
      null
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取当前排队状态失败')
  } finally {
    queueLoading.value = false
  }
}

async function handleSelectPatient(patient: Patient | null) {
  selectedPatient.value = patient
  await loadQueueSnapshot(patient)
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

function visitStatusType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'IN_TREATMENT':
      return 'success'
    case 'QUEUING':
    case 'TRIAGED':
    case 'ARRIVED':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
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

function formatTriageLevel(level?: number) {
  return level ? `${level} 级` : '-'
}

function formatMinutes(value?: number) {
  if (typeof value !== 'number' || value < 0) {
    return '-'
  }
  return `${value} 分钟`
}

function formatDeptRoom(deptId?: number, roomId?: number) {
  const deptText = deptId ? `科室 ${deptId}` : '未分配科室'
  const roomText = roomId ? `诊室 ${roomId}` : '未分配诊室'
  return `${deptText} / ${roomText}`
}

onMounted(() => {
  void loadPatients()
})
</script>

<style scoped>
.patient-workstation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar,
.toolbar__group,
.table-actions,
.card-header-inline,
.card-header-inline__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.toolbar--split,
.card-header-inline {
  justify-content: space-between;
}

.toolbar__group--meta {
  justify-content: flex-end;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.summary-card strong {
  display: block;
  margin: 10px 0 6px;
  font-size: 24px;
  line-height: 1.2;
  color: var(--title-color);
}

.summary-card span,
.summary-card small,
.muted-meta,
.table-hint,
.action-item span {
  color: var(--muted-color);
}

.summary-card--accent {
  background: linear-gradient(180deg, rgba(8, 145, 178, 0.12), rgba(255, 255, 255, 1));
  border-color: rgba(8, 145, 178, 0.2);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.95fr);
  gap: 16px;
}

.side-grid,
.status-stack,
.action-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-stack {
  gap: 6px;
}

.action-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.98);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.action-item strong,
.action-item span {
  display: block;
}

.action-item span {
  margin-top: 6px;
  line-height: 1.7;
}

@media (max-width: 1200px) {
  .summary-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
