<script setup lang="ts">
/**
 * 用户端 - 站内通知中心（FR-A07）。
 * 支持通知列表分页、未读/已读筛选、单条已读、一键全部已读、根据关联对象安全路由跳转。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  myNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  type NotificationView,
} from '@/api/notification'

const router = useRouter()

const loading = ref(false)
const markingAll = ref(false)
const failed = ref(false)
const errorMessage = ref('')

const filterTab = ref<'ALL' | 'UNREAD' | 'READ'>('ALL')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const rawNotifications = ref<NotificationView[]>([])

// 统计当前列表中的未读数（用于顶部标头和按钮控制）
const localUnreadCount = computed(() => {
  return rawNotifications.value.filter((n) => n.readStatus === 0).length
})

// 根据当前选中的 Tab 过滤显示
const displayedNotifications = computed(() => {
  if (filterTab.value === 'UNREAD') {
    return rawNotifications.value.filter((n) => n.readStatus === 0)
  }
  if (filterTab.value === 'READ') {
    return rawNotifications.value.filter((n) => n.readStatus === 1)
  }
  return rawNotifications.value
})

const typeTagConfig: Record<string, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info' }> = {
  ORDER_STATUS: { label: '订单提醒', type: 'primary' },
  COMPLAINT_RESULT: { label: '售后仲裁', type: 'warning' },
  AUDIT_RESULT: { label: '入驻审核', type: 'info' },
  SERVICE_AUDIT: { label: '服务审核', type: 'info' },
  SYSTEM: { label: '系统通知', type: 'danger' },
}

function getTypeTag(type: string) {
  return typeTagConfig[type] || { label: '站内提醒', type: 'info' }
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function loadData() {
  loading.value = true
  failed.value = false
  errorMessage.value = ''
  try {
    const res = await myNotifications(page.value, pageSize.value)
    rawNotifications.value = res.records || []
    total.value = res.total || 0
  } catch (err: any) {
    failed.value = true
    errorMessage.value = err?.message || '加载通知失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function handleMarkRead(item: NotificationView) {
  if (item.readStatus === 1) return
  try {
    await markNotificationRead(item.id)
    item.readStatus = 1
    item.readAt = new Date().toISOString()
  } catch (err: any) {
    ElMessage.error(err?.message || '标记已读失败')
  }
}

async function handleMarkAllRead() {
  if (markingAll.value || localUnreadCount.value === 0) return
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    rawNotifications.value.forEach((n) => {
      n.readStatus = 1
      if (!n.readAt) n.readAt = new Date().toISOString()
    })
    ElMessage.success('全部通知已标记为已读')
  } catch (err: any) {
    ElMessage.error(err?.message || '全部标记已读失败')
  } finally {
    markingAll.value = false
  }
}

/** 白名单路由导航映射 */
function getTargetRoute(item: NotificationView): string | null {
  if (!item.relatedType || !item.relatedId) {
    if (item.relatedType === 'USER') return '/profile'
    return null
  }
  switch (item.relatedType) {
    case 'ORDER':
      return `/orders/${item.relatedId}`
    case 'COMPLAINT':
      return `/complaints/${item.relatedId}`
    case 'APPLICATION':
      return '/become-companion'
    case 'SERVICE':
      return '/companion'
    case 'ANNOUNCEMENT':
      return `/announcements/${item.relatedId}`
    case 'ACTIVITY':
      return `/activities/${item.relatedId}`
    case 'USER':
      return '/profile'
    default:
      return null
  }
}

async function handleItemClick(item: NotificationView) {
  if (item.readStatus === 0) {
    await handleMarkRead(item)
  }
  const target = getTargetRoute(item)
  if (target) {
    router.push(target)
  }
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="notifications-page container">
    <!-- 面包屑与返回 -->
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>站内通知</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 主卡片 -->
    <div class="card content-card">
      <div class="card-header">
        <div class="header-left">
          <h2 class="page-title">
            <span>站内通知</span>
            <el-badge
              v-if="localUnreadCount > 0"
              :value="localUnreadCount"
              :max="99"
              class="unread-badge"
            />
          </h2>
          <div class="tabs-group">
            <el-radio-group v-model="filterTab" size="default">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="UNREAD">未读</el-radio-button>
              <el-radio-button label="READ">已读</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <div class="header-actions">
          <el-button
            type="primary"
            plain
            :loading="markingAll"
            :disabled="localUnreadCount === 0"
            @click="handleMarkAllRead"
          >
            一键全部已读
          </el-button>
        </div>
      </div>

      <!-- 加载失败 -->
      <div v-if="failed" class="error-banner">
        <span>{{ errorMessage }}</span>
        <el-button type="primary" size="small" @click="loadData">重试</el-button>
      </div>

      <!-- 列表内容 -->
      <div v-loading="loading" class="notification-list-wrap">
        <div v-if="displayedNotifications.length === 0 && !loading" class="empty-wrap">
          <el-empty
            :description="filterTab === 'UNREAD' ? '暂无未读通知' : '暂无站内通知'"
          />
        </div>

        <div v-else class="notification-list">
          <div
            v-for="item in displayedNotifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: item.readStatus === 0, clickable: !!getTargetRoute(item) }"
            @click="handleItemClick(item)"
          >
            <div class="item-main">
              <div class="item-title-row">
                <div class="item-title-left">
                  <span v-if="item.readStatus === 0" class="dot-unread" title="未读"></span>
                  <el-tag
                    :type="getTypeTag(item.notificationType).type"
                    size="small"
                    effect="light"
                    class="type-tag"
                  >
                    {{ getTypeTag(item.notificationType).label }}
                  </el-tag>
                  <span class="item-title">{{ item.title }}</span>
                </div>
                <span class="item-time">{{ fmtTime(item.createdAt) }}</span>
              </div>
              <p class="item-content">{{ item.content }}</p>
              <div class="item-footer">
                <span v-if="getTargetRoute(item)" class="link-hint">
                  查看详情 &gt;
                </span>
                <span v-else class="read-status-text">
                  {{ item.readStatus === 1 ? '已读' : '点击标为已读' }}
                </span>
                <el-button
                  v-if="item.readStatus === 0"
                  size="small"
                  text
                  type="primary"
                  class="btn-mark-single"
                  @click.stop="handleMarkRead(item)"
                >
                  标为已读
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
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
  </div>
</template>

<style scoped>
.notifications-page {
  padding: 24px 0 48px;
}
.page-header {
  margin-bottom: 16px;
}
.content-card {
  padding: 24px;
  background: var(--card, #ffffff);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 8px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border, #e2e8f0);
  margin-bottom: 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}
.page-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--foreground, #0f172a);
  display: flex;
  align-items: center;
  gap: 8px;
}
.unread-badge {
  margin-left: 4px;
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
.notification-list-wrap {
  min-height: 240px;
}
.empty-wrap {
  padding: 40px 0;
}
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notification-item {
  display: flex;
  padding: 16px 20px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid var(--border, #e2e8f0);
  transition: all 0.2s ease;
  cursor: pointer;
}
.notification-item:hover {
  border-color: var(--primary, #3b82f6);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.notification-item.unread {
  background: #f8fafc;
  border-left: 4px solid var(--primary, #3b82f6);
}
.item-main {
  width: 100%;
}
.item-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.item-title-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot-unread {
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  display: inline-block;
}
.type-tag {
  font-weight: 500;
}
.item-title {
  font-weight: 600;
  font-size: 1rem;
  color: var(--foreground, #0f172a);
}
.item-time {
  font-size: 0.8125rem;
  color: var(--muted-foreground, #64748b);
}
.item-content {
  margin: 0 0 12px;
  color: var(--muted-foreground, #475569);
  font-size: 0.9375rem;
  line-height: 1.6;
  white-space: pre-wrap;
}
.item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed var(--border, #f1f5f9);
  padding-top: 8px;
}
.link-hint {
  font-size: 0.8125rem;
  color: var(--primary, #3b82f6);
  font-weight: 500;
}
.read-status-text {
  font-size: 0.8125rem;
  color: var(--muted-foreground, #94a3b8);
}
.btn-mark-single {
  font-size: 0.8125rem;
  padding: 0 4px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border, #e2e8f0);
}
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  .header-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .item-title-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
