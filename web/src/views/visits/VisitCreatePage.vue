<template>
  <div class="visit-workspace">
    <PageHeader title="就诊建档" eyebrow="导诊工作台" description="先检索患者，再完成建档与到诊登记，确保后续分诊和叫号信息连续。" />

    <section class="overview-hero triage-overview">
      <div class="overview-hero__panel">
        <span class="section-kicker">建档流程</span>
        <h2>优先确认患者信息，再创建就诊记录。</h2>
        <p>建档完成后可继续登记到诊，并进入对应就诊详情页，支撑后续分诊评估和叫号流程。</p>
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
            <span>当前建档</span>
            <strong>{{ currentVisitId || '未创建' }}</strong>
            <small>{{ currentVisitId ? '已生成就诊记录，可继续登记到诊。' : '创建成功后系统将跳转到就诊详情。' }}</small>
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
              <el-input v-model="patientKeyword" placeholder="姓名 / 手机号 / 证件号" @keyup.enter="searchPatients" />
              <el-button :loading="patientLoading" @click="searchPatients">查询</el-button>
            </div>
          </el-form-item>
          <el-form-item label="患者">
            <el-select v-model="form.patientId" placeholder="请选择患者" filterable style="width: 100%">
              <el-option v-for="patient in patients" :key="patient.id" :label="`${patient.patientName} (${patient.patientNo})`" :value="patient.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="主诉">
            <el-input v-model="form.chiefComplaint" type="textarea" :rows="4" placeholder="请输入患者主诉" />
          </el-form-item>
          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="handleCreateVisit">创建就诊</el-button>
            <el-button :disabled="!currentVisitId" :loading="arriving" @click="handleArrive">登记到诊</el-button>
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
const patientKeyword = ref('')
const patients = ref<Patient[]>([])
const createdVisit = ref<Visit | null>(null)
const currentVisitId = computed(() => createdVisit.value?.id)
const form = reactive({
  patientId: undefined as number | undefined,
  chiefComplaint: ''
})

const selectedPatient = computed(() => patients.value.find((item) => item.id === form.patientId) || null)

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
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者列表失败')
  } finally {
    patientLoading.value = false
  }
}

async function handleCreateVisit() {
  if (!form.patientId || !form.chiefComplaint) {
    ElMessage.warning('请选择患者并填写主诉')
    return
  }

  submitting.value = true
  try {
    createdVisit.value = await createVisit({
      patientId: form.patientId,
      chiefComplaint: form.chiefComplaint
    })
    ElMessage.success('就诊创建成功')
    await router.push(`/workstation/visits/${createdVisit.value.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建就诊失败')
  } finally {
    submitting.value = false
  }
}

async function handleArrive() {
  if (!createdVisit.value) {
    return
  }

  arriving.value = true
  try {
    createdVisit.value = await arriveVisit(createdVisit.value.id)
    ElMessage.success('已登记到诊')
    await router.push(`/workstation/visits/${createdVisit.value.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '到诊登记失败')
  } finally {
    arriving.value = false
  }
}

onMounted(() => {
  const preferredPatientId = Number(route.query.patientId || 0)
  if (preferredPatientId) {
    patientKeyword.value = String(route.query.keyword || '')
  }
  void searchPatients()
})
</script>
