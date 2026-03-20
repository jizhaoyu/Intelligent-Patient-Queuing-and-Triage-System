import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { resolveRoleAllowedSurface, resolveRoleDefaultRoute } from '@/config/portal'
import { clearToken, getToken, setToken } from '@/utils/token'
import type { LoginResult, UserProfile } from '@/types/auth'

interface AuthState {
  profile: UserProfile | null
  initialized: boolean
  bootstrapping: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    profile: null,
    initialized: false,
    bootstrapping: false
  }),
  getters: {
    permissions: (state) => state.profile?.permissions ?? [],
    hasPermission: (state) => (permission?: string) => {
      if (!permission) {
        return true
      }
      return (state.profile?.permissions ?? []).includes(permission)
    },
    defaultRoutePath(): string {
      return resolveRoleDefaultRoute(this.profile?.roleCode)
    },
    allowedSurface(): 'admin' | 'workstation' | null {
      return resolveRoleAllowedSurface(this.profile?.roleCode)
    }
  },
  actions: {
    async initialize() {
      if (this.initialized || !getToken()) {
        this.initialized = true
        return this.profile
      }

      this.bootstrapping = true
      try {
        this.profile = await getCurrentUser()
        return this.profile
      } catch (error) {
        this.reset()
        throw error
      } finally {
        this.bootstrapping = false
        this.initialized = true
      }
    },
    async login(payload: { username: string; password: string }) {
      const result: LoginResult = await loginApi(payload)
      setToken(result.token)
      this.profile = result.profile
      this.initialized = true
      return result
    },
    async fetchProfile() {
      if (!getToken()) {
        this.reset()
        return null
      }
      this.profile = await getCurrentUser()
      this.initialized = true
      return this.profile
    },
    async logout() {
      try {
        if (getToken()) {
          await logoutApi()
        }
      } finally {
        this.reset()
      }
    },
    reset() {
      clearToken()
      this.profile = null
      this.initialized = true
      this.bootstrapping = false
    },
    resolveDefaultRoute() {
      return this.defaultRoutePath
    }
  }
})
