<template>
  <div>
    <PageHeader title="分诊规则" description="查看并维护分诊规则配置" />
    <el-card>
      <el-table v-loading="loading" :data="rules">
        <el-table-column prop="ruleCode" label="规则编码" />
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="symptomKeyword" label="症状关键词" />
        <el-table-column prop="triageLevel" label="分诊等级" />
        <el-table-column prop="specialWeight" label="特殊权重" />
        <el-table-column prop="enabled" label="启用">
          <template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getRules } from '@/api/triage'
import type { TriageRule } from '@/types/triage'

const loading = ref(false)
const rules = ref<TriageRule[]>([])

async function loadRules() {
  loading.value = true
  try {
    rules.value = await getRules()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取分诊规则失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadRules)
</script>
