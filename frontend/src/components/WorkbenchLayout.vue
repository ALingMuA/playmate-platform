<script setup lang="ts">
/**
 * 工作台公共布局（陪玩师端 / 客服工作台 / 管理后台共用）。
 *
 * <p>左侧深色导航 + 顶部操作栏（品牌、用户、返回用户端、退出登录）。
 * 移动端（≤768px）侧边栏折叠为抽屉，通过顶部菜单按钮展开。</p>
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  /** 品牌名（侧边栏顶部与顶栏标题） */
  brand: string
  /** 侧边栏菜单 */
  menus: { path: string; label: string }[]
  /** 高亮路径换算（默认取当前路径；会话详情等动态路由可映射回列表项） */
  activePath?: (path: string) => string
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => (props.activePath ? props.activePath(route.path) : route.path))

// ==================== 移动端抽屉 ====================

const isMobile = ref(false)
const drawerVisible = ref(false)

function onResize() {
  isMobile.value = window.innerWidth <= 768
  if (!isMobile.value) drawerVisible.value = false
}

function onMenuSelect() {
  drawerVisible.value = false
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => window.removeEventListener('resize', onResize))

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" width="208px" class="aside">
      <div class="brand">{{ brand }}</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 移动端抽屉菜单 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      size="220px"
      :with-header="false"
      class="menu-drawer"
    >
      <div class="brand">{{ brand }}</div>
      <el-menu :default-active="activeMenu" router class="menu" @select="onMenuSelect">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button v-if="isMobile" text class="menu-btn" @click="drawerVisible = true">☰</el-button>
          <span class="header-title">{{ brand }}</span>
          <slot name="header-extra" />
        </div>
        <div class="header-right">
          <el-tag v-if="userStore.user" size="small" effect="plain" class="user-tag">
            {{ userStore.user.nickname }}
          </el-tag>
          <el-button text @click="router.push('/')">返回用户端</el-button>
          <el-button text type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: linear-gradient(180deg, var(--side-bg-start), var(--side-bg-end));
  overflow-y: auto;
}
.brand {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  padding: 16px 0;
  letter-spacing: 1px;
}
.menu {
  border-right: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.75);
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.08);
  --el-menu-active-color: #fff;
}
.menu :deep(.el-menu-item.is-active) {
  background: var(--brand-primary);
  border-radius: 0 var(--radius-small) var(--radius-small) 0;
  margin-right: 8px;
}
.menu-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: linear-gradient(180deg, var(--side-bg-start), var(--side-bg-end));
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  z-index: 10;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.header-title {
  font-weight: 600;
  white-space: nowrap;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.user-tag {
  margin-right: 4px;
}
.menu-btn {
  font-size: 18px;
  padding: 4px 8px;
}
.main {
  background: var(--bg-page);
  overflow-y: auto;
}
@media (max-width: 768px) {
  .main {
    padding: 12px;
  }
  .user-tag {
    display: none;
  }
}
</style>
