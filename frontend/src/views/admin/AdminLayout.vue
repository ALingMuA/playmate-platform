<script setup lang="ts">
/**
 * 管理后台布局（基于 WorkbenchLayout 公共工作台布局）。
 */
import WorkbenchLayout from '@/components/WorkbenchLayout.vue'

const menus = [
  { path: '/admin/dashboard', label: '数据概览' },
  { path: '/admin/audit', label: '审核管理' },
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/reviews', label: '评价管理' },
  { path: '/admin/complaints', label: '投诉管理' },
  { path: '/admin/announcements', label: '公告管理' },
  { path: '/admin/ai-knowledge', label: 'AI 知识库' },
  { path: '/admin/cs-accounts', label: '客服账号' },
  { path: '/admin/operation-logs', label: '操作日志' },
]
</script>

<template>
  <WorkbenchLayout brand="管理后台" :menus="menus" />
</template>
