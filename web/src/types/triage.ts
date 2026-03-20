export interface TriageAssessment {
  id: number
  visitId: number
  symptomTags?: string
  bodyTemperature?: number
  heartRate?: number
  bloodPressure?: string
  bloodOxygen?: number
  triageLevel: number
  recommendDeptId?: number
  priorityScore: number
  fastTrack?: number
  manualAdjustScore?: number
  assessor?: string
  assessedTime?: string
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
