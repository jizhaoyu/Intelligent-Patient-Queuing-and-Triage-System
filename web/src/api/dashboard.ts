import http from './http'
import type { DeptDashboardSummary, RoomCurrent } from '@/types/queue'

export function getDashboardSummary(deptId?: number | string) {
  return http.get<any, DeptDashboardSummary>('/dashboard/summary', {
    params: {
      deptId
    }
  })
}

export function getDeptSummary(deptId: number | string) {
  return http.get<any, DeptDashboardSummary>(`/dashboard/depts/${deptId}/summary`)
}

export function getRoomCurrent(roomId: number | string) {
  return http.get<any, RoomCurrent>(`/dashboard/rooms/${roomId}/current`)
}
