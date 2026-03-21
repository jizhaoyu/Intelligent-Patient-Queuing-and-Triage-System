import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw
} from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import LoginLayout from '@/layout/LoginLayout.vue'
import ScreenLayout from '@/layout/ScreenLayout.vue'
import WorkstationLayout from '@/layout/WorkstationLayout.vue'
import { getDeptIdByRoom } from '@/api/clinic'
import { defaultScreenRoute, isRouteAllowedInPortal, loginEntryRoute, portalSurface, resolvePortalHomeRoute } from '@/config/portal'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { hasToken } from '@/utils/token'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    hidden?: boolean
    permission?: string
    menuPath?: string
    surface?: 'login' | 'admin' | 'workstation' | 'screen' | 'patient'
    roleCodes?: string[]
  }
}

function firstAccessibleChild(basePath: string, children: RouteRecordRaw[]) {
  return () => {
    const authStore = useAuthStore(pinia)
    const matched = children.find((child) =>
      authStore.hasPermission(child.meta?.permission as string | undefined)
    )
    return matched ? `${basePath}/${matched.path}` : '/login'
  }
}

function resolveAccessibleRoute(to: RouteLocationNormalized) {
  const authStore = useAuthStore(pinia)
  const routes = router.getRoutes()
  const surface = to.meta.surface

  const candidates = routes
    .filter(
      (route) =>
        route.meta.surface === surface &&
        !route.meta.hidden &&
        route.path !== to.path
    )
    .filter((route) =>
      authStore.hasPermission(route.meta.permission as string | undefined)
    )
    .sort((a, b) => a.path.localeCompare(b.path))

  return candidates[0]?.path || authStore.resolveDefaultRoute()
}

const adminChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: () => import('@/views/dashboard/DashboardPage.vue'),
    meta: {
      title: '管理看板',
      permission: 'dashboard:view',
      surface: 'admin',
      menuPath: '/admin/dashboard'
    }
  }
]

const workstationChildren: RouteRecordRaw[] = [
  {
    path: 'queue-call',
    name: 'workstation-queue-call',
    component: () => import('@/views/queues/QueueCallPage.vue'),
    meta: {
      title: '诊室叫号',
      permission: 'queue:call',
      surface: 'workstation',
      menuPath: '/workstation/queue-call'
    }
  },
  {
    path: 'triage/assessments/new',
    name: 'workstation-triage-assessment-create',
    component: () => import('@/views/triage/TriageAssessmentCreatePage.vue'),
    meta: {
      title: '分诊评估',
      permission: 'triage:assess',
      surface: 'workstation',
      menuPath: '/workstation/triage/assessments/new'
    }
  },
  {
    path: 'triage/assessments/:id',
    name: 'workstation-triage-assessment-detail',
    component: () => import('@/views/triage/TriageAssessmentDetailPage.vue'),
    meta: {
      title: '评估详情',
      permission: 'triage:assess',
      surface: 'workstation',
      hidden: true,
      menuPath: '/workstation/triage/assessments/new'
    }
  }
]

const defaultRootRoute =
  import.meta.env.VITE_APP_SURFACE === 'all'
    ? '/patient/self-queue'
    : import.meta.env.VITE_APP_SURFACE === 'screen'
      ? defaultScreenRoute
      : loginEntryRoute

const routes: RouteRecordRaw[] = [
  portalSurface === 'screen'
    ? {
        path: '/login',
        redirect: defaultScreenRoute
      }
    : {
        path: '/login',
        component: LoginLayout,
        children: [
          {
            path: '',
            name: 'login',
            component: () => import('@/views/auth/LoginPage.vue'),
            meta: { public: true, title: '登录', surface: 'login' }
          }
        ]
      },
  {
    path: '/',
    redirect: defaultRootRoute
  },
  {
    path: '/patient/queue',
    redirect: '/patient/self-queue'
  },
  {
    path: '/patient/self-queue',
    name: 'patient-self-queue',
    component: () => import('@/views/patient/PatientSelfQueuePage.vue'),
    meta: {
      public: true,
      title: '自助取号',
      surface: 'patient'
    }
  },
  {
    path: '/admin',
    component: AdminLayout,
    redirect: firstAccessibleChild('/admin', adminChildren),
    children: adminChildren
  },
  {
    path: '/workstation',
    component: WorkstationLayout,
    redirect: () => useAuthStore(pinia).resolveDefaultRoute(),
    children: workstationChildren
  },
  {
    path: '/screen',
    component: ScreenLayout,
    children: [
      {
        path: 'dept/:deptId',
        name: 'screen-dept',
        component: () => import('@/views/screens/DeptScreenPage.vue'),
        meta: {
          title: '科室大屏',
          permission: 'dashboard:view',
          surface: 'screen',
          public: true
        }
      },
      {
        path: 'room/:roomId',
        component: () => import('@/views/screens/RoomScreenPage.vue'),
        meta: {
          title: '诊室大屏跳转',
          surface: 'screen',
          public: true
        },
        beforeEnter: async (to) => {
          const roomId = Number(to.params.roomId)
          const fallbackRoute = import.meta.env.VITE_DEFAULT_SCREEN_ROUTE || '/screen/dept/1'
          if (!roomId || Number.isNaN(roomId)) {
            return fallbackRoute
          }
          try {
            const deptId = await getDeptIdByRoom(roomId)
            return deptId ? `/screen/dept/${deptId}` : fallbackRoute
          } catch {
            return fallbackRoute
          }
        }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  document.title =
    (to.meta.title as string) || '患者智能排队分诊系统'

  if (to.meta.public) {
    if (to.path === '/login') {
      if (portalSurface === 'screen') {
        return defaultScreenRoute
      }

      if (hasToken()) {
        try {
          await authStore.initialize()
          if (authStore.profile) {
            const portalHomeRoute = resolvePortalHomeRoute(authStore.profile)
            if (portalHomeRoute !== '/login') {
              return portalHomeRoute
            }
            authStore.reset()
            return true
          }
        } catch {
          return true
        }
      }
    }
    return true
  }

  if (!hasToken()) {
    return loginEntryRoute
  }

  if (!authStore.initialized) {
    try {
      await authStore.initialize()
    } catch {
      return loginEntryRoute
    }
  }

  if (!authStore.profile) {
    return loginEntryRoute
  }

  const portalHomeRoute = resolvePortalHomeRoute(authStore.profile)
  if (portalHomeRoute === '/login') {
    authStore.reset()
    return loginEntryRoute
  }

  if (
    !isRouteAllowedInPortal(
      to.meta.surface as
        | 'login'
        | 'admin'
        | 'workstation'
        | 'screen'
        | 'patient'
        | undefined
    )
  ) {
    return portalHomeRoute
  }

  if (
    to.meta.surface &&
    to.meta.surface !== 'login' &&
    to.meta.surface !== 'screen' &&
    to.meta.surface !== 'patient'
  ) {
    const allowedSurface = authStore.allowedSurface
    if (allowedSurface && to.meta.surface !== allowedSurface) {
      return portalHomeRoute
    }
  }

  const routeRoleCodes = to.meta.roleCodes as string[] | undefined
  if (routeRoleCodes?.length && !routeRoleCodes.includes(authStore.profile.roleCode)) {
    return portalHomeRoute
  }

  if (!authStore.hasPermission(to.meta.permission as string | undefined)) {
    return resolveAccessibleRoute(to)
  }

  return true
})

export default router
