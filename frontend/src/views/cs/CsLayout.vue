<script setup lang="ts">
/**
 * 客服工作台布局（基于 WorkbenchLayout 公共工作台布局）。
 *
 * <p>菜单仅保留静态入口（会话队列）；会话详情为动态路由，
 * 由队列/已分配列表跳转进入，高亮映射回"会话队列"。
 * 顶栏展示当前客服账号与工作状态。</p>
 */
import { onMounted, ref } from 'vue'
import WorkbenchLayout from '@/components/WorkbenchLayout.vue'
import { csMe, type CsAccountView } from '@/api/cs'

/** 当前客服账号（工作状态展示） */
const me = ref<CsAccountView | null>(null)

const menus = [{ path: '/cs/queue', label: '会话队列' }]

const workStatusMap: Record<string, string> = {
  ONLINE: '在线',
  BUSY: '忙碌',
  OFFLINE: '离线',
}

/** 动态路由（会话详情）高亮回队列菜单 */
function activePath(path: string): string {
  return path.startsWith('/cs/conversation/') ? '/cs/queue' : path
}

onMounted(async () => {
  try {
    me.value = await csMe()
  } catch {
    // 未上线或强制改密等场景静默处理，由页面自行引导
  }
})
</script>

<template>
  <WorkbenchLayout brand="客服工作台" :menus="menus" :active-path="activePath">
    <template #header-extra>
      <el-tag v-if="me" size="small" :type="me.workStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
        {{ me.csName }} · {{ workStatusMap[me.workStatus] ?? me.workStatus }}
      </el-tag>
    </template>
  </WorkbenchLayout>
</template>
