<template>
  <AppShell
    variant="admin"
    brand="患者智能排队分诊系统"
    subtitle="医院运营中台"
    surface-label="质控、排队与规则治理"
    description="围绕门诊排队、分诊规则和服务质量开展实时监控与决策支持。"
    :navigation="navigation"
    :context-items="contextItems"
    :highlights="['门诊质控', '服务秩序', '规则配置']"
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
  { label: '候诊队列', index: '/admin/queues', permission: 'queue:manage' },
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
