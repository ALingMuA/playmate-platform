<script setup lang="ts">
/**
 * 用户端 - 平台公告列表面板（AnnouncementListPanel）
 * 嵌入消息中心(/messages?tab=announcements)或独立复用。
 * 支持公开公告列表、发布时间展示、分页切换、详情跳转；
 * 面向游客与登录用户公开，不参与个人通知未读数统计。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'

const router = useRouter()

const list = ref<AnnouncementView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(8)
const loading = ref(false)
const failed = ref(false)
const errorMessage = ref('')

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function loadData() {
  loading.value = true
  failed.value = false
  errorMessage.value = ''
  try {
    const res = await publicAnnouncements(page.value, pageSize.value)
    list.value = res.records || []
    total.value = res.total || 0
  } catch (err: any) {
    failed.value = true
    errorMessage.value = err?.message || '加载平台公告失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

function goDetail(id: number) {
  router.push(`/announcements/${id}`)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="announcement-panel">
    <!-- 加载失败错误横幅（包含重试按钮，保留已加载数据） -->
    <div v-if="failed" class="error-banner">
      <span>{{ errorMessage }}</span>
      <el-button type="primary" size="small" @click="loadData">重试</el-button>
    </div>

    <!-- 公告列表内容 -->
    <div v-loading="loading" class="announcement-list-wrap">
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

      <!-- 独立分页栏 -->
      <div v-if="total > pageSize" class="pagination-bar">
        <el-pagination
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.announcement-panel {
  width: 100%;
}
.error-banner {
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.announcement-list-wrap {
  min-height: 240px;
}
.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.announcement-card {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
  border: 1px solid var(--border, #e2e8f0);
  background: #ffffff;
  border-radius: 8px;
}
.announcement-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  border-color: var(--primary, #3b82f6);
}
.card-body {
  padding: 20px;
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
  color: var(--foreground, #0f172a);
}
.item-time {
  font-size: 0.875rem;
  color: var(--muted-foreground, #64748b);
}
.item-content {
  color: var(--muted-foreground, #475569);
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
  color: var(--primary, #3b82f6);
  font-weight: 500;
}
.empty-box {
  text-align: center;
  padding: 64px 0;
  background: #ffffff;
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.empty-text {
  color: var(--muted-foreground, #64748b);
}
.pagination-bar {
  margin-top: 32px;
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--border, #e2e8f0);
}
</style>
