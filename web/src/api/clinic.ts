import http from './http'
import type { ClinicDeptOption } from '@/types/clinic'

export function getDeptOptions() {
  return http.get<any, ClinicDeptOption[]>('/clinic/depts/options', {
    requestCache: {
      dedupe: true,
      ttl: 60_000
    }
  } as any)
}

export function getDeptIdByRoom(roomId: number | string) {
  return http.get<any, number | null>(`/clinic/depts/rooms/${roomId}`)
}
