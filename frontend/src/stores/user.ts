import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, login as apiLogin, type LoginUser } from '@/api/auth'

/** 当前登录用户状态：token 持久化 + 用户信息 + 角色判断 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const user = ref<LoginUser | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const roles = computed(() => user.value?.roles ?? [])

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
  }

  /** 登录并保存用户信息 */
  async function login(account: string, password: string) {
    const result = await apiLogin(account, password)
    setToken(result.token)
    user.value = result.user
    return result.user
  }

  /** 拉取当前用户信息（页面刷新后恢复登录态） */
  async function refreshUser() {
    if (!token.value) return null
    user.value = await fetchMe()
    return user.value
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  function hasRole(role: string): boolean {
    return user.value?.roles.includes(role) ?? false
  }

  return { token, user, isLoggedIn, roles, setToken, login, refreshUser, logout, hasRole }
})
