<script setup lang="ts">
/**
 * 用户端 - 站内通知中心（兼容包装视图，FR-A07）
 * 内部委托给 NotificationListPanel 组件，保持旧组件引用兼容性。
 */
import NotificationListPanel from '@/components/user/NotificationListPanel.vue'
</script>

<template>
  <div class="notifications-page container">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/messages', query: { tab: 'notifications' } }">消息中心</el-breadcrumb-item>
        <el-breadcrumb-item>站内通知</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="card content-card">
      <NotificationListPanel />
    </div>
  </div>
</template>

<style scoped>
.notifications-page {
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
</style>
