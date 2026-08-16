<script setup lang="ts">
/**
 * 个人中心（对齐 frontend-prototype profile.html：个人卡 + 钱包网格 + 账号安全）。
 *
 * <p>展示并维护头像、昵称、性别、简介等资料（FR-A05），展示虚拟钱包概览（FR-U09），
 * 支持修改密码（FR-A04）、注销全部会话（FR-A06）与退出登录。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword, logoutAll, updateProfile, type LoginUser } from '@/api/auth'
import { walletMe } from '@/api/order'
import { uploadFile } from '@/api/file'

const router = useRouter()
const userStore = useUserStore()

const user = ref<LoginUser | null>(null)
const wallet = ref<{ balanceCents: number; frozenCents: number; totalIncomeCents: number } | null>(null)
const loading = ref(false)

const roleMap: Record<string, string> = {
  USER: '普通用户',
  COMPANION: '陪玩师',
  CUSTOMER_SERVICE: '客服人员',
  ADMIN: '管理员',
}

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' }

async function load() {
  loading.value = true
  try {
    user.value = await userStore.refreshUser()
    try {
      wallet.value = await walletMe()
    } catch {
      wallet.value = null
    }
  } finally {
    loading.value = false
  }
}

function fmtMoney(cents: number): string {
  return '¥' + (cents / 100).toFixed(2)
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

// ==================== 编辑资料（FR-A05） ====================

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ nickname: '', avatarUrl: '', gender: 0, introduction: '' })

function openEdit() {
  const u = user.value
  if (!u) return
  editForm.nickname = u.nickname
  editForm.avatarUrl = u.avatarUrl
  editForm.gender = u.gender ?? 0
  editForm.introduction = u.introduction ?? ''
  editVisible.value = true
}

async function handleAvatarUpload(file: File) {
  try {
    const result = await uploadFile(file, 'avatar')
    editForm.avatarUrl = result.url
    ElMessage.success('头像上传成功')
  } catch {
    // 拦截器已提示
  }
  return false
}

async function handleSaveProfile() {
  if (!editForm.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    user.value = await updateProfile({
      nickname: editForm.nickname.trim(),
      avatarUrl: editForm.avatarUrl,
      gender: editForm.gender,
      introduction: editForm.introduction,
    })
    ElMessage.success('资料已更新')
    editVisible.value = false
  } finally {
    saving.value = false
  }
}

// ==================== 修改密码（FR-A04） ====================

const pwdVisible = ref(false)
const changingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function handleChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPwd.value = true
  try {
    const result = await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    userStore.setToken(result.token)
    user.value = result.user
    ElMessage.success('密码修改成功')
    pwdVisible.value = false
  } finally {
    changingPwd.value = false
  }
}

// ==================== 账号安全（FR-A06） ====================

async function handleLogoutAll() {
  try {
    await ElMessageBox.confirm(
      '注销后所有设备的登录状态将立即失效，需要重新登录。确定继续吗？',
      '注销全部会话',
      { type: 'warning', confirmButtonText: '确定注销', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  await logoutAll()
  userStore.logout()
  ElMessage.success('已注销全部会话')
  router.push('/login')
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">管理你的账号信息与资产</p>
      </div>

      <template v-if="user">
        <!-- 个人卡（原型 profile-card） -->
        <div class="card mb-2">
          <div class="card-body profile-card">
            <div class="profile-avatar">
              <el-avatar :size="96" :src="user.avatarUrl || undefined" style="width: 100%; height: 100%">
                {{ user.nickname?.charAt(0) ?? '游' }}
              </el-avatar>
            </div>
            <div class="profile-info">
              <h2>{{ user.nickname }}
                <span class="badge badge-secondary" style="vertical-align: middle">{{ roleMap[user.roles?.[0]] ?? user.roles?.[0] ?? '普通用户' }}</span>
              </h2>
              <div class="profile-meta">
                <span class="badge" :class="user.accountStatus === 'ENABLED' ? 'badge-success' : 'badge-warning'">
                  {{ user.accountStatus === 'ENABLED' ? '账号正常' : user.accountStatus }}
                </span>
                <span class="badge badge-outline">{{ genderMap[user.gender ?? 0] }}</span>
                <span class="badge badge-outline">最近登录 {{ fmtTime(user.lastLoginAt) }}</span>
              </div>
              <p class="mt-2 text-muted" style="font-size: 0.875rem">
                手机号：{{ user.mobile || '未绑定' }} · 邮箱：{{ user.email || '未绑定' }}
              </p>
              <p v-if="user.introduction" class="text-muted" style="font-size: 0.875rem">
                {{ user.introduction }}
              </p>
            </div>
            <div style="margin-left: auto">
              <button class="btn btn-primary btn-sm" @click="openEdit">编辑资料</button>
            </div>
          </div>
        </div>

        <!-- 钱包（原型 wallet-grid，FR-U09） -->
        <div class="wallet-grid">
          <div class="wallet-card">
            <div class="wallet-value">{{ wallet ? fmtMoney(wallet.balanceCents) : '-' }}</div>
            <div class="wallet-label">可用余额</div>
          </div>
          <div class="wallet-card">
            <div class="wallet-value" style="color: var(--muted-foreground)">
              {{ wallet ? fmtMoney(wallet.frozenCents) : '-' }}
            </div>
            <div class="wallet-label">冻结金额</div>
          </div>
          <div class="wallet-card">
            <div class="wallet-value" style="color: var(--success, #16a34a)">
              {{ wallet ? fmtMoney(wallet.totalIncomeCents) : '-' }}
            </div>
            <div class="wallet-label">累计收入</div>
          </div>
        </div>

        <!-- 账号安全 -->
        <div class="card mt-2">
          <div class="card-body">
            <h3 class="card-title mb-2">账号安全</h3>
            <div class="flex gap-1 flex-wrap">
              <button class="btn btn-outline btn-sm" @click="pwdVisible = true">修改密码</button>
              <button class="btn btn-outline btn-sm" @click="handleLogoutAll">注销全部会话</button>
              <button class="btn btn-destructive btn-sm" @click="handleLogout">退出登录</button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 编辑资料对话框 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="520px">
      <el-form label-width="80px">
        <el-form-item label="头像">
          <div class="flex gap-1 items-center">
            <el-avatar :size="56" :src="editForm.avatarUrl || undefined">
              {{ editForm.nickname?.charAt(0) || '游' }}
            </el-avatar>
            <el-upload :show-file-list="false" accept="image/*" :before-upload="handleAvatarUpload">
              <el-button size="small">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="昵称" required>
          <el-input v-model="editForm.nickname" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="editForm.introduction" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="480px">
      <el-form label-width="90px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password
            placeholder="8~32 位，须同时包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>
