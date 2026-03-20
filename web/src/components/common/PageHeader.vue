<template>
  <div class="page-header">
    <div class="page-header__main">
      <div v-if="eyebrow" class="page-header__eyebrow">{{ eyebrow }}</div>
      <h1 class="page-header__title">{{ title }}</h1>
      <div v-if="description" class="page-header__description">{{ description }}</div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>
