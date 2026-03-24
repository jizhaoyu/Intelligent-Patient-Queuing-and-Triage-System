export interface PatientQueueQueryDTO {
  patientNo?: string
  patientName?: string
  phoneSuffix: string
}

export type PatientNextStepUrgency = 'LOW' | 'NORMAL' | 'HIGH' | 'IMMEDIATE'

export interface PatientNextStep {
  stage: string
  title: string
  action: string
  locationHint?: string
  urgency: PatientNextStepUrgency
}

export type PatientSelfQueueMode = 'EXISTING' | 'NEW'

export interface PatientSelfQueueEnrollDTO {
  patientMode?: PatientSelfQueueMode
  patientNo?: string
  patientName?: string
  phoneSuffix?: string
  phone?: string
  gender?: string
  birthDate?: string
  idCard?: string
  allergyHistory?: string
  specialTags?: string
  deptId: number
  chiefComplaint?: string
}

export interface PatientQueueView {
  nextStep?: PatientNextStep
  patientName: string
  patientNo: string
  patientId: number
  visitId?: number
  visitNo?: string
  visitStatus?: string
  visitStatusText?: string
  queueStatus?: string
  queueStatusText?: string
  queueMessage?: string
  ticketNo?: string
  deptId?: number
  deptName?: string
  roomId?: number
  roomName?: string
  doctorName?: string
  rank?: number
  waitingCount?: number
  roomWaitingCount?: number
  estimatedWaitMinutes?: number
  roomEstimatedWaitMinutes?: number
  waitedMinutes?: number
  triageLevel?: number
  aiSuggestedLevel?: number
  enqueueTime?: string
  callTime?: string
  completeTime?: string
  hasActiveQueue: boolean
  aiSuggestedDeptId?: number
  aiSuggestedDeptName?: string
  aiRiskLevel?: string
  aiRiskTags?: string[]
  aiStructuredSymptoms?: string[]
  aiNeedManualReview?: boolean
  aiAdvice?: string
  aiConfidence?: number
  aiSource?: string
  aiModelVersion?: string
}
