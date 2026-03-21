export interface Visit {
  id: number | string
  patientId: number | string
  // 患者业务编号（例如 P 开头），便于界面展示
  patientNo?: string
  visitNo: string
  status: string
  registerTime?: string
  arrivalTime?: string
  chiefComplaint?: string
  currentDeptId?: number | string
  currentRoomId?: number | string
}
