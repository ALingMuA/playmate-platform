<script setup lang="ts">
/**
 * 陪玩师端布局（基于 WorkbenchLayout 公共工作台布局）。
 */
import WorkbenchLayout from '@/components/WorkbenchLayout.vue'

const menus = [
  { path: '/companion/application', label: '入驻申请' },
  { path: '/companion/services', label: '服务管理' },
  { path: '/companion/schedule', label: '档期管理' },
  { path: '/companion/orders', label: '接单履约' },
  { path: '/companion/earnings', label: '收益' },
]
</script>

<template>
  <WorkbenchLayout brand="陪玩师端" :menus="menus" />
</template>
