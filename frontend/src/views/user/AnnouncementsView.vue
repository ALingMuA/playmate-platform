<script setup lang="ts">
/**
 * 用户端 - 平台公告列表中心（兼容包装视图）
 * 内部委托给 AnnouncementListPanel 组件，保持旧组件引用兼容性。
 */
import AnnouncementListPanel from '@/components/user/AnnouncementListPanel.vue'
</script>

<template>
  <div class="page announcements-page">
    <div class="container">
      <div class="page-header">
        <h1 class="page-title">平台公告</h1>
        <p class="page-subtitle">了解最新的系统维护、规则调整及运营动态</p>
      </div>

      <AnnouncementListPanel />
    </div>
  </div>
</template>

<style scoped>
.announcements-page {
  padding: 32px 0 64px;
}
.page-header {
  margin-bottom: 24px;
}
.page-title {
  margin: 0 0 8px;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--foreground, #0f172a);
}
.page-subtitle {
  margin: 0;
  color: var(--muted-foreground, #64748b);
  font-size: 0.9375rem;
}
</style>
