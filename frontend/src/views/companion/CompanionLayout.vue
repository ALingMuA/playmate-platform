<script setup lang="ts">
/**
 * 陪玩师端布局。
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
      { path: '/companion/application', label: '入驻申请' },
      { path: '/companion/services', label: '服务管理' },
      { path: '/companion/schedule', label: '档期管理' },
      { path: '/companion/orders', label: '接单履约' },
      { path: '/companion/earnings', label: '收益' },
    ]
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">陪玩师端</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-title">陪玩师端</span>
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
