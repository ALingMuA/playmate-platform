import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 当前登录用户信息（骨架占位，后续对接 auth 接口） */
export interface LoginUser {
  id: number
  username: string
  nickname: string
  avatarUrl: string
  roles: string[]
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') ?? '')
  const user = ref<LoginUser | null>(null)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('token', value)
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  function hasRole(role: string): boolean {
    return user.value?.roles.includes(role) ?? false
  }

  return { token, user, setToken, logout, hasRole }
})
