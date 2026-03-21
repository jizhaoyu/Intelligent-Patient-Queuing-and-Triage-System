import type { UserProfile } from '@/types/auth'

export type PortalSurface = 'all' | 'admin' | 'workstation' | 'screen'
export type RouteSurface = 'login' | 'admin' | 'workstation' | 'screen' | 'patient'

const ROLE_HOME_MAP: Record<string, string> = {
  ADMIN: '/admin/dashboard',
  DOCTOR: '/workstation/queue-call'
}

const ROLE_SURFACE_MAP: Record<string, 'admin' | 'workstation'> = {
  ADMIN: 'admin',
  DOCTOR: 'workstation'
}

const PORTAL_LABEL_MAP: Record<PortalSurface, string> = {
  all: '综合入口',
  admin: '管理后台入口',
  workstation: '工作台入口',
  screen: '大屏入口'
}

const PORTAL_SURFACE_SET = new Set<PortalSurface>(['all', 'admin', 'workstation', 'screen'])

const rawPortalSurface = import.meta.env.VITE_APP_SURFACE
export const defaultScreenRoute = import.meta.env.VITE_DEFAULT_SCREEN_ROUTE || '/screen/dept/1'

export const portalSurface: PortalSurface = PORTAL_SURFACE_SET.has(rawPortalSurface as PortalSurface)
  ? (rawPortalSurface as PortalSurface)
  : 'all'

export const portalDisplayName = PORTAL_LABEL_MAP[portalSurface]
export const portalTokenKey = import.meta.env.VITE_TOKEN_KEY?.trim() || `triage_queue.token.${portalSurface}`
export const loginEntryRoute = portalSurface === 'screen' ? defaultScreenRoute : '/login'

export function resolveRoleDefaultRoute(roleCode?: string | null) {
  if (!roleCode) {
    return '/login'
  }

  return ROLE_HOME_MAP[roleCode] || '/login'
}

export function resolveRoleAllowedSurface(roleCode?: string | null) {
  if (!roleCode) {
    return null
  }

  return ROLE_SURFACE_MAP[roleCode] || null
}

export function isRouteAllowedInPortal(surface?: RouteSurface) {
  if (!surface || surface === 'login') {
    return true
  }

  return portalSurface === 'all' ? true : surface === portalSurface
}

export function resolvePortalHomeRoute(profile?: UserProfile | null) {
  if (!profile) {
    return '/login'
  }

  switch (portalSurface) {
    case 'admin':
      return resolveRoleAllowedSurface(profile.roleCode) === 'admin' ? '/admin/dashboard' : '/login'
    case 'workstation':
      return resolveRoleAllowedSurface(profile.roleCode) === 'workstation'
        ? resolveRoleDefaultRoute(profile.roleCode)
        : '/login'
    case 'screen':
      if (!profile.permissions.includes('dashboard:view')) {
        return '/login'
      }

      if (profile.deptId) {
        return `/screen/dept/${profile.deptId}`
      }

      return defaultScreenRoute
    default:
      return resolveRoleDefaultRoute(profile.roleCode)
  }
}

export function getPortalAccessDeniedMessage() {
  switch (portalSurface) {
    case 'admin':
      return '当前入口仅支持管理员账号，请改用 5173 端口登录。'
    case 'workstation':
      return '当前入口仅支持工作台账号，请改用 5174 端口登录。'
    case 'screen':
      return '当前入口仅支持大屏查看账号，请改用 5175 端口登录。'
    default:
      return '当前账号无权访问此入口。'
  }
}
