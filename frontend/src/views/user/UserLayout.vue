<script setup lang="ts">
/**
 * 用户端统一布局（对齐 docs/06-原型与架构图/frontend-prototype/ 原型：site-header + site-footer）。
 *
 * <p>导航：首页 / 游戏 / 找陪玩 / 我的订单 / 客服；
 * 右侧按登录态展示登录按钮或用户菜单（个人中心、角色工作台入口、退出）。
 * 工作台入口按角色显示：陪玩师端（COMPANION）、客服工作台（CUSTOMER_SERVICE）、管理后台（ADMIN）。</p>
 */
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const unreadCount = computed(() => notificationStore.unreadCount)

const activeMenu = computed(() => {
  if (route.path.startsWith('/companions') || route.path.startsWith('/booking')) return '/companions'
  if (route.path.startsWith('/orders') || route.path.startsWith('/payment')
      || route.path.startsWith('/review') || route.path.startsWith('/complaint')) return '/orders'
  if (route.path.startsWith('/profile')) return '/profile'
  if (route.path.startsWith('/games')) return '/games'
  if (route.path.startsWith('/activities')) return '/activities'
  if (route.path.startsWith('/support')) return '/support'
  if (route.path === '/') return '/'
  return ''
})

const isLoggedIn = computed(() => userStore.isLoggedIn)
const user = computed(() => userStore.user)

/** 按角色展示的工作台入口 */
const workbenches = computed(() => {
  const items: { label: string; path: string }[] = []
  if (userStore.hasRole('COMPANION')) items.push({ label: '陪玩师端', path: '/companion' })
  if (userStore.hasRole('CUSTOMER_SERVICE')) items.push({ label: '客服工作台', path: '/cs' })
  if (userStore.hasRole('ADMIN')) items.push({ label: '管理后台', path: '/admin' })
  return items
})

async function fetchUnread() {
  if (!isLoggedIn.value) {
    notificationStore.clearUnread()
    return
  }
  await notificationStore.fetchUnread()
}

watch(
  () => route.fullPath,
  () => {
    fetchUnread()
  }
)

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    fetchUnread()
  } else {
    notificationStore.clearUnread()
  }
})

onMounted(() => {
  fetchUnread()
})

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}

function handleUserMenuCommand(cmd: string) {
  if (cmd === '__logout__') {
    handleLogout()
    return
  }
  router.push(cmd)
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}
</script>

<template>
  <div class="user-layout">
    <!-- 顶部导航（原型 site-header） -->
    <header class="site-header">
      <div class="container header-inner">
        <router-link to="/" class="brand" aria-label="返回首页">
          <span class="brand-icon" aria-hidden="true">🎮</span>
          <span>游戏陪玩</span>
        </router-link>

        <nav class="main-nav">
          <router-link to="/" :class="{ active: activeMenu === '/' }">首页</router-link>
          <router-link to="/games" :class="{ active: activeMenu === '/games' }">游戏</router-link>
          <router-link to="/companions" :class="{ active: activeMenu === '/companions' }">找陪玩</router-link>
          <router-link to="/activities" :class="{ active: activeMenu === '/activities' }">活动</router-link>
          <router-link to="/orders" :class="{ active: activeMenu === '/orders' }">我的订单</router-link>
          <router-link to="/support" :class="{ active: activeMenu === '/support' }">客服</router-link>
        </nav>

        <div class="header-actions">
          <!-- 未登录：登录按钮 -->
          <template v-if="!isLoggedIn">
            <button class="btn btn-primary btn-sm" @click="goLogin">登录 / 注册</button>
          </template>
          <!-- 已登录：通知图标与用户菜单 -->
          <template v-else>
            <div class="bell-trigger">
              <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0" class="bell-badge">
                <el-button
                  circle
                  :icon="Bell"
                  class="bell-btn"
                  aria-label="消息中心"
                  title="消息中心"
                  @click="router.push('/messages?tab=notifications')"
                />
              </el-badge>
            </div>

            <el-dropdown trigger="click" @command="handleUserMenuCommand">
              <button type="button" class="user-trigger" aria-label="打开用户菜单">
                <el-avatar :size="32" :src="user?.avatarUrl || undefined" class="user-avatar">
                  {{ user?.nickname?.charAt(0) ?? '游' }}
                </el-avatar>
                <span class="user-name">{{ user?.nickname ?? user?.username ?? '' }}</span>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="/messages?tab=notifications">
                    <span class="menu-item-with-badge">
                      <span>消息中心</span>
                      <el-badge
                        v-if="unreadCount > 0"
                        :value="unreadCount"
                        :max="99"
                        class="menu-badge"
                      />
                    </span>
                  </el-dropdown-item>
                  <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                  <el-dropdown-item command="/coupons">我的优惠券</el-dropdown-item>
                  <!-- 入驻申请入口：仅非陪玩师可见（已是陪玩师则显示下方工作台入口） -->
                  <el-dropdown-item v-if="!userStore.hasRole('COMPANION')" command="/become-companion">
                    成为陪玩师
                  </el-dropdown-item>
                  <el-dropdown-item v-for="w in workbenches" :key="w.path" :command="w.path" divided>
                    {{ w.label }}
                  </el-dropdown-item>
                  <el-dropdown-item command="__logout__" divided>
                    <span class="logout-item">退出登录</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <!-- 页面内容 -->
    <main>
      <router-view />
    </main>

    <!-- 页脚（原型 site-footer） -->
    <footer class="site-footer">
      <div class="container">
        <div class="footer-grid">
          <div class="footer-brand">
            <div class="brand">
              <span class="brand-icon">🎮</span>
              <span>游戏陪玩</span>
            </div>
            <p>找到属于你的最佳游戏搭档，畅享开黑乐趣。</p>
          </div>
          <div class="footer-col">
            <h4>快速入口</h4>
            <router-link to="/games">游戏列表</router-link>
            <router-link to="/companions">查找陪玩师</router-link>
            <router-link to="/activities">平台活动</router-link>
            <router-link to="/messages?tab=announcements">平台公告</router-link>
            <router-link to="/support">在线客服</router-link>
          </div>
          <div class="footer-col">
            <h4>个人中心</h4>
            <router-link to="/orders">我的订单</router-link>
            <router-link to="/coupons">我的优惠券</router-link>
            <router-link to="/profile">个人资料</router-link>
          </div>
          <div class="footer-col">
            <h4>工作台</h4>
            <router-link v-for="w in workbenches" :key="w.path" :to="w.path">{{ w.label }}</router-link>
            <router-link to="/login" v-if="!isLoggedIn">登录 / 注册</router-link>
          </div>
        </div>
        <div class="footer-bottom">© 2026 游戏陪玩系统 · 毕业设计演示项目</div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--background);
}
main {
  flex: 1;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.brand {
  text-decoration: none;
}
.bell-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
}
.bell-btn {
  border: 1px solid var(--border, #e2e8f0);
  background: transparent;
  color: var(--foreground, #1e293b);
  transition: all 0.2s ease;
}
.bell-btn:hover {
  background: var(--muted, #f1f5f9);
  color: var(--primary, #3b82f6);
  border-color: var(--primary, #3b82f6);
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border: none;
  background: transparent;
  font: inherit;
  border-radius: var(--radius);
  transition: background 0.15s;
}
.user-trigger:hover {
  background: var(--muted);
}
.user-name {
  font-size: 0.9375rem;
  color: var(--foreground);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-avatar {
  background: var(--primary);
  color: #fff;
  flex-shrink: 0;
}
.menu-item-with-badge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.menu-badge {
  margin-left: 8px;
}
.logout-item {
  color: var(--destructive);
}
@media (max-width: 768px) {
  .site-header {
    height: auto;
  }
  .header-inner {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    padding-top: 12px;
    padding-bottom: 8px;
  }
  .site-header .brand {
    white-space: nowrap;
  }
  .brand-icon {
    flex-shrink: 0;
  }
  .header-actions {
    grid-column: 2;
    grid-row: 1;
  }
  .user-name {
    max-width: 88px;
  }
  .main-nav {
    grid-column: 1 / -1;
    grid-row: 2;
    min-width: 0;
    overflow-x: auto;
    justify-content: space-between;
  }
  .main-nav a {
    flex-shrink: 0;
  }
}
</style>
