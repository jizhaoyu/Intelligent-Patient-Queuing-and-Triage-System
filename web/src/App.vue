<template>
  <div :class="appClasses">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { portalSurface } from '@/config/portal'

const route = useRoute()

const appClasses = computed(() => [
  'app-root',
  `app-root--portal-${portalSurface}`,
  `app-root--route-${(route.meta.surface as string) || 'default'}`
])
</script>
