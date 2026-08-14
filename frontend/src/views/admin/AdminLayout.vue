<script setup lang="ts">
/**
 * 管理后台布局。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

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
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">管理后台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-title">管理后台</span>
          <el-tag size="small" effect="plain" v-if="userStore.user">
            {{ userStore.user.nickname }}
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
