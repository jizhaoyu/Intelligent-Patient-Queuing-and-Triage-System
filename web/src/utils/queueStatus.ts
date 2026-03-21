import type { QueueTicket } from '@/types/queue'

type QueueLike = Pick<QueueTicket, 'status' | 'displayStatus' | 'displayStatusText'> | null | undefined

function normalizeDisplayStatus(status?: string) {
  if (status === 'WAITING') {
    return 'QUEUEING'
  }
  return status
}

export function getQueueDisplayStatus(ticket?: QueueLike) {
  if (!ticket) {
    return undefined
  }
  return normalizeDisplayStatus(ticket.displayStatus || ticket.status)
}

export function formatQueueStatus(ticket?: QueueLike, fallback = '-') {
  if (!ticket) {
    return fallback
  }
  if (ticket.displayStatusText) {
    return ticket.displayStatusText
  }
  return formatQueueStatusCode(getQueueDisplayStatus(ticket), fallback)
}

export function formatQueueStatusCode(status?: string, fallback = '-') {
  if (!status) {
    return fallback
  }
  const map: Record<string, string> = {
    WAITING_FOR_CONSULTATION: '候诊中',
    QUEUEING: '排队中',
    WAITING: '排队中',
    CALLING: '叫号中',
    COMPLETED: '已完成',
    MISSED: '已过号',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

export function getQueueStatusTagType(target?: QueueLike | string): 'success' | 'warning' | 'info' | 'danger' {
  const displayStatus = typeof target === 'string' ? normalizeDisplayStatus(target) : getQueueDisplayStatus(target)
  switch (displayStatus) {
    case 'CALLING':
      return 'success'
    case 'WAITING_FOR_CONSULTATION':
      return 'warning'
    case 'MISSED':
      return 'danger'
    default:
      return 'info'
  }
}
