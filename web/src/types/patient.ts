export interface Patient {
  id: number
  patientNo: string
  patientName: string
  gender?: string
  birthDate?: string
  phone?: string
  idCard?: string
  allergyHistory?: string
  specialTags?: string
  priorityRevisitPending?: boolean
  priorityRevisitGrantedTime?: string
  priorityRevisitGrantedBy?: string
  currentVisitId?: number
  currentVisitNo?: string
  currentStatus?: string
  currentDeptId?: number
  currentRoomId?: number
  statusUpdatedTime?: string
  createdTime?: string
}
