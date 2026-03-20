import http from './http'
import type { DeptQueueSummary, QueueEventLog, QueueRank, QueueTicket } from '@/types/queue'

export function createTicket(data: Partial<QueueTicket>) {
  return http.post<any, QueueTicket>('/queues/tickets', data)
}

export function getTicket(ticketNo: string) {
  return http.get<any, QueueTicket>(`/queues/tickets/${ticketNo}`)
}

export function getDeptWaiting(deptId: number | string) {
  return http.get<any, DeptQueueSummary>(`/queues/depts/${deptId}/waiting`)
}

export function callNext(roomId: number | string) {
  return http.post<any, QueueTicket>(`/queues/rooms/${roomId}/call-next`)
}

export function recall(ticketNo: string) {
  return http.post<any, QueueTicket>(`/queues/tickets/${ticketNo}/recall`)
}

export function markMissed(ticketNo: string) {
  return http.post<any, QueueTicket>(`/queues/tickets/${ticketNo}/missed`)
}

export function completeTicket(ticketNo: string) {
  return http.post<any, QueueTicket>(`/queues/tickets/${ticketNo}/complete`)
}

export function cancelTicket(ticketNo: string) {
  return http.post<any, QueueTicket>(`/queues/tickets/${ticketNo}/cancel`)
}

export function getRank(ticketNo: string) {
  return http.get<any, QueueRank>(`/queues/tickets/${ticketNo}/rank`)
}

export function getQueueEvents(params?: { ticketNo?: string; eventType?: string }) {
  return http.get<any, QueueEventLog[]>('/queues/events', { params })
}
