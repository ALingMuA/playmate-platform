<script setup lang="ts">
/**
 * 登录 / 注册（对齐 frontend-prototype login.html：左右分栏）。
 *
 * <p>左侧品牌视觉区，右侧登录/注册表单（auth-tabs 切换）；
 * 登录成功后跳转 redirect 回跳地址。</p>
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { register } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({
  account: '',
  password: '',
  username: '',
  nickname: '',
  mobile: '',
  email: '',
})
const registerErrors = reactive({
  username: '',
  nickname: '',
  contact: '',
  password: '',
})

async function handleLogin() {
  if (!form.account || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.account, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  registerErrors.username = /^[A-Za-z0-9_]{4,32}$/.test(form.username)
    ? '' : '用户名须为 4～32 位字母、数字或下划线'
  registerErrors.nickname = form.nickname.trim() ? '' : '请输入昵称'
  registerErrors.contact = form.mobile.trim() || form.email.trim()
    ? '' : '手机号与邮箱至少填写一项'
  registerErrors.password = /^(?=.*[A-Za-z])(?=.*\d)\S{8,32}$/.test(form.password)
    ? '' : '密码须为 8～32 位且同时包含字母和数字'
  if (Object.values(registerErrors).some(Boolean)) {
    ElMessage.warning('请修正注册表单中的错误')
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      mobile: form.mobile || undefined,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功，请登录')
    form.account = form.username
    mode.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌视觉 -->
    <div class="auth-visual">
      <h1>欢迎来到<br />游戏陪玩系统</h1>
      <p>专业陪玩、开黑上分、游戏教学，发现更多游戏乐趣。注册即可开启你的专属陪玩之旅。</p>
    </div>

    <!-- 右侧表单 -->
    <div class="auth-form-wrap">
      <div class="auth-tabs">
        <button class="auth-tab" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button class="auth-tab" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <!-- 登录表单 -->
      <form v-if="mode === 'login'" class="auth-panel active" @submit.prevent="handleLogin">
        <div class="form-group">
          <label class="form-label">用户名 / 手机号</label>
          <input v-model="form.account" class="form-control" placeholder="请输入账号" autocomplete="username" />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="form-control" placeholder="请输入密码"
            autocomplete="current-password" @keyup.enter="handleLogin" />
        </div>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>

      <!-- 注册表单 -->
      <form v-else class="auth-panel active" @submit.prevent="handleRegister">
        <div class="form-group">
          <label class="form-label" for="register-username">用户名</label>
          <input id="register-username" v-model="form.username" class="form-control" placeholder="设置登录账号" autocomplete="username" :aria-invalid="!!registerErrors.username" />
          <small v-if="registerErrors.username" class="field-error">{{ registerErrors.username }}</small>
        </div>
        <div class="form-group">
          <label class="form-label" for="register-nickname">昵称</label>
          <input id="register-nickname" v-model="form.nickname" class="form-control" placeholder="展示昵称" :aria-invalid="!!registerErrors.nickname" />
          <small v-if="registerErrors.nickname" class="field-error">{{ registerErrors.nickname }}</small>
        </div>
        <div class="form-group">
          <label class="form-label" for="register-mobile">手机号</label>
          <input id="register-mobile" v-model="form.mobile" class="form-control" placeholder="选填，手机号与邮箱至少一项" autocomplete="tel" :aria-invalid="!!registerErrors.contact" />
        </div>
        <div class="form-group">
          <label class="form-label" for="register-email">邮箱</label>
          <input id="register-email" v-model="form.email" class="form-control" placeholder="选填" autocomplete="email" :aria-invalid="!!registerErrors.contact" />
          <small v-if="registerErrors.contact" class="field-error">{{ registerErrors.contact }}</small>
        </div>
        <div class="form-group">
          <label class="form-label" for="register-password">密码</label>
          <input id="register-password" v-model="form.password" type="password" class="form-control"
            placeholder="8～32位，含字母和数字" autocomplete="new-password" :aria-invalid="!!registerErrors.password" />
          <small v-if="registerErrors.password" class="field-error">{{ registerErrors.password }}</small>
        </div>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading">
          {{ loading ? '注册中…' : '注 册' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.field-error { display: block; margin-top: 4px; color: #dc2626; font-size: 0.8125rem; }
@media (max-width: 768px) {
  .auth-page {
    display: block;
  }
}
</style>
