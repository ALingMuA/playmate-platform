<script setup lang="ts">
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
    <el-aside width="220px" class="aside">
      <div class="brand">陪玩师端</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>陪玩师端</span>
        <div>
          <el-button text @click="router.push('/')">返回用户端</el-button>
          <el-button text type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
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
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e4e7ed; }
</style>
