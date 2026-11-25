import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, login as apiLogin, type LoginUser } from '@/api/auth'
import { resetUnauthorizedState } from '@/api/http'
import { useNotificationStore } from '@/stores/notification'
import { csSocket } from '@/utils/ws'

let detachStorageListener: (() => void) | null = null

/** 当前登录用户状态：token 持久化 + 用户信息 + 角色判断 + 跨标签页同步 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const user = ref<LoginUser | null>(null)
  let refreshPromise: Promise<LoginUser | null> | null = null

  const isLoggedIn = computed(() => !!token.value)
  const roles = computed(() => user.value?.roles ?? [])

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
    resetUnauthorizedState()
  }

  function clearSessionState() {
    token.value = ''
    user.value = null
    refreshPromise = null
    csSocket.close()
    useNotificationStore().clearUnread()
  }

  /** 登录并保存用户信息（切换账号时先清理旧账号缓存与连接） */
  async function login(account: string, password: string) {
    csSocket.close()
    useNotificationStore().clearUnread()
    const result = await apiLogin(account, password)
    setToken(result.token)
    user.value = result.user
    return result.user
  }

  /** 拉取当前用户信息（并发去重，页面刷新后恢复登录态） */
  async function refreshUser(options?: { silentError?: boolean; skipAuthRedirect?: boolean }) {
    const activeToken = token.value
    if (!activeToken) return null
    if (refreshPromise) return refreshPromise
    refreshPromise = (async () => {
      try {
        const me = await fetchMe(options)
        if (token.value === activeToken) {
          user.value = me
        }
        return me
      } finally {
        refreshPromise = null
      }
    })()
    return refreshPromise
  }

  function logout() {
    localStorage.removeItem('token')
    clearSessionState()
  }

  function hasRole(role: string): boolean {
    return user.value?.roles.includes(role) ?? false
  }

  if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== null && event.key !== 'token') return
      const nextToken = event.key === null ? (localStorage.getItem('token') ?? '') : (event.newValue ?? '')
      if (nextToken === token.value) return
      if (!nextToken) {
        clearSessionState()
      } else {
        token.value = nextToken
        user.value = null
        refreshPromise = null
        resetUnauthorizedState()
        csSocket.close()
        useNotificationStore().clearUnread()
        void refreshUser({ silentError: true, skipAuthRedirect: true }).catch(() => {
          // 跨标签页同步失败时保持未水合状态，由下一次导航守卫或请求处理
        })
      }
    }
    detachStorageListener?.()
    window.addEventListener('storage', onStorage)
    detachStorageListener = () => window.removeEventListener('storage', onStorage)
  }

  return { token, user, isLoggedIn, roles, setToken, login, refreshUser, logout, hasRole }
})
