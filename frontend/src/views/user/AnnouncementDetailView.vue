<script setup lang="ts">
/**
 * 用户端 - 公告详情页（/announcements/:id）
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publicAnnouncementDetail, type AnnouncementView } from '@/api/admin'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()

const announcementId = computed(() => Number(route.params.id))
const announcement = ref<AnnouncementView | null>(null)
const loading = ref(true)
const loadError = ref('')
const notFound = ref(false)

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  try {
    announcement.value = await publicAnnouncementDetail(announcementId.value)
  } catch (err) {
    announcement.value = null
    if (err instanceof ApiError && (err.status === 404 || err.code === 'ANNOUNCEMENT_NOT_FOUND')) {
      notFound.value = true
    } else {
      loadError.value = '公告加载失败，请检查网络后重试。'
    }
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/messages?tab=announcements')
}

onMounted(() => {
  load()
})
</script>

<template>
  <div class="page announcement-detail-page">
    <div class="container" v-loading="loading">
      <div class="back-bar">
        <el-button link @click="goBack">
          ← 返回公告列表
        </el-button>
      </div>

      <div v-if="!announcement && !loading" class="card empty-card" role="alert">
        <div class="empty-icon">⚠️</div>
        <div class="empty-text">{{ notFound ? '该公告不存在或已被撤回' : loadError }}</div>
        <el-button v-if="!notFound" type="primary" class="mt-4" @click="load">重试</el-button>
        <el-button type="primary" class="mt-4" @click="goBack">返回列表</el-button>
      </div>

      <div v-else-if="announcement" class="card detail-card">
        <div class="card-header">
          <div class="header-tags">
            <span class="badge badge-info">官方公告</span>
          </div>
          <h1 class="announcement-title">{{ announcement.title }}</h1>
          <div class="meta-info">
            <span>发布时间：{{ fmtTime(announcement.publishedAt) }}</span>
          </div>
        </div>

        <div class="card-body content-body">
          <div class="content-text">
            {{ announcement.content }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.announcement-detail-page {
  padding: 32px 0 64px;
}
.back-bar {
  margin-bottom: 20px;
}
.detail-card {
  padding: 36px 40px;
  background: var(--card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
}
.card-header {
  border-bottom: 1px solid var(--border);
  padding-bottom: 24px;
  margin-bottom: 28px;
}
.header-tags {
  margin-bottom: 12px;
}
.badge-info {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  font-size: 0.8125rem;
  padding: 3px 10px;
  border-radius: 4px;
  font-weight: 600;
}
.announcement-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--foreground);
  margin-bottom: 12px;
  line-height: 1.4;
}
.meta-info {
  font-size: 0.875rem;
  color: var(--muted-foreground);
}
.content-body {
  padding: 0;
}
.content-text {
  font-size: 1.0625rem;
  line-height: 1.8;
  color: var(--foreground);
  white-space: pre-wrap;
  word-break: break-word;
}
.empty-card {
  text-align: center;
  padding: 64px 0;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.empty-text {
  color: var(--muted-foreground);
  font-size: 1rem;
}
.mt-4 {
  margin-top: 16px;
}
</style>
