<script setup lang="ts">
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

/** 登录成功后的回跳目标（仅允许站内路径，防开放重定向） */
function safeRedirect(): string {
  const redirect = route.query.redirect as string
  return redirect && redirect.startsWith('/') ? redirect : '/'
}

async function handleLogin() {
  if (!form.account || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.account, form.password)
    ElMessage.success('登录成功')
    router.push(safeRedirect())
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
  <div class="login-page">
    <div class="login-bg" />
    <el-card class="login-card">
      <template #header>
        <div class="card-head">
          <div class="brand-row">
            <span class="brand-icon">🎮</span>
            <h2 class="brand-name">游戏陪玩系统</h2>
          </div>
          <p class="brand-sub">找陪玩、约大神、随时开黑</p>
          <el-segmented
            v-model="mode"
            :options="[
              { label: '登录', value: 'login' },
              { label: '注册', value: 'register' },
            ]"
            class="mode-switch"
          />
        </div>
      </template>

      <el-form v-if="mode === 'login'" :model="form" label-width="0" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.account" placeholder="用户名 / 手机号 / 邮箱" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" size="large" class="action-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
        <div class="back-row">
          <el-button text type="primary" @click="router.push('/')">返回首页</el-button>
        </div>
      </el-form>

      <el-form v-else :model="form" label-width="80px" @submit.prevent="handleRegister">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="展示昵称" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="至少8位，含字母和数字" show-password />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.mobile" placeholder="手机号与邮箱至少填一项" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-button type="primary" size="large" class="action-btn" :loading="loading" @click="handleRegister">
          注册
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 12px;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
  background: #1f2d3d;
}
.login-card {
  position: relative;
  width: min(420px, 100%);
  border-radius: var(--radius-large);
  box-shadow: 0 12px 40px rgba(0, 21, 41, 0.25);
}
.card-head {
  text-align: center;
}
.brand-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.brand-icon {
  font-size: 26px;
}
.brand-name {
  margin: 0;
  font-size: 22px;
}
.brand-sub {
  margin: 6px 0 14px;
  font-size: 13px;
  color: var(--text-secondary);
}
.mode-switch {
  width: 100%;
}
.action-btn {
  width: 100%;
}
.back-row {
  margin-top: 8px;
  text-align: center;
}
</style>
