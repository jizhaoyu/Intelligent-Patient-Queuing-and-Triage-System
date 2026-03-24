<template>
  <div class="patient-admin-detail-page">
    <PageHeader title="患者详情" eyebrow="档案治理" description="查看患者基础档案、风险信息与当前就诊快照。">
      <template #actions>
        <el-button type="primary" :disabled="!patient" @click="openEditDialog">编辑患者</el-button>
        <el-button @click="goBack">返回患者管理</el-button>
      </template>
    </PageHeader>

    <el-skeleton :loading="loading" animated>
      <template #default>
        <template v-if="patient">
          <div class="detail-grid">
            <el-card>
              <template #header>患者基础档案</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="患者编号">{{ patient.patientNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ patient.patientName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ patient.gender || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系电话">{{ patient.phone || '-' }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{ patient.birthDate || '-' }}</el-descriptions-item>
                <el-descriptions-item label="证件号">{{ patient.idCard || '-' }}</el-descriptions-item>
                <el-descriptions-item label="创建时间" :span="2">{{ patient.createdTime || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card>
              <template #header>风险与标签</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="过敏史">
                  <span :class="{ 'risk-text': !!patient.allergyHistory }">{{ patient.allergyHistory || '未登记' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="特殊标签">{{ patient.specialTags || '无' }}</el-descriptions-item>
                <el-descriptions-item label="状态更新时间">{{ patient.statusUpdatedTime || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </div>

          <div class="detail-grid detail-grid--secondary">
            <el-card>
              <template #header>当前就诊快照</template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="当前就诊单">{{ patient.currentVisitNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前状态">{{ formatVisitStatus(patient.currentStatus) }}</el-descriptions-item>
                <el-descriptions-item label="当前科室">{{ patient.currentDeptId ? `科室 ${patient.currentDeptId}` : '-' }}</el-descriptions-item>
                <el-descriptions-item label="当前诊室">{{ patient.currentRoomId ? `诊室 ${patient.currentRoomId}` : '-' }}</el-descriptions-item>
                <el-descriptions-item label="就诊记录 ID" :span="2">{{ patient.currentVisitId || '-' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card>
              <template #header>治理相关入口</template>
              <div class="link-list">
                <div class="link-item">
                  <strong>候诊队列</strong>
                  <span>查看当前排队运行态与患者所处链路。</span>
                  <el-button link type="primary" @click="goAdminQueues">进入候诊队列</el-button>
                </div>
                <div class="link-item">
                  <strong>异常治理</strong>
                  <span>核查未入队、错分流或需要人工修复的异常情况。</span>
                  <el-button link type="primary" @click="goQueueExceptions">进入异常治理</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </template>

        <el-empty v-else description="未找到患者信息" />
      </template>
    </el-skeleton>

    <el-dialog v-model="editDialogVisible" title="编辑患者" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="editForm.patientName" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="editForm.gender" clearable placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-input v-model="editForm.birthDate" placeholder="yyyy-MM-dd" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="editForm.idCard" />
        </el-form-item>
        <el-form-item label="过敏史">
          <el-input v-model="editForm.allergyHistory" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="特殊标签">
          <el-input v-model="editForm.specialTags" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSavePatient">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientById, updatePatient } from '@/api/patient'
import type { Patient } from '@/types/patient'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const patient = ref<Patient | null>(null)
const editDialogVisible = ref(false)
const saving = ref(false)
const editForm = reactive<Partial<Patient>>({
  patientName: '',
  gender: '',
  birthDate: '',
  phone: '',
  idCard: '',
  allergyHistory: '',
  specialTags: ''
})

function goBack() {
  void router.push('/admin/patients')
}

function goAdminQueues() {
  void router.push('/admin/queues')
}

function goQueueExceptions() {
  void router.push('/admin/queues/exceptions')
}

function openEditDialog() {
  if (!patient.value) {
    return
  }
  Object.assign(editForm, {
    patientName: patient.value.patientName || '',
    gender: patient.value.gender || '',
    birthDate: patient.value.birthDate || '',
    phone: patient.value.phone || '',
    idCard: patient.value.idCard || '',
    allergyHistory: patient.value.allergyHistory || '',
    specialTags: patient.value.specialTags || ''
  })
  editDialogVisible.value = true
}

async function loadPatient() {
  loading.value = true
  try {
    patient.value = await getPatientById(String(route.params.id || ''))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者详情失败')
  } finally {
    loading.value = false
  }
}

async function handleSavePatient() {
  if (!patient.value?.id) {
    return
  }
  if (!editForm.patientName?.trim()) {
    ElMessage.warning('请输入患者姓名')
    return
  }

  saving.value = true
  try {
    patient.value = await updatePatient(patient.value.id, {
      patientName: editForm.patientName?.trim(),
      gender: editForm.gender || undefined,
      birthDate: editForm.birthDate || undefined,
      phone: editForm.phone || undefined,
      idCard: editForm.idCard || undefined,
      allergyHistory: editForm.allergyHistory || undefined,
      specialTags: editForm.specialTags || undefined
    })
    editDialogVisible.value = false
    ElMessage.success('患者信息已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新患者失败')
  } finally {
    saving.value = false
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

onMounted(() => {
  void loadPatient()
})
</script>

<style scoped>
.patient-admin-detail-page {
  display: grid;
  gap: 20px;
}

.patient-admin-detail-page :deep(.el-card) {
  border: none;
  border-radius: 26px;
  overflow: hidden;
  box-shadow: 0 22px 48px rgba(8, 47, 73, 0.08);
}

.patient-admin-detail-page :deep(.el-card__header) {
  padding: 20px 22px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(248, 252, 253, 0.98), rgba(255, 255, 255, 0.82));
}

.patient-admin-detail-page :deep(.el-card__body) {
  padding: 22px;
}

.patient-admin-detail-page :deep(.el-descriptions__body .el-descriptions__table) {
  overflow: hidden;
  border-radius: 20px;
}

.patient-admin-detail-page :deep(.el-descriptions__label.el-descriptions__cell) {
  min-width: 120px;
  background: rgba(240, 249, 255, 0.88);
  color: var(--muted-color);
  font-weight: 700;
}

.patient-admin-detail-page :deep(.el-descriptions__content.el-descriptions__cell) {
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-color);
}

.patient-admin-detail-page :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.patient-admin-detail-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(248, 252, 253, 0.98), rgba(255, 255, 255, 0.84));
}

.patient-admin-detail-page :deep(.el-dialog__body) {
  padding: 22px 24px 10px;
}

.patient-admin-detail-page :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}

.patient-admin-detail-page :deep(.el-input__wrapper),
.patient-admin-detail-page :deep(.el-select__wrapper),
.patient-admin-detail-page :deep(.el-textarea__inner) {
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.18);
}

.patient-admin-detail-page :deep(.el-input__wrapper),
.patient-admin-detail-page :deep(.el-select__wrapper) {
  min-height: 48px;
}

.patient-admin-detail-page :deep(.el-input__wrapper.is-focus),
.patient-admin-detail-page :deep(.el-select__wrapper.is-focused),
.patient-admin-detail-page :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 4px rgba(34, 211, 238, 0.12),
    inset 0 0 0 1px rgba(8, 145, 178, 0.34);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.detail-grid--secondary {
  align-items: start;
}

.link-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.link-item {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 18px 18px 18px 22px;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 1));
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.link-item::before {
  content: "";
  position: absolute;
  top: 16px;
  bottom: 16px;
  left: 0;
  width: 4px;
  border-radius: 999px;
  background: linear-gradient(180deg, #0891b2, #10b981);
}

.link-item strong {
  font-size: 16px;
  color: var(--title-color);
}

.link-item span {
  color: var(--muted-color);
  line-height: 1.7;
}

.risk-text {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .patient-admin-detail-page :deep(.el-card__body),
  .patient-admin-detail-page :deep(.el-card__header) {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>
