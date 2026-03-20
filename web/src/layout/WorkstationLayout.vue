<template>
  <AppShell
    variant="workstation"
    brand="患者智能排队分诊系统"
    subtitle="临床协同工作台"
    surface-label="导诊、分诊、叫号一体化"
    description="聚焦高频操作、清晰提示和低误操作流程，适配导诊台、分诊护士与诊室医生。"
    :navigation="navigation"
    :context-items="contextItems"
    :highlights="['快速录入', '接诊闭环', '重点提醒']"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppShell from './AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const navigation = [
  { label: '患者查询', index: '/workstation/patients', permission: 'patient:manage' },
  { label: '就诊建档', index: '/workstation/visits/new', permission: 'visit:manage' },
  { label: '分诊评估', index: '/workstation/triage/assessments/new', permission: 'triage:assess' },
  { label: '诊室叫号', index: '/workstation/queue-call', permission: 'queue:call' }
]

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
