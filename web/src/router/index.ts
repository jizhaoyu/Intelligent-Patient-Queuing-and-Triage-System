import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layout/AdminLayout.vue'
import LoginLayout from '@/layout/LoginLayout.vue'
import ScreenLayout from '@/layout/ScreenLayout.vue'
import WorkstationLayout from '@/layout/WorkstationLayout.vue'
import { isRouteAllowedInPortal, resolvePortalHomeRoute } from '@/config/portal'
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
    surface?: 'login' | 'admin' | 'workstation' | 'screen'
  }
}

function firstAccessibleChild(basePath: string, children: RouteRecordRaw[]) {
  return () => {
    const authStore = useAuthStore(pinia)
    const matched = children.find((child) => authStore.hasPermission(child.meta?.permission as string | undefined))
    return matched ? `${basePath}/${matched.path}` : '/login'
  }
}

function resolveAccessibleRoute(to: RouteLocationNormalized) {
  const authStore = useAuthStore(pinia)
  const routes = router.getRoutes()
  const surface = to.meta.surface

  const candidates = routes
    .filter((route) => route.meta.surface === surface && !route.meta.hidden && route.path !== to.path)
    .filter((route) => authStore.hasPermission(route.meta.permission as string | undefined))
    .sort((a, b) => a.path.localeCompare(b.path))

  return candidates[0]?.path || authStore.resolveDefaultRoute()
}

const adminChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: () => import('@/views/dashboard/DashboardPage.vue'),
    meta: { title: '管理看板', permission: 'dashboard:view', surface: 'admin', menuPath: '/admin/dashboard' }
  },
  {
    path: 'patients',
    name: 'admin-patients',
    component: () => import('@/views/patients/PatientListPage.vue'),
    meta: { title: '患者管理', permission: 'patient:manage', surface: 'admin', menuPath: '/admin/patients' }
  },
  {
    path: 'patients/:id',
    name: 'admin-patient-detail',
    component: () => import('@/views/patients/PatientDetailPage.vue'),
    meta: { title: '患者详情', permission: 'patient:manage', hidden: true, surface: 'admin', menuPath: '/admin/patients' }
  },
  {
    path: 'queues',
    name: 'admin-queues',
    component: () => import('@/views/queues/QueueListPage.vue'),
    meta: { title: '候诊队列', permission: 'queue:manage', surface: 'admin', menuPath: '/admin/queues' }
  },
  {
    path: 'queues/events',
    name: 'admin-queue-events',
    component: () => import('@/views/queues/QueueEventPage.vue'),
    meta: { title: '事件日志', permission: 'queue:manage', surface: 'admin', menuPath: '/admin/queues/events' }
  },
  {
    path: 'queues/tickets/:ticketNo',
    name: 'admin-queue-ticket-detail',
    component: () => import('@/views/queues/QueueTicketDetailPage.vue'),
    meta: { title: '排队详情', permission: 'queue:manage', hidden: true, surface: 'admin', menuPath: '/admin/queues' }
  },
  {
    path: 'triage/rules',
    name: 'admin-triage-rules',
    component: () => import('@/views/triage/TriageRulePage.vue'),
    meta: { title: '分诊规则', permission: 'triage:rule', surface: 'admin', menuPath: '/admin/triage/rules' }
  }
]

const workstationChildren: RouteRecordRaw[] = [
  {
    path: 'patients',
    name: 'workstation-patients',
    component: () => import('@/views/patients/PatientListPage.vue'),
    meta: { title: '患者查询', permission: 'patient:manage', surface: 'workstation', menuPath: '/workstation/patients' }
  },
  {
    path: 'patients/:id',
    name: 'workstation-patient-detail',
    component: () => import('@/views/patients/PatientDetailPage.vue'),
    meta: { title: '患者详情', permission: 'patient:manage', hidden: true, surface: 'workstation', menuPath: '/workstation/patients' }
  },
  {
    path: 'visits/new',
    name: 'workstation-visit-create',
    component: () => import('@/views/visits/VisitCreatePage.vue'),
    meta: { title: '就诊建档', permission: 'visit:manage', surface: 'workstation', menuPath: '/workstation/visits/new' }
  },
  {
    path: 'visits/:id',
    name: 'workstation-visit-detail',
    component: () => import('@/views/visits/VisitDetailPage.vue'),
    meta: { title: '就诊详情', permission: 'visit:manage', hidden: true, surface: 'workstation', menuPath: '/workstation/visits/new' }
  },
  {
    path: 'triage/assessments/new',
    name: 'workstation-triage-assessment-create',
    component: () => import('@/views/triage/TriageAssessmentCreatePage.vue'),
    meta: { title: '分诊评估', permission: 'triage:assess', surface: 'workstation', menuPath: '/workstation/triage/assessments/new' }
  },
  {
    path: 'triage/assessments/:id',
    name: 'workstation-triage-assessment-detail',
    component: () => import('@/views/triage/TriageAssessmentDetailPage.vue'),
    meta: { title: '评估详情', permission: 'triage:assess', hidden: true, surface: 'workstation', menuPath: '/workstation/triage/assessments/new' }
  },
  {
    path: 'queue-call',
    name: 'workstation-queue-call',
    component: () => import('@/views/queues/QueueCallPage.vue'),
    meta: { title: '诊室叫号', permission: 'queue:call', surface: 'workstation', menuPath: '/workstation/queue-call' }
  }
]

const routes: RouteRecordRaw[] = [
  {
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
    redirect: '/login'
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
    redirect: firstAccessibleChild('/workstation', workstationChildren),
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
        meta: { title: '科室大屏', permission: 'dashboard:view', surface: 'screen', public: true }
      },
      {
        path: 'room/:roomId',
        name: 'screen-room',
        component: () => import('@/views/screens/RoomScreenPage.vue'),
        meta: { title: '诊室屏', permission: 'dashboard:view', surface: 'screen', public: true }
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
  document.title = (to.meta.title as string) || '患者智能排队分诊系统'

  if (to.meta.public) {
    if (to.path === '/login' && hasToken()) {
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
    return true
  }

  if (!hasToken()) {
    return '/login'
  }

  if (!authStore.initialized) {
    try {
      await authStore.initialize()
    } catch {
      return '/login'
    }
  }

  if (!authStore.profile) {
    return '/login'
  }

  const portalHomeRoute = resolvePortalHomeRoute(authStore.profile)
  if (portalHomeRoute === '/login') {
    authStore.reset()
    return '/login'
  }

  if (!isRouteAllowedInPortal(to.meta.surface as 'login' | 'admin' | 'workstation' | 'screen' | undefined)) {
    return portalHomeRoute
  }

  if (to.meta.surface && to.meta.surface !== 'login' && to.meta.surface !== 'screen') {
    const allowedSurface = authStore.allowedSurface
    if (allowedSurface && to.meta.surface !== allowedSurface) {
      return portalHomeRoute
    }
  }

  if (!authStore.hasPermission(to.meta.permission as string | undefined)) {
    return resolveAccessibleRoute(to)
  }

  return true
})

export default router
