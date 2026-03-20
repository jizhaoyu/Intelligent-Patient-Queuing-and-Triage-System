<template>
  <div>
    <PageHeader title="患者详情" description="查看患者基础信息与历史摘要" />
    <el-skeleton :loading="loading" animated>
      <template #default>
        <el-card v-if="patient">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="患者编号">{{ patient.patientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ patient.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ patient.gender || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ patient.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ patient.birthDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="证件号">{{ patient.idCard || '-' }}</el-descriptions-item>
            <el-descriptions-item label="过敏史">{{ patient.allergyHistory || '-' }}</el-descriptions-item>
            <el-descriptions-item label="特殊标签">{{ patient.specialTags || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">{{ patient.createdTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-empty v-else description="未找到患者信息" />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getPatientById } from '@/api/patient'
import type { Patient } from '@/types/patient'

const route = useRoute()
const loading = ref(false)
const patient = ref<Patient | null>(null)

async function loadPatient() {
  loading.value = true
  try {
    patient.value = await getPatientById(route.params.id as string)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadPatient)
</script>
