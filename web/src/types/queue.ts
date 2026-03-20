export interface QueueTicket {
  ticketNo: string
  visitId: number
  patientId: number
  assessmentId: number
  deptId: number
  roomId?: number
  triageLevel: number
  priorityScore: number
  status: string
  recallCount: number
  fastTrack?: number
  waitingCount?: number
  rank?: number
  estimatedWaitMinutes?: number
  enqueueTime?: string
  callTime?: string
  completeTime?: string
}

export interface DeptQueueSummary {
  deptId: number
  waitingCount: number
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
  roomId?: number
  operatorName?: string
  remark?: string
  createdTime?: string
}

export interface DeptDashboardSummary {
  deptId: number
  waitingCount: number
  callingCount: number
  completedCount: number
  averageWaitMinutes: number
  timeoutHighPriorityCount: number
}

export interface RoomCurrent {
  roomId: number
  ticketNo?: string
  status?: string
  patientId?: number
  triageLevel?: number
  priorityScore?: number
}
