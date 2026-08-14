import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 前端路由分区（与《概要设计说明书》3.3 节一致）：
 *   /           用户端（游客、普通用户、陪玩师共用）
 *   /companion  陪玩师端（已审核陪玩师）
 *   /support    在线客服（普通用户、陪玩师）
 *   /cs         客服工作台（客服人员）
 *   /admin      管理后台（管理员）
 *
 * 路由守卫仅改善体验；真实权限判断由后端 Spring Security 与数据范围校验执行。
 */
const routes: RouteRecordRaw[] = [
  // ===== 用户端 =====
  {
    path: '/',
    name: 'user-home',
    component: () => import('@/views/user/HomeView.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/games',
    name: 'user-games',
    component: () => import('@/views/user/GamesView.vue'),
    meta: { title: '游戏' },
  },
  {
    path: '/companions',
    name: 'user-companions',
    component: () => import('@/views/user/CompanionsView.vue'),
    meta: { title: '陪玩师' },
  },
  {
    path: '/orders',
    name: 'user-orders',
    component: () => import('@/views/user/OrdersView.vue'),
    meta: { title: '我的订单', requiresAuth: true },
  },
  {
    path: '/profile',
    name: 'user-profile',
    component: () => import('@/views/user/ProfileView.vue'),
    meta: { title: '个人中心', requiresAuth: true },
  },

  // ===== 陪玩师端 =====
  {
    path: '/companion',
    component: () => import('@/views/companion/CompanionLayout.vue'),
    meta: { requiresAuth: true, roles: ['COMPANION'] },
    children: [
      { path: '', redirect: '/companion/schedule' },
      { path: 'application', name: 'companion-application', component: () => import('@/views/companion/ApplicationView.vue'), meta: { title: '入驻申请' } },
      { path: 'services', name: 'companion-services', component: () => import('@/views/companion/ServiceManageView.vue'), meta: { title: '服务管理' } },
      { path: 'schedule', name: 'companion-schedule', component: () => import('@/views/companion/ScheduleView.vue'), meta: { title: '档期管理' } },
      { path: 'orders', name: 'companion-orders', component: () => import('@/views/companion/OrderFulfillView.vue'), meta: { title: '接单履约' } },
      { path: 'earnings', name: 'companion-earnings', component: () => import('@/views/companion/EarningsView.vue'), meta: { title: '收益' } },
    ],
  },

  // ===== 在线客服（用户侧） =====
  {
    path: '/support',
    name: 'support',
    component: () => import('@/views/support/SupportView.vue'),
    meta: { title: '在线客服', requiresAuth: true },
  },

  // ===== 客服工作台 =====
  {
    path: '/cs',
    component: () => import('@/views/cs/CsLayout.vue'),
    meta: { requiresAuth: true, roles: ['CUSTOMER_SERVICE'] },
    children: [
      { path: '', redirect: '/cs/queue' },
      { path: 'queue', name: 'cs-queue', component: () => import('@/views/cs/QueueView.vue'), meta: { title: '会话队列' } },
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
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue'), meta: { title: '数据概览' } },
      { path: 'audit', name: 'admin-audit', component: () => import('@/views/admin/AuditView.vue'), meta: { title: '审核管理' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'reviews', name: 'admin-reviews', component: () => import('@/views/admin/ReviewsManageView.vue'), meta: { title: '评价管理' } },
      { path: 'complaints', name: 'admin-complaints', component: () => import('@/views/admin/ComplaintsManageView.vue'), meta: { title: '投诉管理' } },
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
})

// 路由守卫：登录态 + 角色校验（真实权限由后端执行）
router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title as string} - 游戏陪玩系统` : '游戏陪玩系统'
  const userStore = useUserStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)
  const requiredRoles = to.matched.flatMap((r) => (r.meta.roles as string[] | undefined) ?? [])

  if (requiresAuth && !userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (requiresAuth && !userStore.user) {
    // 刷新后恢复登录态
    try {
      await userStore.refreshUser()
    } catch {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (requiredRoles.length > 0 && !requiredRoles.some((role) => userStore.hasRole(role))) {
    return { path: '/' }
  }
  return true
})

export default router
