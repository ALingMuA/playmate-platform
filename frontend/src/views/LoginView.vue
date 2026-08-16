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
  if (!form.username || !form.password || !form.nickname) {
    ElMessage.warning('请完整填写注册信息')
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
        <p class="mt-2 text-muted" style="font-size: 0.8125rem; text-align: center">
          演示账号 admin / Admin@123456
        </p>
      </form>

      <!-- 注册表单 -->
      <form v-else class="auth-panel active" @submit.prevent="handleRegister">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input v-model="form.username" class="form-control" placeholder="设置登录账号" autocomplete="username" />
        </div>
        <div class="form-group">
          <label class="form-label">昵称</label>
          <input v-model="form.nickname" class="form-control" placeholder="展示昵称" />
        </div>
        <div class="form-group">
          <label class="form-label">手机号</label>
          <input v-model="form.mobile" class="form-control" placeholder="选填，手机号与邮箱至少一项" autocomplete="tel" />
        </div>
        <div class="form-group">
          <label class="form-label">邮箱</label>
          <input v-model="form.email" class="form-control" placeholder="选填" autocomplete="email" />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="form-control"
            placeholder="至少8位，含字母和数字" autocomplete="new-password" />
        </div>
        <button type="submit" class="btn btn-primary btn-block btn-lg" :disabled="loading">
          {{ loading ? '注册中…' : '注 册' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
@media (max-width: 768px) {
  .auth-page {
    display: block;
  }
}
</style>
