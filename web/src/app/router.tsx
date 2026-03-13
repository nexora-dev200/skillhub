import { lazy, Suspense, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'

function createLazyRouteComponent(load: () => Promise<{ default: ComponentType<any> }>) {
  const LazyComponent = lazy(load)

  return function LazyRouteComponent(props: Record<string, unknown>) {
    return (
      <Suspense
        fallback={
          <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
            Loading...
          </div>
        }
      >
        <LazyComponent {...props} />
      </Suspense>
    )
  }
}

const HomePage = createLazyRouteComponent(() =>
  import('@/pages/home').then((module) => ({ default: module.HomePage })),
)
const LoginPage = createLazyRouteComponent(() =>
  import('@/pages/login').then((module) => ({ default: module.LoginPage })),
)
const DashboardPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard').then((module) => ({ default: module.DashboardPage })),
)
const SearchPage = createLazyRouteComponent(() =>
  import('@/pages/search').then((module) => ({ default: module.SearchPage })),
)
const NamespacePage = createLazyRouteComponent(() =>
  import('@/pages/namespace').then((module) => ({ default: module.NamespacePage })),
)
const SkillDetailPage = createLazyRouteComponent(() =>
  import('@/pages/skill-detail').then((module) => ({ default: module.SkillDetailPage })),
)
const PublishPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/publish').then((module) => ({ default: module.PublishPage })),
)
const MySkillsPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/my-skills').then((module) => ({ default: module.MySkillsPage })),
)
const MyNamespacesPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/my-namespaces').then((module) => ({ default: module.MyNamespacesPage })),
)
const NamespaceMembersPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/namespace-members').then((module) => ({ default: module.NamespaceMembersPage })),
)
const ReviewsPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/reviews').then((module) => ({ default: module.ReviewsPage })),
)
const ReviewDetailPage = createLazyRouteComponent(() =>
  import('@/pages/dashboard/review-detail').then((module) => ({ default: module.ReviewDetailPage })),
)
const DeviceAuthPage = createLazyRouteComponent(() =>
  import('@/pages/device').then((module) => ({ default: module.DeviceAuthPage })),
)
const AdminUsersPage = createLazyRouteComponent(() =>
  import('@/pages/admin/users').then((module) => ({ default: module.AdminUsersPage })),
)
const AuditLogPage = createLazyRouteComponent(() =>
  import('@/pages/admin/audit-log').then((module) => ({ default: module.AuditLogPage })),
)

const rootRoute = createRootRoute({
  component: Layout,
})

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/search',
  component: SearchPage,
  validateSearch: (search: Record<string, unknown>) => {
    return {
      q: (search.q as string) || '',
      sort: (search.sort as string) || 'relevance',
      page: Number(search.page) || 1,
    }
  },
})

const namespaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/@$namespace',
  component: NamespacePage,
})

const skillDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/@$namespace/$slug',
  component: SkillDetailPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: DashboardPage,
})

const dashboardSkillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/skills',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: MySkillsPage,
})

const dashboardPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/publish',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: PublishPage,
})

const dashboardNamespacesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/namespaces',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: MyNamespacesPage,
})

const dashboardNamespaceMembersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/namespaces/$slug/members',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: NamespaceMembersPage,
})

const dashboardReviewsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/reviews',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: ReviewsPage,
})

const dashboardReviewDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/reviews/$id',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: ReviewDetailPage,
})

const deviceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/device',
  component: DeviceAuthPage,
})

const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/users',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    if (!user.platformRoles?.includes('USER_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AdminUsersPage,
})

const adminAuditLogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/audit-log',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    if (!user.platformRoles?.includes('AUDITOR') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AuditLogPage,
})

const routeTree = rootRoute.addChildren([
  homeRoute,
  loginRoute,
  searchRoute,
  namespaceRoute,
  skillDetailRoute,
  dashboardRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardNamespacesRoute,
  dashboardNamespaceMembersRoute,
  dashboardReviewsRoute,
  dashboardReviewDetailRoute,
  deviceRoute,
  adminUsersRoute,
  adminAuditLogRoute,
])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
