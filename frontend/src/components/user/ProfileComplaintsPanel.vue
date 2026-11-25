<script setup lang="ts">
/**
 * 个人中心 - 我的投诉面板（FR-U19 售后进度）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myComplaints, type Complaint } from '@/api/review'

const router = useRouter()

const activeTab = ref('ALL')
const list = ref<Complaint[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const failed = ref(false)

const tabs = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已处理' },
]

const statusMap: Record<string, { label: string; badgeClass: string }> = {
  PENDING: { label: '待处理', badgeClass: 'badge-warning' },
  PROCESSING: { label: '处理中', badgeClass: 'badge-info' },
  RESOLVED: { label: '已处理', badgeClass: 'badge-success' },
}

const typeMap: Record<string, string> = {
  NO_FULFILLMENT: '未履约',
  LATE: '迟到',
  ATTITUDE: '态度问题',
  MISMATCH: '服务不符',
  OTHER: '其他',
}

const resolutionMap: Record<string, string> = {
  KEEP: '维持原单',
  FULL_REFUND: '全额退款',
  PARTIAL_REFUND: '部分退款',
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

function fmtMoney(cents?: number): string {
  if (!cents || cents <= 0) return '¥0.00'
  return '¥' + (cents / 100).toFixed(2)
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const status = activeTab.value === 'ALL' ? undefined : activeTab.value
    const result = await myComplaints(page.value, pageSize, status)
    list.value = result.records || []
    total.value = result.total
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function onTabChange(val: string) {
  activeTab.value = val
  page.value = 1
  load()
}

function goDetail(item: Complaint) {
  router.push(`/complaints/${item.id}`)
}

function goOrder(item: Complaint) {
  router.push(`/orders/${item.orderId}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="complaints-panel">
    <div class="tabs mb-2">
      <button
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="onTabChange(t.value)"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="failed" class="empty-state" role="alert">
      <p>投诉记录加载失败。</p>
      <button class="btn btn-outline mt-1" @click="load">重试</button>
    </div>

    <div v-else-if="list.length" class="complaint-list">
      <div
        v-for="row in list"
        :key="row.id"
        class="complaint-card"
        @click="goDetail(row)"
      >
        <div class="card-header">
          <div class="header-left">
            <span class="complaint-id">投诉编号 #{{ row.id }}</span>
            <span class="order-no">订单号：{{ row.orderNo }}</span>
          </div>
          <span class="badge" :class="statusMap[row.complaintStatus]?.badgeClass || 'badge-outline'">
            {{ statusMap[row.complaintStatus]?.label || row.complaintStatus }}
          </span>
        </div>

        <div class="card-content">
          <div class="service-title">{{ row.serviceTitleSnapshot }}</div>
          <div class="info-row">
            <span class="meta-label">投诉类型：</span>
            <span class="meta-value">{{ typeMap[row.complaintType] || row.complaintType }}</span>
            <span class="meta-divider">·</span>
            <span class="meta-label">提交时间：</span>
            <span class="meta-value">{{ fmtTime(row.createdAt) }}</span>
          </div>
          <div class="description-preview">
            {{ row.description }}
          </div>

          <!-- 仲裁结果卡片 -->
          <div v-if="row.complaintStatus === 'RESOLVED'" class="resolution-box">
            <div class="res-title">仲裁结果：{{ resolutionMap[row.resolutionType] || '已处理' }}</div>
            <div class="res-detail">
              <span>退款金额：<strong class="text-danger">{{ row.refundAmountCents > 0 ? fmtMoney(row.refundAmountCents) : '未产生退款' }}</strong></span>
              <span v-if="row.handlingOpinion" class="opinion">处理意见：{{ row.handlingOpinion }}</span>
            </div>
          </div>
        </div>

        <div class="card-footer" @click.stop>
          <button class="btn btn-primary btn-sm" @click="goDetail(row)">查看售后进度</button>
          <button class="btn btn-ghost btn-sm" @click="goOrder(row)">查看原订单</button>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">🛡️</div>
      <p>暂无投诉售后记录</p>
      <button class="btn btn-outline mt-2" @click="router.push('/orders')">查看我的订单</button>
    </div>
  </div>
</template>

<style scoped>
.complaints-panel {
  width: 100%;
}
.tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border, #e5e7eb);
  padding-bottom: 8px;
}
.tab {
  background: none;
  border: none;
  padding: 6px 14px;
  border-radius: var(--radius, 6px);
  cursor: pointer;
  font-size: 0.875rem;
  color: var(--muted-foreground, #6b7280);
}
.tab.active {
  background: var(--primary, #3b82f6);
  color: #fff;
  font-weight: 500;
}
.complaint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}
.complaint-card {
  background: var(--card, #fff);
  border: 1px solid var(--border, #e5e7eb);
  border-radius: var(--radius, 8px);
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.complaint-card:hover {
  border-color: var(--primary, #3b82f6);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.complaint-id {
  font-weight: 600;
  font-size: 0.95rem;
}
.order-no {
  font-size: 0.85rem;
  color: var(--muted-foreground, #6b7280);
}
.service-title {
  font-weight: 500;
  font-size: 1rem;
  margin-bottom: 6px;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: var(--muted-foreground, #6b7280);
  margin-bottom: 8px;
}
.meta-divider {
  color: var(--border, #d1d5db);
}
.description-preview {
  font-size: 0.875rem;
  color: var(--foreground, #374151);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}
.resolution-box {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.res-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #166534;
  margin-bottom: 4px;
}
.res-detail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.85rem;
  color: #15803d;
}
.text-danger {
  color: #dc2626;
}
.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--border, #f3f4f6);
  padding-top: 10px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--muted-foreground, #6b7280);
}
.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 8px;
}
.badge-warning {
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
}
.badge-info {
  background: #e0f2fe;
  color: #0369a1;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
}
.badge-success {
  background: #dcfce7;
  color: #15803d;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
}
.badge-outline {
  border: 1px solid #d1d5db;
  color: #6b7280;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
}
</style>
