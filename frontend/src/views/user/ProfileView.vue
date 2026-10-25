<script setup lang="ts">
/**
 * 个人主页：账户资料 + 个人业务数据聚合页（概览/订单/收藏/钱包/设置）。
 *
 * <p>所有用户在此查看与维护个人信息、订单、收藏与钱包（FR-A05/A04/A06、FR-U09）；
 * 陪玩师身份以「陪玩服务」分区叠加：接单状态与陪玩主页资料就地维护（FR-P05/P06），
 * 游戏能力认证为平台审核制。普通用户主页不含入驻引导，申请入口在用户菜单与找陪玩页。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { changePassword, logoutAll, updateProfile, type LoginUser } from '@/api/auth'
import { uploadFile } from '@/api/file'
import { myProfile, type CompanionProfile } from '@/api/companion'
import ProfileOverviewPanel from '@/components/user/ProfileOverviewPanel.vue'
import OrdersPanel from '@/components/user/OrdersPanel.vue'
import CompanionProfilePanel from '@/components/user/CompanionProfilePanel.vue'
import ProfileFavoritesPanel from '@/components/user/ProfileFavoritesPanel.vue'
import ProfileWalletPanel from '@/components/user/ProfileWalletPanel.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const user = ref<LoginUser | null>(null)
const loading = ref(false)
const activeTab = ref('overview')
const visitedTabs = ref(new Set<string>(['overview']))

// 陪玩师身份叠加：仅 COMPANION 角色加载陪玩资料（普通用户不请求陪玩接口）
const isCompanion = computed(() => user.value?.roles?.includes('COMPANION') ?? false)
const companionProfile = ref<CompanionProfile | null>(null)

const profileTabs = computed(() => [
  { key: 'overview', label: '概览' },
  { key: 'orders', label: '订单' },
  { key: 'favorites', label: '收藏' },
  { key: 'wallet', label: '钱包' },
  ...(isCompanion.value ? [{ key: 'companion', label: '陪玩服务' }] : []),
  { key: 'settings', label: '设置' },
])

const profileCompleteness = computed(() => {
  if (!user.value) return 0
  const completed = [user.value.nickname, user.value.avatarUrl, user.value.introduction]
    .filter((value) => Boolean(value?.trim())).length
  return Math.round((completed / 3) * 100)
})

function readHashTab(hash: string) {
  // 旧版 #tab=reviews 回落到概览；#tab=companion 仅陪玩师有效
  const match = hash.match(/^#tab=(overview|orders|favorites|wallet|companion|settings)$/)
  const key = match?.[1]
  return key && profileTabs.value.some((tab) => tab.key === key) ? key : 'overview'
}

async function selectTab(key: string) {
  activeTab.value = key
  visitedTabs.value = new Set([...visitedTabs.value, key])
  if (route.hash !== `#tab=${key}`) {
    await router.replace({ name: 'user-profile', hash: `#tab=${key}` })
  }
}

const serviceStatusText: Record<string, string> = {
  AVAILABLE: '可接单',
  BUSY: '忙碌',
  RESTING: '休息中',
  SUSPENDED: '资格暂停',
}

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
    // 陪玩资料仅陪玩师角色加载；探测失败不影响个人主页其它区块
    companionProfile.value = isCompanion.value ? await myProfile(true).catch(() => null) : null
    activeTab.value = readHashTab(route.hash)
    visitedTabs.value = new Set([...visitedTabs.value, activeTab.value])
  } finally {
    loading.value = false
  }
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
watch(() => route.hash, (hash) => {
  activeTab.value = readHashTab(hash)
  visitedTabs.value = new Set([...visitedTabs.value, activeTab.value])
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <div class="page-header">
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">管理个人资料，查看你的订单、收藏与钱包</p>
      </div>

      <template v-if="user">
        <section class="card profile-hero mb-2">
          <div class="card-body profile-card">
            <div class="profile-avatar">
              <el-avatar :size="88" :src="user.avatarUrl || undefined">{{ user.nickname?.charAt(0) ?? '游' }}</el-avatar>
            </div>
            <div class="profile-info">
              <h2>{{ user.nickname }}
                <span class="badge badge-secondary" style="vertical-align: middle">{{ roleMap[user.roles?.[0]] ?? user.roles?.[0] ?? '普通用户' }}</span>
              </h2>
              <div class="profile-meta">
                <span class="badge" :class="user.accountStatus === 'ENABLED' ? 'badge-success' : 'badge-warning'">
                  {{ user.accountStatus === 'ENABLED' ? '账号正常' : user.accountStatus }}
                </span>
                <span v-if="isCompanion" class="badge badge-outline">陪玩师</span>
              </div>
              <p v-if="user.introduction" class="text-muted mt-1">{{ user.introduction }}</p>
              <div class="profile-completeness mt-1" :aria-label="`资料完整度 ${profileCompleteness}%`">
                <span>资料完整度 {{ profileCompleteness }}%</span>
                <div class="profile-completeness-track"><span :style="{ width: `${profileCompleteness}%` }" /></div>
              </div>
            </div>
            <button class="btn btn-primary btn-sm profile-edit-button" @click="openEdit">编辑资料</button>
          </div>
        </section>

        <nav class="profile-tabs" aria-label="个人中心分区">
          <button v-for="tab in profileTabs" :key="tab.key" type="button" class="profile-tab"
            :class="{ active: activeTab === tab.key }" :aria-current="activeTab === tab.key ? 'page' : undefined"
            @click="selectTab(tab.key)">
            {{ tab.label }}
          </button>
        </nav>

        <main id="profile-tab-content" class="profile-tab-content">
          <section v-if="visitedTabs.has('overview')" v-show="activeTab === 'overview'" class="profile-overview-section">
            <!-- 陪玩师身份入口卡：数据与编辑统一在「陪玩服务」分区，此处不重复展示 -->
            <section v-if="companionProfile" class="card mb-2">
              <div class="card-body">
                <div class="flex items-center justify-between flex-wrap gap-1">
                  <h2 class="card-title">陪玩服务</h2>
                  <div class="flex gap-1 flex-wrap">
                    <span class="badge" :class="companionProfile.serviceStatus === 'AVAILABLE' ? 'badge-success' : 'badge-warning'">
                      {{ serviceStatusText[companionProfile.serviceStatus] ?? companionProfile.serviceStatus }}
                    </span>
                    <span class="badge badge-outline">
                      {{ companionProfile.certificationStatus === 'APPROVED' ? '认证通过' : '资格已暂停' }}
                    </span>
                  </div>
                </div>
                <p class="text-muted mt-1">评分、接单状态与主页资料统一在「陪玩服务」分区维护。</p>
                <div class="flex gap-1 mt-2 flex-wrap">
                  <button class="btn btn-primary btn-sm" @click="selectTab('companion')">管理陪玩服务 →</button>
                  <button class="btn btn-outline btn-sm" @click="router.push(`/companions/${user.id}`)">预览公开主页</button>
                </div>
              </div>
            </section>
            <ProfileOverviewPanel />
          </section>

          <OrdersPanel v-if="visitedTabs.has('orders')" v-show="activeTab === 'orders'" />

          <ProfileFavoritesPanel v-if="visitedTabs.has('favorites')" v-show="activeTab === 'favorites'" />

          <ProfileWalletPanel v-if="visitedTabs.has('wallet')" v-show="activeTab === 'wallet'" />

          <CompanionProfilePanel
            v-if="visitedTabs.has('companion') && isCompanion"
            v-show="activeTab === 'companion'"
            @updated="companionProfile = $event"
          />

          <section v-if="visitedTabs.has('settings')" v-show="activeTab === 'settings'" class="profile-settings">
            <div class="card">
              <div class="card-body">
                <h2 class="card-title">账号资料</h2>
                <p class="text-muted mt-1">性别：{{ genderMap[user.gender ?? 0] }} · 最近登录：{{ fmtTime(user.lastLoginAt) }}</p>
                <p class="text-muted mt-1">手机号：{{ user.mobile || '未绑定' }} · 邮箱：{{ user.email || '未绑定' }}</p>
                <button class="btn btn-primary btn-sm mt-2" @click="openEdit">编辑个人资料</button>
              </div>
            </div>
            <div class="card">
              <div class="card-body">
                <h2 class="card-title mb-2">账号安全</h2>
                <div class="flex gap-1 flex-wrap">
                  <button class="btn btn-outline btn-sm" @click="pwdVisible = true">修改密码</button>
                  <button class="btn btn-outline btn-sm" @click="handleLogoutAll">注销全部会话</button>
                  <button class="btn btn-destructive btn-sm" @click="handleLogout">退出登录</button>
                </div>
              </div>
            </div>
          </section>
        </main>
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

<style scoped>
.profile-hero .profile-card { align-items: center; gap: 18px; }
.profile-hero .profile-info { flex: 1; min-width: 0; }
.profile-edit-button { align-self: flex-start; }
.profile-completeness { max-width: 260px; display: grid; gap: 5px; color: var(--muted-foreground); font-size: 12px; }
.profile-completeness-track { height: 6px; border-radius: 999px; overflow: hidden; background: var(--muted); }
.profile-completeness-track span { display: block; height: 100%; border-radius: inherit; background: var(--primary); transition: width 180ms ease; }
.profile-tabs { display: flex; gap: 5px; margin: 0 0 18px; overflow-x: auto; border-bottom: 1px solid var(--border); scrollbar-width: thin; }
.profile-tab { flex: 0 0 auto; padding: 12px 15px; border: 0; border-bottom: 2px solid transparent; color: var(--muted-foreground); background: transparent; font-weight: 600; white-space: nowrap; }
.profile-tab:hover, .profile-tab:focus-visible { color: var(--primary); }
.profile-tab.active { color: var(--primary); border-bottom-color: var(--primary); }
.profile-tab:focus-visible { outline: 2px solid var(--primary); outline-offset: -2px; }
.profile-tab-content { min-height: 240px; }
.profile-settings { display: grid; gap: 14px; }
.profile-settings .card { margin: 0; }
@media (max-width: 640px) {
  .profile-hero .profile-card { align-items: flex-start; flex-wrap: wrap; gap: 12px; }
  .profile-hero .profile-avatar { width: 64px; height: 64px; }
  .profile-edit-button { margin-left: auto; }
  .profile-tabs { margin-left: -16px; margin-right: -16px; padding: 0 16px; }
  .profile-tab { padding: 11px 12px; }
}
@media (prefers-reduced-motion: reduce) {
  .profile-completeness-track span { transition: none; }
}
</style>
