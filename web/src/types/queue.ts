export interface QueueTicketCreateDTO {
  visitId: number
  assessmentId: number
  roomId?: number
}

export interface QueueTicket {
  ticketNo: string
  visitId: number
  patientId: number | string
  // 患者业务编号（如 P123...），用于前端展示
  patientNo?: string
  patientName?: string
  assessmentId: number
  deptId: number
  deptName?: string
  roomId?: number
  roomName?: string
  doctorName?: string
  chiefComplaint?: string
  triageLevel: number
  priorityScore: number
  priorityReason?: string
  queueStrategyMode?: string
  surgePriorityApplied?: boolean
  agingBoostApplied?: boolean
  aiPriorityAdvice?: string
  aiAdvice?: string
  aiSuggestedLevel?: number
  aiRiskLevel?: string
  aiNeedManualReview?: boolean
  status: string
  displayStatus?: string
  displayStatusText?: string
  waitingForConsultation?: boolean
  recallCount: number
  fastTrack?: number
  sourceType?: string
  sourceRemark?: string
  lastAdjustReason?: string
  waitingCount?: number
  rank?: number
  estimatedWaitMinutes?: number
  // 已等待时长（分钟），由后端根据数据库时间实时计算
  waitedMinutes?: number
  enqueueTime?: string
  callTime?: string
  completeTime?: string
}

export interface DeptQueueSummary {
  deptId: number
  waitingCount: number
  callingTickets?: QueueTicket[]
  waitingTickets: QueueTicket[]
}

export interface QueueRank {
  ticketNo: string
  status: string
  rank: number
  waitingCount: number
  estimatedWaitMinutes: number
}

export interface QueueEventLog {
  id: number
  ticketNo: string
  eventType: string
  fromStatus?: string
  toStatus?: string
  visitId?: number
  patientId?: number | string
  deptId?: number
  roomId?: number
  operatorName?: string
  sourceType?: string
  sourceRemark?: string
  remark?: string
  createdTime?: string
}

export interface QueueExceptionItem {
  visitId: number
  visitNo?: string
  patientId?: number | string
  patientNo?: string
  patientName?: string
  chiefComplaint?: string
  triageLevel?: number
  assessmentId?: number
  assessedTime?: string
  deptId?: number
  deptName?: string
  recommendDeptId?: number
  recommendDeptName?: string
  reason?: string
}

export interface DeptDashboardSummary {
  deptId: number
  waitingCount: number
  callingCount: number
  completedCount: number
  averageWaitMinutes: number
  timeoutHighPriorityCount: number
  unqueuedTriagedCount?: number
}

export interface RoomCurrent {
  roomId: number
  ticketNo?: string
  status?: string
  patientId?: number | string
  patientName?: string
  triageLevel?: number
  priorityScore?: number
}
