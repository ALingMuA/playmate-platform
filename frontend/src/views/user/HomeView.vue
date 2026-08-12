<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const entries = [
  { path: '/games', label: '游戏列表', desc: '浏览平台游戏' },
  { path: '/companions', label: '陪玩师列表', desc: '查找陪玩师与服务' },
  { path: '/orders', label: '我的订单', desc: '预约、支付与确认' },
  { path: '/support', label: '在线客服', desc: 'AI 客服与人工客服' },
  { path: '/companion', label: '陪玩师端', desc: '入驻、档期、接单、收益' },
  { path: '/cs', label: '客服工作台', desc: '会话队列与处理' },
  { path: '/admin', label: '管理后台', desc: '审核、用户、配置' },
]
</script>

<template>
  <div class="home">
    <h1>游戏陪玩系统</h1>
    <p class="sub">用户端 / 陪玩师端 / 客服工作台 / 管理后台 —— 按路由分区的前端骨架</p>
    <el-row :gutter="16">
      <el-col v-for="e in entries" :key="e.path" :span="6">
        <el-card class="entry" shadow="hover" @click="router.push(e.path)">
          <h3>{{ e.label }}</h3>
          <p>{{ e.desc }}</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.home {
  max-width: 960px;
  margin: 40px auto;
  padding: 0 16px;
}
.sub {
  color: #909399;
  margin-bottom: 24px;
}
.entry {
  cursor: pointer;
  margin-bottom: 16px;
}
.entry h3 {
  margin: 0 0 8px;
}
.entry p {
  color: #909399;
  font-size: 13px;
  margin: 0;
}
</style>
