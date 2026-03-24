<template>
  <div class="visit-workspace">
    <PageHeader
      title="就诊建档"
      :eyebrow="surfaceBasePath === '/admin' ? '管理后台' : '导诊工作台'"
      :description="
        surfaceBasePath === '/admin'
          ? '先检索患者，再完成建档与到诊登记；后台端不再提供分诊评估入口。'
          : '先检索患者，再完成建档与到诊登记，确保后续分诊和叫号信息连续。'
      "
    />

    <section class="overview-hero triage-overview">
      <div class="overview-hero__panel">
        <span class="section-kicker">建档流程</span>
        <h2>优先确认患者信息，再创建就诊记录。</h2>
        <p>
          建档完成后可继续登记到诊，并进入对应就诊详情页。
          <template v-if="canGoAssessment">工作台端仍可继续进入分诊评估。</template>
          <template v-else>后台端仅保留建档与详情查看能力。</template>
        </p>
      </div>
      <div class="overview-hero__aside">
        <div class="overview-list">
          <div class="overview-list__item">
            <span>检索结果</span>
            <strong>{{ patients.length }}</strong>
            <small>可按姓名、手机号或证件号查询患者。</small>
          </div>
          <div class="overview-list__item">
            <span>当前患者</span>
            <strong>{{ selectedPatient?.patientName || '待选择' }}</strong>
            <small>{{ selectedPatient ? `患者编号 ${selectedPatient.patientNo}` : '请先选择患者后再建档。' }}</small>
          </div>
          <div class="overview-list__item">
            <span>当前就诊</span>
            <strong>{{ activeVisitNo || '未创建' }}</strong>
            <small>{{ activeVisitStatusText }}</small>
          </div>
        </div>
      </div>
    </section>

    <div class="content-grid">
      <el-card>
        <template #header>建档表单</template>
        <el-form label-position="top">
          <el-form-item label="搜索患者">
            <div class="toolbar">
              <el-input
                v-model="patientKeyword"
                placeholder="姓名 / 手机号 / 证件号"
                clearable
                @keyup.enter="searchPatients"
              />
              <el-button :loading="patientLoading" @click="searchPatients">查询</el-button>
            </div>
          </el-form-item>

          <el-form-item label="患者">
            <el-select
              v-model="form.patientId"
              placeholder="请选择患者"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="patient in patients"
                :key="patient.id"
                :label="`${patient.patientName} (${patient.patientNo})`"
                :value="patient.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="主诉">
            <el-input
              v-model="form.chiefComplaint"
              type="textarea"
              :rows="4"
              placeholder="请输入患者主诉"
            />
          </el-form-item>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="handleCreateVisit">
              创建就诊
            </el-button>
            <el-button :disabled="!canArriveVisit" :loading="arriving" @click="handleArrive">
              登记到诊
            </el-button>
            <el-button :disabled="!activeVisitId" @click="goVisitDetail">
              查看详情
            </el-button>
            <el-button
              v-if="canGoAssessment"
              type="success"
              plain
              :disabled="!activeVisitId"
              @click="goAssessment"
            >
              去分诊评估
            </el-button>
          </div>
        </el-form>
      </el-card>

      <el-card>
        <template #header>患者摘要</template>
        <div v-if="selectedPatient" class="detail-list">
          <div class="detail-list__item">
            <span>患者信息</span>
            <strong>{{ selectedPatient.patientName }} · {{ selectedPatient.patientNo }}</strong>
            <small>{{ selectedPatient.gender || '性别未登记' }} · {{ selectedPatient.phone || '电话未登记' }}</small>
          </div>
          <div class="detail-list__item">
            <span>过敏史</span>
            <strong>{{ selectedPatient.allergyHistory || '未登记' }}</strong>
            <small>请在建档与分诊时留意患者历史风险信息。</small>
          </div>
          <div class="detail-list__item">
            <span>特殊标签</span>
            <strong>{{ selectedPatient.specialTags || '无' }}</strong>
            <small>如存在重点照护标签，请及时同步导诊与分诊人员。</small>
          </div>
          <div class="detail-list__item">
            <span>当前状态</span>
            <strong>{{ formatVisitStatus(activeVisitStatus || selectedPatient.currentStatus) }}</strong>
            <small>{{ activeVisitNo ? `当前就诊号 ${activeVisitNo}` : '当前尚无就诊记录。' }}</small>
          </div>
        </div>
        <el-empty v-else description="请选择患者" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientList } from '@/api/patient'
import { arriveVisit, createVisit } from '@/api/visit'
import type { Patient } from '@/types/patient'
import type { Visit } from '@/types/visit'

const route = useRoute()
const router = useRouter()

const patientLoading = ref(false)
const submitting = ref(false)
const arriving = ref(false)
const patientKeyword = ref((route.query.keyword as string) || '')
const patients = ref<Patient[]>([])
const createdVisit = ref<Visit | null>(null)

const surfaceBasePath = computed(() =>
  route.path.startsWith('/admin') ? '/admin' : '/workstation'
)
const canGoAssessment = computed(() => surfaceBasePath.value !== '/admin')

const form = reactive({
  patientId: undefined as number | undefined,
  chiefComplaint: ''
})

const selectedPatient = computed(
  () => patients.value.find((item) => item.id === form.patientId) || null
)
const activeVisitId = computed<number | string | null>(
  () => createdVisit.value?.id ?? selectedPatient.value?.currentVisitId ?? null
)
const activeVisitNo = computed(
  () => createdVisit.value?.visitNo ?? selectedPatient.value?.currentVisitNo ?? ''
)
const activeVisitStatus = computed(
  () => createdVisit.value?.status ?? selectedPatient.value?.currentStatus ?? ''
)
const canArriveVisit = computed(
  () => Boolean(activeVisitId.value) && activeVisitStatus.value === 'REGISTERED'
)
const activeVisitStatusText = computed(() => {
  if (!activeVisitId.value) {
    return '创建成功后将跳转到就诊详情。'
  }
  return `当前状态：${formatVisitStatus(activeVisitStatus.value)}`
})

async function searchPatients() {
  patientLoading.value = true
  try {
    patients.value = await getPatientList(patientKeyword.value || undefined)
    const preferredPatientId = Number(route.query.patientId || 0)

    if (preferredPatientId) {
      const matched = patients.value.find((item) => item.id === preferredPatientId)
      if (matched) {
        form.patientId = matched.id
      }
    }

    if (!form.patientId && patients.value.length === 1) {
      form.patientId = patients.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者列表失败')
  } finally {
    patientLoading.value = false
  }
}

function syncSelectedPatientVisit(visit: Visit) {
  if (!selectedPatient.value) {
    return
  }
  selectedPatient.value.currentVisitId = Number(visit.id)
  selectedPatient.value.currentVisitNo = visit.visitNo
  selectedPatient.value.currentStatus = visit.status
  selectedPatient.value.currentDeptId = Number(visit.currentDeptId || 0) || undefined
  selectedPatient.value.currentRoomId = Number(visit.currentRoomId || 0) || undefined
}

async function handleCreateVisit() {
  if (!form.patientId || !form.chiefComplaint.trim()) {
    ElMessage.warning('请选择患者并填写主诉')
    return
  }

  submitting.value = true
  try {
    createdVisit.value = await createVisit({
      patientId: form.patientId,
      chiefComplaint: form.chiefComplaint.trim()
    })
    syncSelectedPatientVisit(createdVisit.value)
    ElMessage.success('就诊创建成功')
    await goVisitDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建就诊失败')
  } finally {
    submitting.value = false
  }
}

async function handleArrive() {
  if (!activeVisitId.value) {
    return
  }

  arriving.value = true
  try {
    createdVisit.value = await arriveVisit(activeVisitId.value)
    syncSelectedPatientVisit(createdVisit.value)
    ElMessage.success('已登记到诊')
    await goVisitDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '到诊登记失败')
  } finally {
    arriving.value = false
  }
}

async function goVisitDetail() {
  if (!activeVisitId.value) {
    return
  }
  await router.push(`${surfaceBasePath.value}/visits/${activeVisitId.value}`)
}

async function goAssessment() {
  if (!canGoAssessment.value || !activeVisitId.value) {
    return
  }
  await router.push({
    path: `${surfaceBasePath.value}/triage/assessments/new`,
    query: { visitId: String(activeVisitId.value) }
  })
}

function formatVisitStatus(status?: string) {
  const map: Record<string, string> = {
    REGISTERED: '已挂号',
    ARRIVED: '已到诊',
    TRIAGED: '已分诊',
    QUEUING: '排队中',
    IN_TREATMENT: '就诊中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return status ? map[status] || status : '未就诊'
}

onMounted(() => {
  void searchPatients()
})
</script>
