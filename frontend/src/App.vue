<script setup lang="ts">
// 根组件：路由出口 + 全局路由加载进度条
import { routeProgress } from '@/utils/progress'
</script>

<template>
  <div class="route-progress" :class="{ active: routeProgress.active }" />
  <router-view />
</template>
