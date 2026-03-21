<template>
  <div class="patient-admin-page">
    <PageHeader title="患者管理" eyebrow="档案治理" description="维护患者基础档案，查看当前就诊状态摘要，并为治理与审计提供入口。">
      <template #actions>
        <el-button type="primary" @click="createDialogVisible = true">新增患者</el-button>
      </template>
    </PageHeader>

    <el-card class="mb-16">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="姓名 / 手机号 / 证件号"
          style="width: 320px"
          clearable
          @keyup.enter="loadPatients"
        />
        <el-button type="primary" :loading="loading" @click="loadPatients">查询</el-button>
      </div>
    </el-card>

    <section class="summary-grid">
      <article class="summary-card">
        <span>患者总数</span>
        <strong>{{ patients.length }}</strong>
        <small>当前检索结果</small>
      </article>
      <article class="summary-card">
        <span>有在诊记录</span>
        <strong>{{ activeVisitCount }}</strong>
        <small>存在当前就诊单</small>
      </article>
      <article class="summary-card">
        <span>排队中</span>
        <strong>{{ queueingCount }}</strong>
        <small>可继续关注队列治理</small>
      </article>
      <article class="summary-card summary-card--accent">
        <span>就诊中</span>
        <strong>{{ inTreatmentCount }}</strong>
        <small>用于运行态监控</small>
      </article>
    </section>

    <el-card>
      <el-table v-loading="loading" :data="patients">
        <el-table-column prop="patientNo" label="编号" min-width="150" />
        <el-table-column prop="patientName" label="姓名" min-width="120" />
        <el-table-column prop="gender" label="性别" width="90" />
        <el-table-column label="当前状态" min-width="190">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag :type="visitStatusType(row.currentStatus)" effect="plain">
                {{ formatVisitStatus(row.currentStatus) }}
              </el-tag>
              <div v-if="row.currentVisitNo" class="muted-meta">就诊单 {{ row.currentVisitNo }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="specialTags" label="特殊标签" min-width="180" />
        <el-table-column label="当前科室/诊室" min-width="170">
          <template #default="{ row }">
            <div class="status-cell">
              <span>{{ row.currentDeptId ? `科室 ${row.currentDeptId}` : '未关联科室' }}</span>
              <small class="muted-meta">{{ row.currentRoomId ? `诊室 ${row.currentRoomId}` : '未关联诊室' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新增患者" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="createForm.patientName" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.gender" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-input v-model="createForm.birthDate" placeholder="yyyy-MM-dd" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.idCard" />
        </el-form-item>
        <el-form-item label="过敏史">
          <el-input v-model="createForm.allergyHistory" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="特殊标签">
          <el-input v-model="createForm.specialTags" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createPatient, getPatientList } from '@/api/patient'
import type { Patient } from '@/types/patient'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const patients = ref<Patient[]>([])
const createDialogVisible = ref(false)
const createForm = reactive<Partial<Patient>>({
  patientName: '',
  gender: '',
  birthDate: '',
  phone: '',
  idCard: '',
  allergyHistory: '',
  specialTags: ''
})

const activeVisitCount = computed(() => patients.value.filter((item) => !!item.currentVisitId).length)
const queueingCount = computed(() => patients.value.filter((item) => item.currentStatus === 'QUEUING').length)
const inTreatmentCount = computed(() => patients.value.filter((item) => item.currentStatus === 'IN_TREATMENT').length)

function viewDetail(id: number) {
  void router.push(`/admin/patients/${id}`)
}

async function loadPatients() {
  loading.value = true
  try {
    patients.value = await getPatientList(keyword.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者列表失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.patientName) {
    ElMessage.warning('请输入患者姓名')
    return
  }

  submitting.value = true
  try {
    const patient = await createPatient(createForm)
    ElMessage.success('患者创建成功')
    createDialogVisible.value = false
    Object.assign(createForm, {
      patientName: '',
      gender: '',
      birthDate: '',
      phone: '',
      idCard: '',
      allergyHistory: '',
      specialTags: ''
    })
    await router.push(`/admin/patients/${patient.id}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建患者失败')
  } finally {
    submitting.value = false
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

onMounted(() => {
  void loadPatients()
})
</script>

<style scoped>
.patient-admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar,
.status-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.status-cell {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 16px 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 1));
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
.muted-meta {
  color: var(--muted-color);
}

.summary-card--accent {
  background: linear-gradient(180deg, rgba(8, 145, 178, 0.1), rgba(255, 255, 255, 1));
  border-color: rgba(8, 145, 178, 0.2);
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
