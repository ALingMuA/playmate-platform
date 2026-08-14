<script setup lang="ts">
/**
 * 个人中心（FR-A05 个人资料 / FR-A06 账号安全 / FR-A02 退出）。
 *
 * <p>展示并维护头像、昵称、性别、简介等资料，支持修改密码、注销全部会话与退出登录；
 * 同时展示虚拟钱包概览（FR-U09 余额）。数据来自 auth 模块与 wallet 模块接口。</p>
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

/** 用户资料（来自 store，refreshUser 保持最新） */
const user = ref<LoginUser | null>(null)
/** 钱包概览 */
const wallet = ref<{ balanceCents: number; frozenCents: number; totalIncomeCents: number } | null>(null)
const loading = ref(false)

/** 角色映射 */
const roleMap: Record<string, string> = {
  USER: '普通用户',
  COMPANION: '陪玩师',
  CUSTOMER_SERVICE: '客服人员',
  ADMIN: '管理员',
}

/** 性别映射：0未知，1男，2女 */
const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' }

async function load() {
  loading.value = true
  try {
    user.value = await userStore.refreshUser()
    try {
      wallet.value = await walletMe()
    } catch {
      wallet.value = null // 钱包接口失败不影响资料展示
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
const editForm = reactive({
  nickname: '',
  avatarUrl: '',
  gender: 0,
  introduction: '',
})

function openEdit() {
  const u = user.value
  if (!u) return
  editForm.nickname = u.nickname
  editForm.avatarUrl = u.avatarUrl
  editForm.gender = u.gender ?? 0
  editForm.introduction = u.introduction ?? ''
  editVisible.value = true
}

/** 头像上传（file 模块，category=avatar） */
async function handleAvatarUpload(file: File) {
  try {
    const result = await uploadFile(file, 'avatar')
    editForm.avatarUrl = result.url
    ElMessage.success('头像上传成功')
  } catch {
    // 上传失败已由 http 拦截器提示
  }
  return false // 阻止 el-upload 默认上传
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
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function openPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

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
    // 改密后旧令牌失效，服务端返回新令牌：更新本地登录态
    userStore.setToken(result.token)
    user.value = result.user
    ElMessage.success('密码修改成功，请牢记新密码')
    pwdVisible.value = false
  } finally {
    changingPwd.value = false
  }
}

// ==================== 账号安全与退出（FR-A06 / FR-A02） ====================

/** 注销全部会话：本地退出并跳转登录页 */
async function handleLogoutAll() {
  try {
    await ElMessageBox.confirm(
      '注销后所有设备的登录状态将立即失效，需要重新登录。确定继续吗？',
      '注销全部会话',
      { type: 'warning', confirmButtonText: '确定注销', cancelButtonText: '再想想' },
    )
  } catch {
    return // 用户取消
  }
  await logoutAll()
  userStore.logout()
  ElMessage.success('已注销全部会话')
  router.push('/login')
}

/** 退出登录（FR-A02）：无状态 JWT，客户端丢弃令牌即可 */
function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(load)
</script>

<template>
  <div class="profile-page">
    <div class="page-header">
      <h1>个人中心</h1>
      <p class="sub">维护个人资料与账号安全</p>
    </div>

    <div v-loading="loading" class="profile-body">
      <!-- 左侧：用户信息卡 -->
      <el-card class="info-card" shadow="hover">
        <div class="avatar-row">
          <el-avatar :size="72" :src="user?.avatarUrl || undefined" class="avatar">
            {{ user?.nickname?.charAt(0) ?? '游' }}
          </el-avatar>
          <div class="info-main">
            <h2 class="nickname">{{ user?.nickname ?? '-' }}</h2>
            <p class="username">@{{ user?.username ?? '-' }}</p>
            <div class="role-tags">
              <el-tag v-for="r in user?.roles ?? []" :key="r" size="small" type="primary" effect="plain">
                {{ roleMap[r] ?? r }}
              </el-tag>
            </div>
          </div>
        </div>
        <el-descriptions :column="1" class="detail" border>
          <el-descriptions-item label="手机号">{{ user?.mobile || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ user?.email || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ genderMap[user?.gender ?? 0] }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">{{ user?.accountStatus ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近登录">{{ fmtTime(user?.lastLoginAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions">
          <el-button type="primary" @click="openEdit">编辑资料</el-button>
          <el-button @click="openPwd">修改密码</el-button>
        </div>
      </el-card>

      <!-- 右侧：钱包与账号安全 -->
      <div class="side">
        <el-card class="wallet-card" shadow="hover">
          <template #header>虚拟钱包（模拟支付余额）</template>
          <div class="wallet-main">{{ wallet ? fmtMoney(wallet.balanceCents) : '-' }}</div>
          <div class="wallet-meta">
            <span>冻结：{{ wallet ? fmtMoney(wallet.frozenCents) : '-' }}</span>
            <span>累计收入：{{ wallet ? fmtMoney(wallet.totalIncomeCents) : '-' }}</span>
          </div>
        </el-card>

        <el-card class="security-card" shadow="hover">
          <template #header>账号安全</template>
          <div class="security-actions">
            <el-button plain @click="handleLogoutAll">注销全部会话</el-button>
            <el-button plain type="danger" @click="handleLogout">退出登录</el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 编辑资料对话框 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="520px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="头像">
          <div class="avatar-edit">
            <el-avatar :size="56" :src="editForm.avatarUrl || undefined">
              {{ editForm.nickname?.charAt(0) || '游' }}
            </el-avatar>
            <el-upload :show-file-list="false" accept="image/*" :before-upload="handleAvatarUpload">
              <el-button size="small">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="昵称" required>
          <el-input v-model="editForm.nickname" maxlength="32" show-word-limit placeholder="1~32 字符" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="editForm.introduction" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="介绍一下自己（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="480px">
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password
            placeholder="8~32 位，须同时包含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  color: #909399;
  margin: 0 0 20px;
}
.profile-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.info-card {
  flex: 1;
}
.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.avatar {
  background: var(--brand-primary);
  color: #fff;
  font-size: 28px;
}
.nickname {
  margin: 0 0 4px;
}
.username {
  margin: 0 0 8px;
  color: #909399;
  font-size: 13px;
}
.role-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.detail {
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 8px;
}
.side {
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.wallet-main {
  font-size: 28px;
  font-weight: 700;
  color: #f56c6c;
  margin-bottom: 8px;
}
.wallet-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #909399;
}
.security-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.avatar-edit {
  display: flex;
  align-items: center;
  gap: 12px;
}
@media (max-width: 768px) {
  .profile-body {
    flex-direction: column;
  }
  .side {
    width: 100%;
  }
}
</style>
