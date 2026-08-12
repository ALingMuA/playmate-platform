<script setup lang="ts">
/**
 * 骨架占位页：后续迭代填充真实页面内容
 */
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <el-empty :description="title + '（骨架占位页，功能开发中）'" />
  <p v-if="description" class="placeholder-desc">{{ description }}</p>
</template>

<style scoped>
.placeholder-desc {
  color: #909399;
  font-size: 13px;
  text-align: center;
  margin-top: 4px;
}
</style>
