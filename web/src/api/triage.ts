import http from './http'
import type { TriageAssessment, TriageRule } from '@/types/triage'

export function createAssessment(data: Partial<TriageAssessment>) {
  return http.post<any, TriageAssessment>('/triage/assessments', data)
}

export function getAssessmentById(id: number | string) {
  return http.get<any, TriageAssessment>(`/triage/assessments/${id}`)
}

export function reassess(id: number | string, data: Partial<TriageAssessment>) {
  return http.post<any, TriageAssessment>(`/triage/assessments/${id}/reassess`, data)
}

export function getRules() {
  return http.get<any, TriageRule[]>('/triage/rules')
}

export function updateRule(id: number | string, data: Partial<TriageRule>) {
  return http.put<any, TriageRule>(`/triage/rules/${id}`, data)
}
