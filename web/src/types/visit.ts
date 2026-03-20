export interface Visit {
  id: number
  patientId: number
  visitNo: string
  status: string
  registerTime?: string
  arrivalTime?: string
  chiefComplaint?: string
  currentDeptId?: number
  currentRoomId?: number
}
