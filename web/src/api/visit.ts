import http from './http'
import type { Visit } from '@/types/visit'

export function createVisit(data: Partial<Visit>) {
  return http.post<any, Visit>('/visits', data)
}

export function getVisitById(id: number | string) {
  return http.get<any, Visit>(`/visits/${id}`)
}

export function arriveVisit(id: number | string) {
  return http.post<any, Visit>(`/visits/${id}/arrive`)
}
