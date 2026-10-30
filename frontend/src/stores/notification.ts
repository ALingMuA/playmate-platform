import { defineStore } from 'pinia'
import { ref } from 'vue'
import { unreadNotificationCount } from '@/api/notification'
import { useUserStore } from '@/stores/user'

/**
 * 站内通知状态管理 Store（FR-A07 & 统一消息中心）
 * 严格语义隔离：只统计 /api/notifications/unread-count 的个人通知，绝不掺入平台公告。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref<number>(0)
  const loading = ref<boolean>(false)

  /**
   * 拉取未读通知数量
   * 未登录时自动清零且不发起接口调用，避免 401 报错
   */
  async function fetchUnread(): Promise<void> {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      unreadCount.value = 0
      return
    }
    loading.value = true
    try {
      const res = await unreadNotificationCount()
      unreadCount.value = res.count ?? 0
    } catch {
      // 忽略未读数拉取静默失败，避免打断界面主流程
    } finally {
      loading.value = false
    }
  }

  /**
   * 递减未读数（单条通知标记已读时）
   */
  function decrementUnread(amount = 1): void {
    unreadCount.value = Math.max(0, unreadCount.value - amount)
  }

  /**
   * 清零未读数（一键全部已读或退出登录时）
   */
  function clearUnread(): void {
    unreadCount.value = 0
  }

  /**
   * 直接设置未读数
   */
  function setUnread(count: number): void {
    unreadCount.value = Math.max(0, count)
  }

  return {
    unreadCount,
    loading,
    fetchUnread,
    refreshUnread: fetchUnread,
    decrementUnread,
    clearUnread,
    setUnread,
  }
})
