import http from './http'
import type { Patient } from '@/types/patient'

export function getPatientList(keyword?: string) {
  return http.get<any, Patient[]>('/patients', { params: { keyword } })
}

export function getPatientById(id: number | string) {
  return http.get<any, Patient>(`/patients/${id}`)
}

export function createPatient(data: Partial<Patient>) {
  return http.post<any, Patient>('/patients', data)
}

export function updatePatient(id: number | string, data: Partial<Patient>) {
  return http.put<any, Patient>(`/patients/${id}`, data)
}
