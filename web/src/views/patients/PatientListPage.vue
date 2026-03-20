<template>
  <div>
    <PageHeader title="患者管理" description="维护患者基础资料">
      <template #actions>
        <el-button type="primary" @click="createDialogVisible = true">新增患者</el-button>
      </template>
    </PageHeader>

    <el-card class="mb-16">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="姓名/手机号/证件号" style="width: 280px" @keyup.enter="loadPatients" />
        <el-button type="primary" :loading="loading" @click="loadPatients">查询</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table v-loading="loading" :data="patients">
        <el-table-column prop="patientNo" label="编号" min-width="140" />
        <el-table-column prop="patientName" label="姓名" min-width="120" />
        <el-table-column prop="gender" label="性别" width="90" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="specialTags" label="特殊标签" min-width="160" />
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <el-button link type="primary" @click="viewDetail(scope.row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新增患者" width="520px">
      <el-form label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="createForm.patientName" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.gender" placeholder="请选择性别">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-input v-model="createForm.birthDate" placeholder="yyyy-MM-dd" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="createForm.idCard" />
        </el-form-item>
        <el-form-item label="过敏史">
          <el-input v-model="createForm.allergyHistory" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="特殊标签">
          <el-input v-model="createForm.specialTags" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { createPatient, getPatientList } from '@/api/patient'
import type { Patient } from '@/types/patient'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const keyword = ref((route.query.keyword as string) || '')
const patients = ref<Patient[]>([])
const createDialogVisible = ref(false)
const createForm = reactive<Partial<Patient>>({
  patientName: '',
  gender: '',
  birthDate: '',
  phone: '',
  idCard: '',
  allergyHistory: '',
  specialTags: ''
})

function resolveDetailPath(id: number) {
  return route.path.startsWith('/admin') ? `/admin/patients/${id}` : `/workstation/patients/${id}`
}

function viewDetail(id: number) {
  router.push(resolveDetailPath(id))
}

async function loadPatients() {
  loading.value = true
  try {
    patients.value = await getPatientList(keyword.value || undefined)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取患者列表失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.patientName) {
    ElMessage.warning('请输入患者姓名')
    return
  }

  submitting.value = true
  try {
    const patient = await createPatient(createForm)
    ElMessage.success('患者创建成功')
    createDialogVisible.value = false
    Object.assign(createForm, {
      patientName: '',
      gender: '',
      birthDate: '',
      phone: '',
      idCard: '',
      allergyHistory: '',
      specialTags: ''
    })
    await router.push(resolveDetailPath(patient.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建患者失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadPatients)
</script>
