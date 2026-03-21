import { computed, ref } from 'vue'
import { getDeptOptions } from '@/api/clinic'
import type { ClinicDeptOption } from '@/types/clinic'

type UseDeptScopeOptions = {
  allowAll?: boolean
  initialDeptId?: number | null
}

type DeptSelectOption = ClinicDeptOption & {
  id: number
}

const ALL_DEPT_OPTION: DeptSelectOption = {
  id: 0,
  deptCode: 'ALL',
  deptName: '全院'
}

function normalizeDeptId(value?: number | null, allowAll = false) {
  if (value == null || Number.isNaN(value)) {
    return allowAll ? 0 : undefined
  }

  return value > 0 ? value : allowAll ? 0 : undefined
}

export function useDeptScope(options: UseDeptScopeOptions = {}) {
  const allowAll = options.allowAll ?? false
  const deptOptions = ref<ClinicDeptOption[]>([])
  const loading = ref(false)
  const selectedDeptId = ref<number | undefined>(normalizeDeptId(options.initialDeptId, allowAll))

  const deptSelectOptions = computed<DeptSelectOption[]>(() => {
    return allowAll ? [ALL_DEPT_OPTION, ...deptOptions.value] : deptOptions.value
  })

  const selectedDeptOption = computed(() => deptOptions.value.find((item) => item.id === selectedDeptId.value) || null)
  const scopeLabel = computed(() => {
    if (selectedDeptId.value === 0) {
      return '全院'
    }
    if (selectedDeptId.value) {
      return selectedDeptOption.value?.deptName || `科室 ${selectedDeptId.value}`
    }
    return allowAll ? '全院' : '未选择科室'
  })

  function syncSelectedDeptId() {
    const currentDeptId = normalizeDeptId(selectedDeptId.value, allowAll)
    if (currentDeptId === 0) {
      selectedDeptId.value = 0
      return
    }
    if (currentDeptId && deptOptions.value.some((item) => item.id === currentDeptId)) {
      selectedDeptId.value = currentDeptId
      return
    }

    const initialDeptId = normalizeDeptId(options.initialDeptId, allowAll)
    if (initialDeptId === 0) {
      selectedDeptId.value = 0
      return
    }
    if (initialDeptId && deptOptions.value.some((item) => item.id === initialDeptId)) {
      selectedDeptId.value = initialDeptId
      return
    }

    selectedDeptId.value = allowAll ? 0 : deptOptions.value[0]?.id
  }

  async function loadDeptOptions() {
    loading.value = true
    try {
      deptOptions.value = await getDeptOptions()
      syncSelectedDeptId()
    } finally {
      loading.value = false
    }
  }

  function setSelectedDeptId(deptId?: number | null) {
    selectedDeptId.value = normalizeDeptId(deptId, allowAll)
  }

  return {
    deptOptions,
    deptSelectOptions,
    loading,
    selectedDeptId,
    selectedDeptOption,
    scopeLabel,
    loadDeptOptions,
    setSelectedDeptId
  }
}
