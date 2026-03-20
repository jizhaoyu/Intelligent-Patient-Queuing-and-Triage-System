import { portalTokenKey } from '@/config/portal'

const TOKEN_KEY = portalTokenKey

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function hasToken() {
  return Boolean(getToken())
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}
