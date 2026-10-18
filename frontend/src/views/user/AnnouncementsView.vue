<script setup lang="ts">
/**
 * 用户端 - 公告列表中心（/announcements）
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'

const router = useRouter()
const list = ref<AnnouncementView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 8
const loading = ref(false)

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function loadData() {
  loading.value = true
  try {
    const res = await publicAnnouncements(page.value, pageSize)
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/announcements/${id}`)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page announcements-page">
    <div class="container" v-loading="loading">
      <div class="page-header">
        <h1 class="page-title">平台公告</h1>
        <p class="page-subtitle">了解最新的系统维护、规则调整及运营动态</p>
      </div>

      <div v-if="list.length === 0 && !loading" class="empty-box card">
        <div class="empty-icon">📢</div>
        <div class="empty-text">暂无已发布的平台公告</div>
      </div>

      <div v-else class="announcement-list">
        <div
          v-for="item in list"
          :key="item.id"
          class="card announcement-card"
          @click="goDetail(item.id)"
        >
          <div class="card-body">
            <div class="item-header">
              <div class="item-title">
                <span class="badge badge-info">官方</span>
                <span class="title-text">{{ item.title }}</span>
              </div>
              <div class="item-time">{{ fmtTime(item.publishedAt) }}</div>
            </div>
            <div class="item-content">
              {{ item.content }}
            </div>
            <div class="item-footer">
              <span class="read-more">阅读全文 →</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.announcements-page {
  padding: 32px 0 64px;
}
.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.announcement-card {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  border: 1px solid var(--border);
}
.announcement-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary);
}
.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.badge-info {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.title-text {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--foreground);
}
.item-time {
  font-size: 0.875rem;
  color: var(--muted-foreground);
}
.item-content {
  color: var(--muted-foreground);
  font-size: 0.9375rem;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}
.item-footer {
  display: flex;
  justify-content: flex-end;
}
.read-more {
  font-size: 0.875rem;
  color: var(--primary);
  font-weight: 500;
}
.empty-box {
  text-align: center;
  padding: 64px 0;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.empty-text {
  color: var(--muted-foreground);
}
.pagination-bar {
  margin-top: 32px;
  display: flex;
  justify-content: center;
}
</style>
