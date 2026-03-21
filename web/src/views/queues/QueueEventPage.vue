<template>
  <div>
    <PageHeader
      title="事件日志"
      description="按票号或事件类型查看排队流转、来源与操作记录"
    />

    <el-card>
      <div class="toolbar mb-16">
        <el-input
          v-model="filters.ticketNo"
          placeholder="输入票号搜索"
          clearable
          style="width: 220px"
          @keyup.enter="loadEvents"
        />
        <el-select
          v-model="filters.eventType"
          placeholder="选择事件类型"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="item in eventTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadEvents">
          查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="events" row-key="id">
        <el-table-column prop="ticketNo" label="票号" min-width="180" />
        <el-table-column prop="visitId" label="就诊 ID" width="110" />
        <el-table-column prop="patientId" label="患者 ID" width="110" />
        <el-table-column prop="deptId" label="科室 ID" width="110" />
        <el-table-column label="事件类型" width="140">
          <template #default="{ row }">
            {{ formatEventType(row.eventType) }}
          </template>
        </el-table-column>
        <el-table-column label="状态流转" min-width="220">
          <template #default="{ row }">
            {{ formatStatusFlow(row) }}
          </template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">
            {{ formatSourceType(row.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceRemark" label="来源说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="roomId" label="诊室 ID" width="110" />
        <el-table-column prop="operatorName" label="操作人" width="140" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && events.length === 0"
        description="当前筛选条件下暂无事件记录"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getQueueEvents } from '@/api/queue'
import type { QueueEventLog } from '@/types/queue'
import { formatQueueStatusCode } from '@/utils/queueStatus'

const loading = ref(false)
const events = ref<QueueEventLog[]>([])
const filters = reactive({
  ticketNo: '',
  eventType: ''
})

const eventTypeOptions = [
  { label: '入队', value: 'ENQUEUE' },
  { label: '叫号', value: 'CALL_NEXT' },
  { label: '复呼', value: 'RECALL' },
  { label: '过号', value: 'MISSED' },
  { label: '完成', value: 'COMPLETE' },
  { label: '取消', value: 'CANCEL' },
  { label: '人工调整', value: 'MANUAL_ADJUST' }
]

async function loadEvents() {
  loading.value = true
  try {
    events.value = await getQueueEvents({
      ticketNo: filters.ticketNo || undefined,
      eventType: filters.eventType || undefined
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取事件日志失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  filters.ticketNo = ''
  filters.eventType = ''
  void loadEvents()
}

function formatEventType(eventType?: string) {
  const map: Record<string, string> = {
    ENQUEUE: '入队',
    CALL_NEXT: '叫号',
    RECALL: '复呼',
    MISSED: '过号',
    COMPLETE: '完成',
    CANCEL: '取消',
    MANUAL_ADJUST: '人工调整'
  }
  return eventType ? map[eventType] || eventType : '-'
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

function formatStatusFlow(event: QueueEventLog) {
  if (event.fromStatus && event.toStatus) {
    return `${formatResolvedQueueStatus(event.fromStatus)} -> ${formatResolvedQueueStatus(event.toStatus)}`
  }
  if (event.toStatus) {
    return `开始 -> ${formatResolvedQueueStatus(event.toStatus)}`
  }
  return '-'
}

function formatResolvedQueueStatus(status?: string) {
  return formatQueueStatusCode(status)
}

function formatSourceType(sourceType?: string) {
  const map: Record<string, string> = {
    TRIAGE_AUTO: '分诊自动入队',
    KIOSK: '院内自助机',
    MANUAL_REPAIR: '异常补录'
  }
  return sourceType ? map[sourceType] || sourceType : '-'
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ') : '-'
}

onMounted(() => {
  void loadEvents()
})
</script>
