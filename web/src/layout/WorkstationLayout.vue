<template>
  <AppShell
    variant="workstation"
    brand="患者智能排队分诊系统"
    subtitle="临床协同工作台"
    surface-label="诊室叫号"
    description="仅保留医生诊室叫号主链路，聚焦待诊患者处理与接诊节奏。"
    :navigation="navigation"
    :context-items="contextItems"
    :highlights="['待诊患者', '叫号进度', '接诊焦点']"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppShell from './AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const navigation = computed(() => {
  const items = [
    { label: '诊室叫号', index: '/workstation/queue-call', permission: 'queue:call' }
  ]
  if (authStore.hasPermission('triage:assess')) {
    items.push({
      label: '分诊评估',
      index: '/workstation/triage/assessments/new',
      permission: 'triage:assess'
    })
  }
  return items
})

const contextItems = computed(() => {
  const items: string[] = []
  if (authStore.profile?.deptId) {
    items.push(`所属科室 ${authStore.profile.deptId}`)
  }
  if (authStore.profile?.roomId) {
    items.push(`当前诊室 ${authStore.profile.roomId}`)
  }
  return items
})
</script>
