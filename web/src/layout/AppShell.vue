<template>
  <div class="app-shell" :class="`app-shell--${variant}`">
    <aside class="app-sidebar">
      <div class="app-sidebar__panel">
        <div v-if="subtitle" class="app-sidebar__eyebrow">{{ subtitle }}</div>
        <div class="app-logo">{{ brand }}</div>
        <div v-if="surfaceLabel" class="app-sidebar__headline">{{ surfaceLabel }}</div>
        <div v-if="description" class="app-subtitle">{{ description }}</div>
        <div v-if="highlights?.length" class="app-sidebar__meta">
          <span v-for="item in highlights" :key="item">{{ item }}</span>
        </div>
      </div>
      <div class="app-sidebar__section">功能导航</div>
      <el-menu :default-active="activeMenu" router class="app-menu">
        <el-menu-item v-for="item in visibleNavigation" :key="item.index" :index="item.index">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar__footer">
        <div class="app-sidebar__footer-label">当前值班</div>
        <div class="app-sidebar__footer-value">{{ authStore.profile?.nickname || authStore.profile?.username || '未登录' }}</div>
        <div class="app-sidebar__footer-meta">{{ footerMeta }}</div>
      </div>
    </aside>

    <section class="app-main">
      <header class="app-header">
        <div class="app-header__intro">
          <div class="app-header__eyebrow">{{ shellLabel }}</div>
          <div class="app-header__title">{{ pageTitle }}</div>
          <div v-if="surfaceLabel" class="app-header__subtitle">{{ surfaceLabel }}</div>
        </div>
        <div class="app-header__actions">
          <div v-if="contextTagList.length" class="app-header__tags">
            <el-tag v-for="item in contextTagList" :key="item" effect="plain">{{ item }}</el-tag>
          </div>
          <div class="app-user-panel">
            <div class="app-user-panel__meta">
              <div class="app-user-panel__label">当前值班</div>
              <div class="app-user">{{ authStore.profile?.nickname || authStore.profile?.username || '未登录' }}</div>
              <div class="app-user-panel__role">{{ footerMeta }}</div>
            </div>
          </div>
          <el-button plain @click="handleLogout">退出登录</el-button>
        </div>
      </header>
      <main class="app-content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

interface NavigationItem {
  label: string
  index: string
  permission?: string
}

const props = defineProps<{
  variant: 'admin' | 'workstation'
  brand: string
  subtitle?: string
  surfaceLabel?: string
  description?: string
  navigation: NavigationItem[]
  contextItems?: string[]
  highlights?: string[]
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const visibleNavigation = computed(() => props.navigation.filter((item) => authStore.hasPermission(item.permission)))

const contextTagList = computed(() => {
  const items = [...(props.contextItems || [])]
  if (authStore.profile?.roleCode) {
    items.push(`岗位 ${authStore.profile.roleCode}`)
  }
  return items
})

const activeMenu = computed(() => (route.meta.menuPath as string) || route.path)
const pageTitle = computed(() => (route.meta.title as string) || '页面')
const shellLabel = computed(() => (props.variant === 'admin' ? '管理后台' : '临床工作台'))
const sidebarFootnote = computed(() => props.surfaceLabel || props.subtitle || '医疗协同流程')
const footerMeta = computed(() => (authStore.profile?.roleCode ? `岗位 ${authStore.profile.roleCode}` : sidebarFootnote.value))

async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
}
</script>
