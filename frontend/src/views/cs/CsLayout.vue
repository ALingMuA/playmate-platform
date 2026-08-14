<script setup lang="ts">
/**
 * 客服工作台布局。
 *
 * <p>菜单仅保留静态入口（会话队列）；会话详情为动态路由，
 * 由队列/已分配列表跳转进入，不放入菜单。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { csMe, type CsAccountView } from '@/api/cs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => (route.path.startsWith('/cs/conversation/') ? '/cs/queue' : route.path))

/** 当前客服账号（工作状态展示） */
const me = ref<CsAccountView | null>(null)

const workStatusMap: Record<string, string> = {
  ONLINE: '在线',
  BUSY: '忙碌',
  OFFLINE: '离线',
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
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
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">客服工作台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/cs/queue">会话队列</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-title">客服工作台</span>
          <el-tag v-if="me" size="small" :type="me.workStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
            {{ me.csName }} · {{ workStatusMap[me.workStatus] ?? me.workStatus }}
          </el-tag>
        </div>
        <div>
          <el-button text @click="router.push('/')">返回用户端</el-button>
          <el-button text type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; }
.brand { color: #fff; font-size: 16px; font-weight: 600; text-align: center; padding: 16px 0; }
.menu { border-right: none; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e4e7ed; background: #fff; }
.header-left { display: flex; align-items: center; gap: 12px; }
.header-title { font-weight: 600; }
.main { background: #f5f7fa; }
</style>
