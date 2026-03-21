export interface TriageAssessment {
  id: number
  visitId: number
  symptomTags?: string
  bodyTemperature?: number
  heartRate?: number
  bloodPressure?: string
  bloodOxygen?: number
  age?: number
  gender?: string
  elderly?: boolean
  pregnant?: boolean
  child?: boolean
  disabled?: boolean
  revisit?: boolean
  triageLevel: number
  recommendDeptId?: number
  recommendDeptName?: string
  priorityScore: number
  fastTrack?: number
  manualAdjustScore?: number
  assessor?: string
  assessedTime?: string
  queueCreated?: boolean
  queueTicketNo?: string
  queueStatus?: string
  queueDeptId?: number
  queueRoomId?: number
  queueDeptName?: string
  queueRoomName?: string
  aiSuggestedLevel?: number
  aiSuggestedDeptId?: number
  aiSuggestedDeptName?: string
  aiPriorityScore?: number
  aiRiskLevel?: string
  aiRiskTags?: string[]
  aiAdvice?: string
  aiConfidence?: number
  aiRuleDiff?: string
  aiNeedManualReview?: boolean
  aiSource?: string
  aiModelVersion?: string
}

export interface TriageRule {
  id: number
  ruleCode: string
  ruleName: string
  symptomKeyword?: string
  triageLevel?: number
  recommendDeptId?: number
  specialWeight?: number
  fastTrack?: number
  enabled?: number
}
