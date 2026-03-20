<template>
  <div>
    <PageHeader title="事件日志" description="查看排队流转与操作记录" />
    <el-card>
      <div class="toolbar mb-16">
        <el-input
          v-model="filters.ticketNo"
          placeholder="票号搜索"
          clearable
          style="width: 220px"
          @keyup.enter="loadEvents"
        />
        <el-select v-model="filters.eventType" placeholder="事件类型" clearable style="width: 180px">
          <el-option v-for="item in eventTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadEvents">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="events" row-key="id">
        <el-table-column prop="ticketNo" label="票号" />
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
        <el-table-column prop="roomId" label="诊室" width="100" />
        <el-table-column prop="operatorName" label="操作人" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="时间" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getQueueEvents } from '@/api/queue'
import type { QueueEventLog } from '@/types/queue'

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

function formatStatusFlow(event: QueueEventLog) {
  if (event.fromStatus && event.toStatus) {
    return `${event.fromStatus} -> ${event.toStatus}`
  }
  if (event.toStatus) {
    return `START -> ${event.toStatus}`
  }
  return '-'
}

onMounted(() => {
  void loadEvents()
})
</script>
