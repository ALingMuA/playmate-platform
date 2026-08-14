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
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <h2>游戏陪玩系统</h2>
        <el-segmented v-model="mode" :options="[
          { label: '登录', value: 'login' },
          { label: '注册', value: 'register' },
        ]" style="margin-top: 12px" @change="(v: string | number | boolean) => (mode = v as 'login' | 'register')" />
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
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f0f2f5;
}
.login-card {
  width: 420px;
}
.action-btn {
  width: 100%;
}
</style>
