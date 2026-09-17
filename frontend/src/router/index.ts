import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { startRouteProgress, stopRouteProgress } from '@/utils/progress'

/**
 * 前端路由分区（与《概要设计说明书》3.3 节一致）：
 *   /           用户端（游客、普通用户、陪玩师共用），含"成为陪玩师"入驻申请页
 *   /companion  陪玩师端（已审核陪玩师）
 *   /support    在线客服（普通用户、陪玩师）
 *   /cs         客服工作台（客服人员）
 *   /admin      管理后台（管理员）
 *
 * 入驻申请（FR-P01）属于"普通用户 → 陪玩师"的前置流程，因此放在用户端 `/become-companion`，
 * 不设角色要求；若仍挂在 `/companion` 分区下，普通用户会被角色守卫拦回首页，形成
 * "先成为陪玩师才能申请、要申请才能成为陪玩师"的死锁。
 *
 * 路由守卫仅改善体验；真实权限判断由后端 Spring Security 与数据范围校验执行。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 角色不足时的兜底跳转路径（未设置时回到首页） */
    roleFallback?: string
    /** 是否为工作台首页（首页不显示"返回上一页"按钮） */
    workbenchHome?: boolean
  }
}
const routes: RouteRecordRaw[] = [
  // ===== 用户端（统一顶部导航布局） =====
  {
    path: '/',
    component: () => import('@/views/user/UserLayout.vue'),
    children: [
      {
        path: '',
        name: 'user-home',
        component: () => import('@/views/user/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'games',
        name: 'user-games',
        component: () => import('@/views/user/GamesView.vue'),
        meta: { title: '游戏' },
      },
      {
        path: 'companions',
        name: 'user-companions',
        component: () => import('@/views/user/CompanionsView.vue'),
        meta: { title: '陪玩师' },
      },
      {
        path: 'orders',
        name: 'user-orders',
        component: () => import('@/views/user/OrdersView.vue'),
        meta: { title: '我的订单', requiresAuth: true },
      },
      {
        path: 'profile',
        name: 'user-profile',
        component: () => import('@/views/user/ProfileView.vue'),
        meta: { title: '个人中心', requiresAuth: true },
      },
      {
        // 入驻申请入口（FR-P01~P04）：登录即可访问，不要求陪玩师角色
        path: 'become-companion',
        name: 'become-companion',
        component: () => import('@/views/user/BecomeCompanionView.vue'),
        meta: { title: '成为陪玩师', requiresAuth: true },
      },
      {
        path: 'support',
        name: 'support',
        component: () => import('@/views/support/SupportView.vue'),
        meta: { title: '在线客服', requiresAuth: true },
      },
      {
        path: 'companions/:userId',
        name: 'companion-detail',
        component: () => import('@/views/user/CompanionDetailView.vue'),
        meta: { title: '陪玩师详情' },
      },
      {
        path: 'booking',
        name: 'booking',
        component: () => import('@/views/user/BookingView.vue'),
        meta: { title: '创建预约', requiresAuth: true },
      },
      {
        path: 'payment/:id',
        name: 'payment',
        component: () => import('@/views/user/PaymentView.vue'),
        meta: { title: '订单支付', requiresAuth: true },
      },
      {
        path: 'orders/:id',
        name: 'order-detail',
        component: () => import('@/views/user/OrderDetailView.vue'),
        meta: { title: '订单详情', requiresAuth: true },
      },
      {
        path: 'review/:orderId',
        name: 'review',
        component: () => import('@/views/user/ReviewView.vue'),
        meta: { title: '评价订单', requiresAuth: true },
      },
      {
        path: 'complaint/:orderId',
        name: 'complaint',
        component: () => import('@/views/user/ComplaintView.vue'),
        meta: { title: '发起投诉', requiresAuth: true },
      },
    ],
  },

  // ===== 陪玩师端 =====
  {
    path: '/companion',
    component: () => import('@/views/companion/CompanionLayout.vue'),
    // 非陪玩师访问时兜底到入驻申请页，而不是丢弃到首页（角色不足的提示与下一步更明确）
    meta: { requiresAuth: true, roles: ['COMPANION'], roleFallback: '/become-companion' },
    children: [
      // 默认落地"我的陪玩主页"：进入工作台先看到自己的陪玩项目门面
      { path: '', redirect: '/companion/profile' },
      // 入驻申请已迁移到用户端 `/become-companion`，此处保留重定向以兼容旧链接
      { path: 'application', redirect: '/become-companion' },
      { path: 'profile', name: 'companion-profile', component: () => import('@/views/companion/CompanionProfileView.vue'), meta: { title: '我的陪玩主页', workbenchHome: true } },
      { path: 'services', name: 'companion-services', component: () => import('@/views/companion/ServiceManageView.vue'), meta: { title: '服务管理' } },
      { path: 'schedule', name: 'companion-schedule', component: () => import('@/views/companion/ScheduleView.vue'), meta: { title: '档期管理' } },
      { path: 'orders', name: 'companion-orders', component: () => import('@/views/companion/OrderFulfillView.vue'), meta: { title: '接单履约' } },
      { path: 'earnings', name: 'companion-earnings', component: () => import('@/views/companion/EarningsView.vue'), meta: { title: '收益' } },
    ],
  },

  // ===== 客服工作台 =====
  {
    path: '/cs',
    component: () => import('@/views/cs/CsLayout.vue'),
    meta: { requiresAuth: true, roles: ['CUSTOMER_SERVICE'] },
    children: [
      { path: '', redirect: '/cs/queue' },
      { path: 'queue', name: 'cs-queue', component: () => import('@/views/cs/QueueView.vue'), meta: { title: '会话队列', workbenchHome: true } },
      { path: 'conversation/:id', name: 'cs-conversation', component: () => import('@/views/cs/ConversationView.vue'), meta: { title: '会话详情' } },
    ],
  },

  // ===== 管理后台 =====
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue'), meta: { title: '数据概览', workbenchHome: true } },
      { path: 'audit', name: 'admin-audit', component: () => import('@/views/admin/AuditView.vue'), meta: { title: '审核管理' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'catalog', name: 'admin-catalog', component: () => import('@/views/admin/CatalogManageView.vue'), meta: { title: '目录管理' } },
      { path: 'reviews', name: 'admin-reviews', component: () => import('@/views/admin/ReviewsManageView.vue'), meta: { title: '评价管理' } },
      { path: 'complaints', name: 'admin-complaints', component: () => import('@/views/admin/ComplaintsManageView.vue'), meta: { title: '投诉管理' } },
      { path: 'announcements', name: 'admin-announcements', component: () => import('@/views/admin/AnnouncementsManageView.vue'), meta: { title: '公告管理' } },
      { path: 'ai-knowledge', name: 'admin-ai-knowledge', component: () => import('@/views/admin/AiKnowledgeManageView.vue'), meta: { title: 'AI 知识库' } },
      { path: 'ai-settings', name: 'admin-ai-settings', component: () => import('@/views/admin/AiSettingsView.vue'), meta: { title: 'AI 接口配置' } },
      { path: 'cs-accounts', name: 'admin-cs-accounts', component: () => import('@/views/admin/CsAccountsManageView.vue'), meta: { title: '客服账号' } },
      { path: 'operation-logs', name: 'admin-operation-logs', component: () => import('@/views/admin/OperationLogsView.vue'), meta: { title: '操作日志' } },
    ],
  },

  // ===== 登录与兜底 =====
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 滚动行为：前进/后退保持滚动位置，普通跳转回到顶部（锚点除外）
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

// 路由守卫：登录态 + 角色校验（真实权限由后端执行）
router.beforeEach(async (to) => {
  startRouteProgress()
  document.title = to.meta.title ? `${to.meta.title as string} - 游戏陪玩系统` : '游戏陪玩系统'
  const userStore = useUserStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)
  const requiredRoles = to.matched.flatMap((r) => (r.meta.roles as string[] | undefined) ?? [])
  const roleSatisfied = () =>
    requiredRoles.length === 0 || requiredRoles.some((role) => userStore.hasRole(role))

  if (requiresAuth && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (requiresAuth && (!userStore.user || !roleSatisfied())) {
    // 刷新后恢复登录态；角色不足时也刷新一次：
    // 角色集合写在 JWT 里，入驻审核通过（授予 COMPANION）后旧令牌中的角色是滞后的，
    // 因此以 `/auth/me` 返回的实时角色为准，避免"审核已通过但仍进不去工作台"
    try {
      await userStore.refreshUser()
    } catch {
      if (!userStore.user) {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
  }
  if (!roleSatisfied()) {
    // 角色仍不足：按分区兜底跳转（陪玩师端 → 入驻申请页），未配置兜底的仍在首页
    const fallback = to.matched.map((r) => r.meta.roleFallback).find((path) => !!path)
    return { path: fallback ?? '/' }
  }
  return true
})

router.afterEach(() => {
  stopRouteProgress()
})

router.onError(() => {
  stopRouteProgress()
})

export default router
