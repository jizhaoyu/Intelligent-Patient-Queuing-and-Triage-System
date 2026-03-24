<template>
  <AppShell
    variant="admin"
    brand="患者智能排队分诊系统"
    subtitle="医院运营中台"
    surface-label="运营总览"
    description="统一承接患者档案、就诊建档、队列运行与规则维护。"
    :navigation="navigation"
    :context-items="contextItems"
    :highlights="['全院态势', '档案治理', '队列运行', '规则维护']"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppShell from './AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const navigation = [
  { label: '运营总览', index: '/admin/dashboard', permission: 'dashboard:view' },
  { label: '患者管理', index: '/admin/patients', permission: 'patient:manage' },
  { label: '就诊建档', index: '/admin/visits/new', permission: 'visit:manage' },
  { label: '候诊队列', index: '/admin/queues', permission: 'queue:manage' },
  { label: '异常治理', index: '/admin/queues/exceptions', permission: 'queue:manage' },
  { label: '事件日志', index: '/admin/queues/events', permission: 'queue:manage' },
  { label: '分诊规则', index: '/admin/triage/rules', permission: 'triage:rule' }
]

const contextItems = computed(() => {
  const items: string[] = []
  if (authStore.profile?.deptId) {
    items.push(`所属科室 ${authStore.profile.deptId}`)
  }
  if (authStore.profile?.roomId) {
    items.push(`关联诊室 ${authStore.profile.roomId}`)
  }
  return items
})
</script>
