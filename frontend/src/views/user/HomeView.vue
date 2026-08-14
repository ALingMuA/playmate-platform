<script setup lang="ts">
/**
 * 首页（FR-A08 公告查看）。
 *
 * <p>Hero 欢迎区 + 平台公告（仅已发布）+ 功能入口；游客可访问。</p>
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const user = computed(() => userStore.user)

const entries = [
  { path: '/games', label: '游戏列表', desc: '浏览平台游戏，挑选心仪项目', icon: '🎮' },
  { path: '/companions', label: '查找陪玩师', desc: '按游戏筛选服务与档期', icon: '🤝' },
  { path: '/orders', label: '我的订单', desc: '预约、支付、评价与投诉', icon: '📋' },
  { path: '/support', label: '在线客服', desc: 'AI 客服即时答疑，可转人工', icon: '💬' },
]

/** 平台公告（仅已发布，FR-A08） */
const announcements = ref<AnnouncementView[]>([])
/** 公告展开查看详情 */
const expanded = ref<Record<number, boolean>>({})

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

onMounted(async () => {
  try {
    const result = await publicAnnouncements(1, 5)
    announcements.value = result.records
  } catch {
    announcements.value = []
  }
})
</script>

<template>
  <div class="home">
    <!-- Hero 欢迎区 -->
    <div class="hero">
      <h1 class="hero-title">游戏陪玩系统</h1>
      <p class="hero-sub">
        <template v-if="userStore.isLoggedIn">欢迎回来，{{ user?.nickname ?? user?.username }}！</template>
        <template v-else>找陪玩、约大神、随时开黑 —— 注册登录即可预约服务</template>
      </p>
      <div class="hero-actions">
        <el-button v-if="!userStore.isLoggedIn" type="primary" size="large" @click="router.push('/login')">
          立即登录
        </el-button>
        <el-button type="success" size="large" @click="router.push('/games')">浏览游戏</el-button>
      </div>
    </div>

    <!-- 平台公告（FR-A08） -->
    <el-card v-if="announcements.length" class="announce-card" shadow="hover">
      <template #header>
        <div class="announce-head">📢 平台公告</div>
      </template>
      <div v-for="a in announcements" :key="a.id" class="announce-item">
        <div class="announce-title-row" @click="expanded[a.id] = !expanded[a.id]">
          <span class="announce-title">{{ a.title }}</span>
          <span class="announce-time">{{ fmtTime(a.publishedAt) }}</span>
        </div>
        <div v-if="expanded[a.id]" class="announce-content">{{ a.content }}</div>
      </div>
    </el-card>

    <!-- 功能入口 -->
    <el-row :gutter="16">
      <el-col v-for="e in entries" :key="e.path" :xs="24" :sm="12" :md="6">
        <el-card class="entry" shadow="hover" @click="router.push(e.path)">
          <div class="entry-icon">{{ e.icon }}</div>
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
  margin: 0 auto;
  padding: 32px 16px 48px;
}
.hero {
  text-align: center;
  padding: 40px 16px 32px;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a5a80 100%);
  border-radius: 12px;
  margin-bottom: 24px;
}
.hero-title {
  color: #fff;
  font-size: 32px;
  margin: 0 0 12px;
}
.hero-sub {
  color: #c0ccda;
  font-size: 15px;
  margin: 0 0 24px;
}
.hero-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
.announce-card {
  margin-bottom: 24px;
}
.announce-head {
  font-weight: 600;
}
.announce-item {
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}
.announce-item:last-child {
  border-bottom: none;
}
.announce-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  gap: 12px;
}
.announce-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.announce-title:hover {
  color: #409eff;
}
.announce-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.announce-content {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  background: #f7f8fa;
  padding: 8px 10px;
  border-radius: 6px;
}
.entry {
  cursor: pointer;
  margin-bottom: 16px;
  border-radius: 8px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.entry:hover {
  transform: translateY(-2px);
}
.entry-icon {
  font-size: 28px;
  margin-bottom: 8px;
}
.entry h3 {
  margin: 0 0 6px;
}
.entry p {
  color: #909399;
  font-size: 13px;
  margin: 0;
}
</style>
