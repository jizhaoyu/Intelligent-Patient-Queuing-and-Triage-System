<template>
  <div>
    <PageHeader
      title="异常治理"
      description="查看“已分诊未入队”记录，并执行管理员异常补录修复"
    >
      <template #actions>
        <el-select
          v-model="selectedDeptId"
          class="dept-select"
          placeholder="请选择查看范围"
          :loading="deptOptionsLoading"
          style="width: 220px"
        >
          <el-option
            v-for="item in deptSelectOptions"
            :key="item.id"
            :label="item.deptName"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadExceptions">
          刷新异常
        </el-button>
        <el-tag effect="plain">当前范围：{{ scopeLabel }}</el-tag>
      </template>
    </PageHeader>

    <el-card>
      <el-table v-loading="loading" :data="exceptions" row-key="visitId">
        <el-table-column prop="patientNo" label="患者编号" min-width="140" />
        <el-table-column prop="patientName" label="患者姓名" min-width="120" />
        <el-table-column prop="visitNo" label="就诊号" min-width="150" />
        <el-table-column prop="chiefComplaint" label="主诉" min-width="180" show-overflow-tooltip />
        <el-table-column label="建议科室" min-width="140">
          <template #default="{ row }">
            {{ row.recommendDeptName || row.deptName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="分诊等级" width="110">
          <template #default="{ row }">
            {{ row.triageLevel ? `${row.triageLevel} 级` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="分诊时间" min-width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.assessedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="异常说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :loading="repairingVisitId === row.visitId"
              @click="handleRepair(row)"
            >
              执行异常补录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && exceptions.length === 0"
        description="当前没有待处理的已分诊未入队异常"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createTicket, getUnqueuedTriagedExceptions } from '@/api/queue'
import { useDeptScope } from '@/composables/useDeptScope'
import { useAuthStore } from '@/stores/auth'
import type { QueueExceptionItem } from '@/types/queue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const repairingVisitId = ref<number | null>(null)
const exceptions = ref<QueueExceptionItem[]>([])
const deptScopeInitialized = ref(false)
const {
  deptSelectOptions,
  loading: deptOptionsLoading,
  selectedDeptId,
  scopeLabel,
  loadDeptOptions
} = useDeptScope({
  allowAll: true,
  initialDeptId: authStore.profile?.deptId ?? 0
})

async function loadExceptions() {
  loading.value = true
  try {
    const deptId =
      selectedDeptId.value && selectedDeptId.value > 0
        ? selectedDeptId.value
        : undefined
    exceptions.value = await getUnqueuedTriagedExceptions(deptId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取异常列表失败')
  } finally {
    loading.value = false
  }
}

async function handleRepair(row: QueueExceptionItem) {
  if (!row.visitId || !row.assessmentId) {
    ElMessage.warning('当前异常记录缺少就诊或评估信息，无法执行补录')
    return
  }

  repairingVisitId.value = row.visitId
  try {
    const ticket = await createTicket({
      visitId: row.visitId,
      assessmentId: row.assessmentId
    })
    ElMessage.success(`异常补录成功，票号 ${ticket.ticketNo}`)
    await loadExceptions()
    await router.push(`/admin/queues/tickets/${ticket.ticketNo}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行异常补录失败')
  } finally {
    repairingVisitId.value = null
  }
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

onMounted(async () => {
  try {
    await loadDeptOptions()
    deptScopeInitialized.value = true
    await loadExceptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室列表失败')
  }
})

watch(selectedDeptId, () => {
  if (!deptScopeInitialized.value) {
    return
  }
  void loadExceptions()
})
</script>
