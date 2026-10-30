<script setup lang="ts">
/**
 * 用户端 - 统一消息中心（MessagesCenterView，/messages）
 * 整合站内通知与平台公告：
 * 1. URL Query 参数双向同步（tab=notifications | tab=announcements）
 * 2. 默认标签规范化：游客默认 announcements，登录用户默认 notifications，非法参数 router.replace 修正
 * 3. 标签面板惰性加载与 DOM 缓存（visitedTabs + v-show），切换不丢弃状态、不重复拉取
 * 4. 严格保持通知与公告的数据隔离及未读数语义
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { TabPaneName } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import NotificationListPanel from '@/components/user/NotificationListPanel.vue'
import AnnouncementListPanel from '@/components/user/AnnouncementListPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const activeTab = ref<'notifications' | 'announcements'>('announcements')
const visitedTabs = ref<Set<string>>(new Set())

/** 计算默认标签：登录用户优先 notifications，游客优先 announcements */
function getDefaultTab(): 'notifications' | 'announcements' {
  return userStore.isLoggedIn ? 'notifications' : 'announcements'
}

/** 同步与规范化 URL Query 中的 tab 参数 */
function syncTabFromRoute() {
  const tabQuery = route.query.tab as string | undefined
  const defaultTab = getDefaultTab()

  if (tabQuery === 'notifications' || tabQuery === 'announcements') {
    activeTab.value = tabQuery
    visitedTabs.value.add(tabQuery)
  } else {
    // 缺失或非法参数：重置并用 router.replace 规范化 URL
    activeTab.value = defaultTab
    visitedTabs.value.add(defaultTab)
    router.replace({
      path: '/messages',
      query: { ...route.query, tab: defaultTab },
    })
  }
}

/** 用户手动切换标签 */
function handleTabChange(tabName: TabPaneName) {
  const targetTab = tabName as 'notifications' | 'announcements'
  if (targetTab !== activeTab.value) {
    activeTab.value = targetTab
  }
  visitedTabs.value.add(targetTab)

  // 仅在 query 不一致时推入路由
  if (route.query.tab !== targetTab) {
    router.push({
      path: '/messages',
      query: { ...route.query, tab: targetTab },
    })
  }
}

// 监听路由 query 变化（支持浏览器后退/前进及外部直接链接跳转）
watch(
  () => route.query.tab,
  () => {
    if (route.path === '/messages') {
      syncTabFromRoute()
    }
  }
)

// 监听登录态变化
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      notificationStore.fetchUnread()
    }
    // 若当前无指定 query 或非法 query，则重新按新身份规范化
    if (route.path === '/messages') {
      const q = route.query.tab
      if (q !== 'notifications' && q !== 'announcements') {
        syncTabFromRoute()
      }
    }
  }
)

onMounted(() => {
  syncTabFromRoute()
  if (userStore.isLoggedIn) {
    notificationStore.fetchUnread()
  }
})
</script>

<template>
  <div class="messages-center-page container">
    <!-- 面包屑导航 -->
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>消息中心</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 主卡片容器 -->
    <div class="card content-card">
      <div class="card-top-bar">
        <h2 class="page-title">消息中心</h2>
      </div>

      <!-- 选项卡导航 -->
      <el-tabs
        :model-value="activeTab"
        class="messages-tabs"
        @tab-change="handleTabChange"
      >
        <el-tab-pane name="notifications">
          <template #label>
            <span class="tab-label">
              <span>站内通知</span>
              <el-badge
                v-if="userStore.isLoggedIn && notificationStore.unreadCount > 0"
                :value="notificationStore.unreadCount"
                :max="99"
                class="tab-badge"
              />
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane label="平台公告" name="announcements" />
      </el-tabs>

      <!-- 面板内容容器：使用 visitedTabs 惰性加载 + v-show 缓存，避免切换清空数据与状态 -->
      <div class="panels-container">
        <div
          v-if="visitedTabs.has('notifications')"
          v-show="activeTab === 'notifications'"
          class="panel-wrapper"
        >
          <NotificationListPanel />
        </div>

        <div
          v-if="visitedTabs.has('announcements')"
          v-show="activeTab === 'announcements'"
          class="panel-wrapper"
        >
          <AnnouncementListPanel />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.messages-center-page {
  padding: 24px 0 48px;
}
.page-header {
  margin-bottom: 16px;
}
.content-card {
  padding: 24px;
  background: var(--card, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
}
.card-top-bar {
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 600;
  color: var(--foreground, #0f172a);
}
.messages-tabs {
  margin-bottom: 20px;
}
:deep(.messages-tabs .el-tabs__item) {
  font-size: 1rem;
  font-weight: 500;
  padding: 0 20px;
}
:deep(.messages-tabs .el-tabs__header) {
  margin-bottom: 24px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-badge {
  display: inline-flex;
}
.panels-container {
  min-height: 320px;
}
.panel-wrapper {
  width: 100%;
}
</style>
