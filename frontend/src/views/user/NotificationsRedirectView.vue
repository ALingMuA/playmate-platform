<script setup lang="ts">
/**
 * 用户端 - 旧通知路由重定向兼容视图（NotificationsRedirectView）
 * 当已认证用户访问旧路径 /notifications 时，自动转发至 /messages?tab=notifications。
 * 配合 router.beforeEach (requiresAuth: true) 拦截未登录访客先去登录。
 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace({
    path: '/messages',
    query: { tab: 'notifications' },
  })
})
</script>

<template>
  <div class="notifications-redirect container" v-loading="true">
    <div class="redirect-hint">正在跳转至消息中心...</div>
  </div>
</template>

<style scoped>
.notifications-redirect {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.redirect-hint {
  color: var(--muted-foreground, #64748b);
  font-size: 0.9375rem;
}
</style>
