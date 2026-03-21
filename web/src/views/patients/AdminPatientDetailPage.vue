<template>
  <div class="patient-admin-detail-page">
    <PageHeader title="患者详情" eyebrow="档案治理" description="查看患者基础档案、风险信息与当前就诊快照。">
      <template #actions>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientById } from '@/api/patient'
import type { Patient } from '@/types/patient'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const patient = ref<Patient | null>(null)

function goBack() {
  void router.push('/admin/patients')
}

function goAdminQueues() {
  void router.push('/admin/queues')
}

function goQueueExceptions() {
  void router.push('/admin/queues/exceptions')
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
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.98);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.link-item span {
  color: var(--muted-color);
}

.risk-text {
  color: #b45309;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
