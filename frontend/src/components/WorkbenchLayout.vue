<script setup lang="ts">
/**
 * 工作台公共布局（陪玩师端 / 客服工作台 / 管理后台共用）。
 *
 * <p>左侧深色导航 + 顶部操作栏（品牌、用户、返回用户端、退出登录）。
 * 移动端（≤768px）侧边栏折叠为抽屉，通过顶部菜单按钮展开。</p>
 */
import { computed, onMounted, onUnmounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Bell,
  ChatDotRound,
  Collection,
  Document,
  Files,
  HomeFilled,
  Menu,
  Monitor,
  Money,
  Operation,
  Service,
  Setting,
  SwitchButton,
  Tickets,
  UserFilled,
} from '@element-plus/icons-vue'
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
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? props.brand)
// 工作台首页不显示"返回上一页"按钮；首页由路由 meta.workbenchHome 标注，避免硬编码路径
const canGoBack = computed(() => !route.meta.workbenchHome)

const menuIcons: Record<string, Component> = {
  '我的陪玩主页': UserFilled,
  '服务管理': Service,
  '档期管理': Tickets,
  '接单履约': Collection,
  '收益': Money,
  '会话队列': ChatDotRound,
  '数据概览': HomeFilled,
  '审核管理': Files,
  '用户管理': UserFilled,
  '目录管理': Files,
  '评价管理': Collection,
  '投诉管理': Bell,
  '公告管理': Document,
  'AI 知识库': Monitor,
  '客服账号': UserFilled,
  '操作日志': Operation,
}

function menuIcon(label: string): Component {
  return menuIcons[label] ?? Setting
}

function goBack() {
  router.back()
}

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
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 11h4M8 9v4" />
            <path d="M15 12h.01M18 10h.01" />
            <path d="M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.013.104-.022.156V14a4 4 0 0 0 3.98 4h10.64a4 4 0 0 0 3.98-4V8.746a4 4 0 0 0-.022-.156A4 4 0 0 0 17.32 5Z" />
          </svg>
        </span>
        <span>{{ brand }}</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="menuIcon(m.label)" /></el-icon>
          <span>{{ m.label }}</span>
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
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 11h4M8 9v4" />
            <path d="M15 12h.01M18 10h.01" />
            <path d="M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.013.104-.022.156V14a4 4 0 0 0 3.98 4h10.64a4 4 0 0 0 3.98-4V8.746a4 4 0 0 0-.022-.156A4 4 0 0 0 17.32 5Z" />
          </svg>
        </span>
        <span>{{ brand }}</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu" @select="onMenuSelect">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="menuIcon(m.label)" /></el-icon>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-tooltip v-if="isMobile" content="打开导航" placement="bottom">
            <el-button text circle class="menu-btn" @click="drawerVisible = true">
              <el-icon><Menu /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip v-if="canGoBack" content="返回上一页" placement="bottom">
            <el-button text circle class="back-btn" @click="goBack">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
          </el-tooltip>
          <div class="page-context">
            <span class="header-title">{{ brand }}</span>
            <span class="page-title">{{ pageTitle }}</span>
          </div>
          <slot name="header-extra" />
        </div>
        <div class="header-right">
          <el-tag v-if="userStore.user" size="small" effect="plain" class="user-tag">
            {{ userStore.user.nickname }}
          </el-tag>
          <el-tooltip content="返回用户端" placement="bottom">
            <el-button text circle @click="router.push('/')"><el-icon><HomeFilled /></el-icon></el-button>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="bottom">
            <el-button text circle type="danger" @click="handleLogout"><el-icon><SwitchButton /></el-icon></el-button>
          </el-tooltip>
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
  background: var(--side-bg-start);
  overflow-y: auto;
}
.brand {
  color: #fff;
  min-height: 76px;
  padding: 20px 22px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--primary);
  font-size: 17px;
  flex: 0 0 auto;
}
.brand-mark svg {
  width: 19px;
  height: 19px;
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
  border-radius: 10px;
  margin-right: 12px;
}
.menu-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--side-bg-start);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  z-index: 10;
  padding: 0 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.page-context {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.header-title {
  font-weight: 600;
  white-space: nowrap;
}
.page-title {
  color: var(--text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
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
  .user-tag,
  .header-title {
    display: none;
  }
  .header {
    padding: 0 12px;
  }
}
</style>
