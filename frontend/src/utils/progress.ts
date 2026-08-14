import { reactive } from 'vue'

/** 全局路由加载进度条状态（由路由守卫驱动，App.vue 渲染） */
export const routeProgress = reactive({ active: false })

export function startRouteProgress() {
  routeProgress.active = true
}

export function stopRouteProgress() {
  routeProgress.active = false
}
