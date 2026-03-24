<template>
  <div class="triage-rule-page">
    <PageHeader title="分诊规则" description="按页查看并维护分诊规则，避免长列表影响定位和编辑效率。" />

    <el-card>
      <div class="rule-table-toolbar">
        <div class="rule-table-toolbar__meta">
          <strong>规则列表</strong>
          <span>{{ paginationSummary }}</span>
        </div>
        <span class="data-pill">每页 {{ pageSize }} 条</span>
      </div>

      <el-table v-loading="loading" :data="pagedRules" row-key="id">
        <el-table-column label="序号" width="84">
          <template #default="{ $index }">
            {{ ruleIndex($index) }}
          </template>
        </el-table-column>
        <el-table-column prop="ruleCode" label="规则编码" min-width="150" />
        <el-table-column prop="ruleName" label="规则名称" min-width="180" />
        <el-table-column prop="symptomKeyword" label="症状关键词" min-width="180" />
        <el-table-column prop="triageLevel" label="分诊等级" width="110" />
        <el-table-column label="推荐科室" min-width="150">
          <template #default="{ row }">
            {{ formatDeptName(row.recommendDeptId) }}
          </template>
        </el-table-column>
        <el-table-column prop="specialWeight" label="特殊权重" width="110" />
        <el-table-column prop="enabled" label="启用" width="100">
          <template #default="{ row }">
            {{ row.enabled ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="rule-pagination">
        <span class="rule-pagination__summary">{{ paginationSummary }}</span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          :total="rules.length"
        />
      </div>
    </el-card>

    <el-dialog v-model="editDialogVisible" title="编辑分诊规则" width="560px">
      <el-form label-position="top">
        <el-form-item label="规则编码">
          <el-input :model-value="editForm.ruleCode" disabled />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="editForm.ruleName" />
        </el-form-item>
        <el-form-item label="症状关键词">
          <el-input v-model="editForm.symptomKeyword" />
        </el-form-item>
        <el-form-item label="推荐科室">
          <el-select v-model="editForm.recommendDeptId" clearable placeholder="请选择科室" style="width: 100%">
            <el-option v-for="item in deptOptions" :key="item.id" :label="item.deptName" :value="item.id" />
          </el-select>
        </el-form-item>
        <div class="rule-form-grid">
          <el-form-item label="分诊等级">
            <el-input-number v-model="editForm.triageLevel" :min="1" :max="4" style="width: 100%" />
          </el-form-item>
          <el-form-item label="特殊权重">
            <el-input-number v-model="editForm.specialWeight" :min="0" :max="999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="快速通道">
            <el-switch v-model="editForm.fastTrack" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="editForm.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getDeptOptions } from '@/api/clinic'
import { getRules, updateRule } from '@/api/triage'
import type { ClinicDeptOption } from '@/types/clinic'
import type { TriageRule } from '@/types/triage'

const loading = ref(false)
const saving = ref(false)
const rules = ref<TriageRule[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const deptOptions = ref<ClinicDeptOption[]>([])
const editDialogVisible = ref(false)
const editForm = reactive<Partial<TriageRule>>({
  id: undefined,
  ruleCode: '',
  ruleName: '',
  symptomKeyword: '',
  triageLevel: 4,
  recommendDeptId: undefined,
  specialWeight: 0,
  fastTrack: 0,
  enabled: 1
})

const pagedRules = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return rules.value.slice(start, start + pageSize.value)
})

const paginationSummary = computed(() => {
  const total = rules.value.length
  if (!total) {
    return '暂无可用规则'
  }

  const start = (currentPage.value - 1) * pageSize.value + 1
  const end = Math.min(start + pageSize.value - 1, total)
  return `当前显示第 ${start} - ${end} 条，共 ${total} 条规则`
})

watch(
  [() => rules.value.length, pageSize],
  () => {
    const maxPage = Math.max(1, Math.ceil(rules.value.length / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  },
  { immediate: true }
)

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

async function loadDeptData() {
  try {
    deptOptions.value = await getDeptOptions()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '获取科室列表失败')
  }
}

function ruleIndex(index: number) {
  return (currentPage.value - 1) * pageSize.value + index + 1
}

function formatDeptName(deptId?: number) {
  if (!deptId) {
    return '-'
  }
  return deptOptions.value.find((item) => item.id === deptId)?.deptName || `科室 ${deptId}`
}

function openEditDialog(rule: TriageRule) {
  Object.assign(editForm, {
    id: rule.id,
    ruleCode: rule.ruleCode,
    ruleName: rule.ruleName,
    symptomKeyword: rule.symptomKeyword || '',
    triageLevel: rule.triageLevel ?? 4,
    recommendDeptId: rule.recommendDeptId,
    specialWeight: rule.specialWeight ?? 0,
    fastTrack: rule.fastTrack ?? 0,
    enabled: rule.enabled ?? 1
  })
  editDialogVisible.value = true
}

async function handleSaveRule() {
  if (!editForm.id) {
    return
  }
  if (!editForm.ruleName?.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }

  saving.value = true
  try {
    const updated = await updateRule(editForm.id, {
      ruleName: editForm.ruleName.trim(),
      symptomKeyword: editForm.symptomKeyword?.trim() || undefined,
      triageLevel: editForm.triageLevel,
      recommendDeptId: editForm.recommendDeptId,
      specialWeight: editForm.specialWeight,
      fastTrack: editForm.fastTrack,
      enabled: editForm.enabled
    })
    rules.value = rules.value.map((rule) => (rule.id === updated.id ? updated : rule))
    editDialogVisible.value = false
    ElMessage.success('分诊规则已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新分诊规则失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadDeptData(), loadRules()])
})
</script>

<style scoped>
.triage-rule-page {
  display: grid;
  gap: 20px;
}

.triage-rule-page :deep(.el-card) {
  border: none;
  border-radius: 26px;
  overflow: hidden;
  box-shadow: 0 22px 48px rgba(6, 95, 70, 0.08);
}

.triage-rule-page :deep(.el-card__body) {
  padding: 22px;
}

.rule-table-toolbar,
.rule-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.rule-table-toolbar {
  margin-bottom: 16px;
}

.rule-table-toolbar__meta {
  display: grid;
  gap: 4px;
}

.rule-table-toolbar__meta strong {
  font-size: 18px;
  line-height: 1.3;
  color: var(--title-color);
}

.rule-table-toolbar__meta span,
.rule-pagination__summary {
  font-size: 14px;
  line-height: 1.7;
  color: var(--muted-color);
}

.rule-pagination {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.triage-rule-page :deep(.el-table) {
  border-radius: 22px;
  overflow: hidden;
  background: transparent;
}

.triage-rule-page :deep(.el-table th.el-table__cell) {
  background: rgba(236, 253, 245, 0.9);
  color: var(--muted-color);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.triage-rule-page :deep(.el-table td.el-table__cell) {
  border-bottom-color: rgba(148, 163, 184, 0.12);
}

.triage-rule-page :deep(.el-table__row:hover > td.el-table__cell) {
  background: rgba(16, 185, 129, 0.05);
}

.triage-rule-page :deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
}

.triage-rule-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(247, 253, 250, 0.98), rgba(255, 255, 255, 0.84));
}

.triage-rule-page :deep(.el-dialog__body) {
  padding: 22px 24px 10px;
}

.triage-rule-page :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}

.triage-rule-page :deep(.el-input__wrapper),
.triage-rule-page :deep(.el-select__wrapper) {
  min-height: 48px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.92);
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.18);
}

.triage-rule-page :deep(.el-input__wrapper.is-focus),
.triage-rule-page :deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 4px rgba(16, 185, 129, 0.12),
    inset 0 0 0 1px rgba(5, 150, 105, 0.32);
}

.rule-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 900px) {
  .rule-form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .triage-rule-page :deep(.el-card__body) {
    padding-left: 18px;
    padding-right: 18px;
  }

  .rule-table-toolbar,
  .rule-pagination {
    align-items: stretch;
  }

  .rule-pagination :deep(.el-pagination) {
    justify-content: flex-start;
  }
}
</style>
