<script setup lang="ts">
/**
 * 登录 / 注册（对齐 frontend-prototype login.html：左右分栏）。
 *
 * <p>左侧品牌视觉区，右侧登录/注册表单（auth-tabs 切换）；
 * 登录成功后跳转 redirect 回跳地址或各角色专属工作台首页。
 * 同时提供测试快捷登录通道，支持管理员、陪玩师、客服人员、普通用户全角色一键登录。</p>
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

interface QuickAccount {
  roleName: string
  roleCode: 'ADMIN' | 'COMPANION' | 'CUSTOMER_SERVICE' | 'USER'
  account: string
  password: string
  tag: string
  description: string
  defaultPath: string
}

const quickAccounts: QuickAccount[] = [
  {
    roleName: '管理员',
    roleCode: 'ADMIN',
    account: 'admin',
    password: 'Admin@123456',
    tag: '运营后台',
    description: '数据概览与审核管理',
    defaultPath: '/admin/dashboard',
  },
  {
    roleName: '陪玩师',
    roleCode: 'COMPANION',
    account: 'companion_test',
    password: 'Test123456',
    tag: '陪玩工作台',
    description: '接单履约与档期收益',
    defaultPath: '/companion/orders',
  },
  {
    roleName: '客服人员',
    roleCode: 'CUSTOMER_SERVICE',
    account: 'cs_test',
    password: 'Test123456',
    tag: '客服中心',
    description: '咨询接待与会话管理',
    defaultPath: '/cs/queue',
  },
  {
    roleName: '普通用户',
    roleCode: 'USER',
    account: 'user_test',
    password: 'Test123456',
    tag: '玩家前台',
    description: '浏览游戏与预约下单',
    defaultPath: '/',
  },
]

function resolveRedirectPath(qa?: QuickAccount): string {
  const redirect = (route.query.redirect as string) || ''
  const defaultPath = qa ? qa.defaultPath : (
    userStore.hasRole('ADMIN') ? '/admin/dashboard' :
    userStore.hasRole('CUSTOMER_SERVICE') ? '/cs/queue' :
    userStore.hasRole('COMPANION') ? '/companion/orders' : '/'
  )

  if (!redirect || redirect === '/' || redirect === '/login') {
    return defaultPath
  }

  // 校验目标路由与当前角色权限匹配度，避免被路由守卫直接拦截
  if (qa) {
    if (redirect.startsWith('/admin') && qa.roleCode !== 'ADMIN') {
      return defaultPath
    }
    if (redirect.startsWith('/companion') && qa.roleCode !== 'COMPANION' && qa.roleCode !== 'ADMIN') {
      return defaultPath
    }
    if (redirect.startsWith('/cs') && qa.roleCode !== 'CUSTOMER_SERVICE' && qa.roleCode !== 'ADMIN') {
      return defaultPath
    }
  }
  return redirect
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
    const targetPath = resolveRedirectPath()
    router.push(targetPath)
  } finally {
    loading.value = false
  }
}

async function handleQuickLogin(qa: QuickAccount) {
  if (loading.value) return
  mode.value = 'login'
  form.account = qa.account
  form.password = qa.password
  loading.value = true
  try {
    await userStore.login(qa.account, qa.password)
    ElMessage.success(`以【${qa.roleName}】身份一键登录成功`)
    const targetPath = resolveRedirectPath(qa)
    router.push(targetPath)
  } catch (err: any) {
    ElMessage.error(err?.message || `【${qa.roleName}】一键登录失败，请确认测试账号已在数据库初始化`)
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

      <!-- 测试快捷登录通道 -->
      <div v-if="mode === 'login'" class="quick-login-section">
        <div class="quick-divider">
          <span>测试快捷登录通道</span>
        </div>
        <div class="quick-grid">
          <button
            v-for="qa in quickAccounts"
            :key="qa.roleCode"
            type="button"
            class="quick-card"
            :class="`role-${qa.roleCode.toLowerCase()}`"
            :disabled="loading"
            @click="handleQuickLogin(qa)"
          >
            <div class="quick-card-header">
              <span class="role-name">{{ qa.roleName }}</span>
              <span class="role-badge">{{ qa.tag }}</span>
            </div>
            <div class="quick-card-account">账号: {{ qa.account }}</div>
            <div class="quick-card-desc">{{ qa.description }}</div>
          </button>
        </div>
        <div class="quick-tip">
          💡 点击任意角色卡片将自动填入账号并直接登录进入对应工作台
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.field-error { display: block; margin-top: 4px; color: #dc2626; font-size: 0.8125rem; }

.quick-login-section {
  width: min(100%, 520px);
  margin-top: 24px;
}

.quick-divider {
  position: relative;
  text-align: center;
  margin: 16px 0 20px;
}

.quick-divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--border, #ebebeb);
  z-index: 0;
}

.quick-divider span {
  position: relative;
  z-index: 1;
  background: var(--background, #fff);
  padding: 0 14px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted-foreground, #7f8d9f);
  letter-spacing: 0.5px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid var(--border, #ebebeb);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  outline: none;
}

.quick-card:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}

.quick-card:active:not(:disabled) {
  transform: translateY(0);
}

.quick-card:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.quick-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 4px;
}

.role-name {
  font-size: 0.9375rem;
  font-weight: 700;
  color: var(--foreground, #0e1115);
}

.role-badge {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1.2;
}

.quick-card-account {
  font-size: 0.75rem;
  color: #64748b;
  font-family: monospace;
  margin-bottom: 2px;
}

.quick-card-desc {
  font-size: 0.75rem;
  color: #94a3b8;
  line-height: 1.3;
}

/* 角色个性化配色 */
.quick-card.role-admin {
  border-left: 3px solid #8b5cf6;
}
.quick-card.role-admin:hover:not(:disabled) {
  border-color: #8b5cf6;
  background: #faf5ff;
}
.quick-card.role-admin .role-badge {
  background: #ede9fe;
  color: #7c3aed;
}

.quick-card.role-companion {
  border-left: 3px solid #f59e0b;
}
.quick-card.role-companion:hover:not(:disabled) {
  border-color: #f59e0b;
  background: #fffbeb;
}
.quick-card.role-companion .role-badge {
  background: #fef3c7;
  color: #d97706;
}

.quick-card.role-customer_service {
  border-left: 3px solid #06b6d4;
}
.quick-card.role-customer_service:hover:not(:disabled) {
  border-color: #06b6d4;
  background: #ecfeff;
}
.quick-card.role-customer_service .role-badge {
  background: #cffafe;
  color: #0891b2;
}

.quick-card.role-user {
  border-left: 3px solid #3b82f6;
}
.quick-card.role-user:hover:not(:disabled) {
  border-color: #3b82f6;
  background: #eff6ff;
}
.quick-card.role-user .role-badge {
  background: #dbeafe;
  color: #2563eb;
}

.quick-tip {
  margin-top: 12px;
  font-size: 0.75rem;
  color: #94a3b8;
  text-align: center;
}

@media (max-width: 768px) {
  .auth-page {
    display: block;
  }
}

@media (max-width: 480px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
