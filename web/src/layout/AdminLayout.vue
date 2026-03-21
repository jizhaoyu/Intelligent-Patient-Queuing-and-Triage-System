<template>
  <AppShell
    variant="admin"
    brand="患者智能排队分诊系统"
    subtitle="医院运营中台"
    surface-label="运营总览"
    description="聚焦全院候诊、叫号与接诊态势，提供核心运营视角。"
    :navigation="navigation"
    :context-items="contextItems"
    :highlights="['全院态势', '候诊负载', '接诊进度']"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppShell from './AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const navigation = [{ label: '运营总览', index: '/admin/dashboard', permission: 'dashboard:view' }]

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
