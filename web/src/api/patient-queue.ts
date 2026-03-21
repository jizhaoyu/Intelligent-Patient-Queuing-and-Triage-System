import http from './http'
import type { PatientQueueQueryDTO, PatientQueueView, PatientSelfQueueEnrollDTO } from '@/types/patient-queue'

export function queryPatientQueue(data: PatientQueueQueryDTO) {
  return http.post<any, PatientQueueView>('/patient-queue/query', data)
}

export function enrollPatientQueue(data: PatientSelfQueueEnrollDTO) {
  return http.post<any, PatientQueueView>('/patient-queue/enroll', data)
}
