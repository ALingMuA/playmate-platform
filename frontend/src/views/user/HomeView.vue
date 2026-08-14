<script setup lang="ts">
/**
 * 首页（FR-A08 公告查看）。
 *
 * <p>展示平台公告（仅已发布）与各端功能入口；游客可访问。</p>
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'

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
    <h1>游戏陪玩系统</h1>
    <p class="sub">用户端 / 陪玩师端 / 客服工作台 / 管理后台</p>

    <!-- 平台公告（FR-A08） -->
    <el-card v-if="announcements.length" class="announce-card" shadow="hover">
      <template #header>
        <div class="announce-head">
          <span>📢 平台公告</span>
        </div>
      </template>
      <div v-for="a in announcements" :key="a.id" class="announce-item">
        <div class="announce-title-row" @click="expanded[a.id] = !expanded[a.id]">
          <span class="announce-title">{{ a.title }}</span>
          <span class="announce-time">{{ fmtTime(a.publishedAt) }}</span>
        </div>
        <div v-if="expanded[a.id]" class="announce-content">{{ a.content }}</div>
      </div>
    </el-card>

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
