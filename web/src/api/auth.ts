import http from './http'
import type { LoginResult, UserProfile } from '@/types/auth'

export function login(data: { username: string; password: string }) {
  return http.post<any, LoginResult>('/auth/login', data)
}

export function getCurrentUser() {
  return http.get<any, UserProfile>('/auth/me')
}

export function logout() {
  return http.post<any, void>('/auth/logout')
}
