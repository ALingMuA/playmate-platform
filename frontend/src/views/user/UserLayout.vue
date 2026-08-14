<script setup lang="ts">
/**
 * 用户端统一布局（顶部导航）。
 *
 * <p>导航：首页 / 游戏 / 陪玩师 / 我的订单 / 在线客服；
 * 右侧按登录态展示登录按钮或用户菜单（个人中心、角色工作台入口、退出）。
 * 工作台入口按角色显示：陪玩师端（COMPANION）、客服工作台（CUSTOMER_SERVICE）、管理后台（ADMIN）。</p>
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => {
  // 订单/个人中心等子页面高亮对应菜单
  if (route.path.startsWith('/orders')) return '/orders'
  if (route.path.startsWith('/profile')) return '/profile'
  if (route.path.startsWith('/companions')) return '/companions'
  if (route.path.startsWith('/games')) return '/games'
  if (route.path.startsWith('/support')) return '/support'
  return '/'
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

/** 用户菜单命令：路径则跳转，logout 则退出 */
function onUserCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/')
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
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo" @click="router.push('/')">
          <span class="logo-icon">🎮</span>
          <span class="logo-text">游戏陪玩系统</span>
        </div>

        <nav class="nav">
          <router-link to="/" class="nav-item" :class="{ active: activeMenu === '/' }">首页</router-link>
          <router-link to="/games" class="nav-item" :class="{ active: activeMenu === '/games' }">游戏</router-link>
          <router-link to="/companions" class="nav-item" :class="{ active: activeMenu === '/companions' }">陪玩师</router-link>
          <router-link to="/orders" class="nav-item" :class="{ active: activeMenu === '/orders' }">我的订单</router-link>
          <router-link to="/support" class="nav-item" :class="{ active: activeMenu === '/support' }">在线客服</router-link>
        </nav>

        <div class="user-area">
          <!-- 未登录：登录按钮 -->
          <template v-if="!isLoggedIn">
            <el-button type="primary" size="small" @click="goLogin">登录 / 注册</el-button>
          </template>
          <!-- 已登录：用户菜单 -->
          <template v-else>
            <el-dropdown trigger="click" @command="onUserCommand">
              <div class="user-trigger">
                <el-avatar :size="28" :src="user?.avatarUrl || undefined" class="user-avatar">
                  {{ user?.nickname?.charAt(0) ?? '游' }}
                </el-avatar>
                <span class="user-name">{{ user?.nickname ?? user?.username ?? '' }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="/orders">我的订单</el-dropdown-item>
                  <el-dropdown-item v-for="w in workbenches" :key="w.path" :command="w.path" divided>
                    {{ w.label }}
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <span class="logout-item">退出登录</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </div>
      </div>
    </header>

    <!-- 页面内容（路由过渡） -->
    <main class="content">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      游戏陪玩系统 · 毕业设计演示项目
    </footer>
  </div>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
}
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-card);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 24px;
}
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.2s;
}
.logo:hover {
  opacity: 0.85;
}
.logo-icon {
  font-size: 22px;
}
.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}
.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
  scrollbar-width: none;
}
.nav::-webkit-scrollbar {
  display: none;
}
.nav-item {
  padding: 6px 14px;
  border-radius: var(--radius-small);
  font-size: 14px;
  color: var(--text-regular);
  text-decoration: none;
  white-space: nowrap;
  transition: all 0.2s;
}
.nav-item:hover {
  color: var(--brand-primary);
  background: var(--brand-primary-lighter);
}
.nav-item.active {
  color: var(--brand-primary);
  font-weight: 600;
  background: var(--brand-primary-lighter);
}
.user-area {
  flex-shrink: 0;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-small);
  transition: background 0.2s;
}
.user-trigger:hover {
  background: var(--bg-hover);
}
.user-name {
  font-size: 14px;
  color: var(--text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-avatar {
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-light));
  color: #fff;
  flex-shrink: 0;
}
.logout-item {
  color: var(--brand-danger);
}
.content {
  flex: 1;
  width: 100%;
}
.footer {
  text-align: center;
  padding: 16px;
  font-size: 12px;
  color: var(--text-placeholder);
  background: var(--bg-card);
  border-top: 1px solid var(--border-lighter);
}

/* 移动端：压缩间距，隐藏用户名（保留头像），收窄导航项 */
@media (max-width: 768px) {
  .header-inner {
    gap: 10px;
    padding: 0 12px;
  }
  .logo-text {
    display: none;
  }
  .nav-item {
    padding: 6px 10px;
    font-size: 13px;
  }
  .user-name {
    display: none;
  }
}
</style>
