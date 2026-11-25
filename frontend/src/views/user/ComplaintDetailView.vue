<script setup lang="ts">
/**
 * 用户端 - 投诉与售后详情页（FR-U19 售后进度）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { complaintDetail, type Complaint } from '@/api/review'

const route = useRoute()
const router = useRouter()

const complaintId = computed(() => Number(route.params.id))
const complaint = ref<Complaint | null>(null)
const loading = ref(true)
const failed = ref(false)
const errorMessage = ref('')

const typeMap: Record<string, string> = {
  NO_FULFILLMENT: '未履约',
  LATE: '迟到',
  ATTITUDE: '态度问题',
  MISMATCH: '服务不符',
  OTHER: '其他',
}

const statusMap: Record<string, { label: string; badgeClass: string; desc: string }> = {
  PENDING: {
    label: '待处理',
    badgeClass: 'badge-warning',
    desc: '您的投诉已提交，平台管理员将在审核后给出处理意见，请耐心等待。',
  },
  PROCESSING: {
    label: '处理中',
    badgeClass: 'badge-info',
    desc: '平台客服或管理员正在核实订单履约证据与双方记录。',
  },
  RESOLVED: {
    label: '已处理',
    badgeClass: 'badge-success',
    desc: '平台管理员已完成仲裁并出具最终处理结果。',
  },
}

const resolutionMap: Record<string, string> = {
  KEEP: '维持原单（正常履约，不产生退款）',
  FULL_REFUND: '全额退款（订单金额全额原路退还至虚拟钱包）',
  PARTIAL_REFUND: '部分退款（协商金额退还至虚拟钱包）',
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

function fmtMoney(cents?: number): string {
  if (!cents || cents <= 0) return '¥0.00'
  return '¥' + (cents / 100).toFixed(2)
}

let loadSeq = 0

async function load() {
  const id = complaintId.value
  const seq = ++loadSeq
  complaint.value = null
  if (!id || isNaN(id)) {
    failed.value = true
    errorMessage.value = '无效的投诉编号'
    loading.value = false
    return
  }
  loading.value = true
  failed.value = false
  try {
    const data = await complaintDetail(id)
    if (seq !== loadSeq || complaintId.value !== id) return
    complaint.value = data
  } catch (err: unknown) {
    if (seq !== loadSeq) return
    failed.value = true
    errorMessage.value = err instanceof Error ? err.message : '投诉不存在或无权访问'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(load)
watch(complaintId, () => {
  void load()
})
</script>

<template>
  <div class="page complaint-detail-page">
    <div class="container" v-loading="loading">
      <!-- 面包屑/返回导航 -->
      <div class="back-bar mb-2">
        <button class="btn btn-ghost btn-sm" @click="router.push('/profile#tab=complaints')">
          ← 返回我的投诉
        </button>
      </div>

      <template v-if="complaint">
        <!-- 详情页头 -->
        <div class="page-header flex justify-between items-center mb-3">
          <div>
            <h1 class="page-title">投诉进度 #{{ complaint.id }}</h1>
            <p class="page-subtitle">提交时间：{{ fmtTime(complaint.createdAt) }}</p>
          </div>
          <span
            class="badge badge-lg"
            :class="statusMap[complaint.complaintStatus]?.badgeClass || 'badge-outline'"
          >
            {{ statusMap[complaint.complaintStatus]?.label || complaint.complaintStatus }}
          </span>
        </div>

        <!-- 进度提示横幅 -->
        <el-alert
          :title="statusMap[complaint.complaintStatus]?.desc || '投诉处理中'"
          :type="complaint.complaintStatus === 'RESOLVED' ? 'success' : 'info'"
          :closable="false"
          show-icon
          class="mb-3"
        />

        <!-- 仲裁结果卡片（已处理时突出展示） -->
        <div v-if="complaint.complaintStatus === 'RESOLVED'" class="card mb-3 border-success">
          <div class="card-header bg-success-light">
            <h3 class="card-title">⚖️ 平台仲裁处理结果</h3>
          </div>
          <div class="card-body">
            <div class="grid grid-2 gap-2 mb-2">
              <div>
                <span class="text-muted">处理结论：</span>
                <strong>{{ resolutionMap[complaint.resolutionType] || complaint.resolutionType || '已处理' }}</strong>
              </div>
              <div>
                <span class="text-muted">退款金额：</span>
                <strong class="text-destructive font-lg">
                  {{ complaint.refundAmountCents > 0 ? fmtMoney(complaint.refundAmountCents) : '未产生退款' }}
                </strong>
              </div>
              <div>
                <span class="text-muted">处理时间：</span>
                <span>{{ fmtTime(complaint.handledAt) }}</span>
              </div>
            </div>
            <div class="opinion-box mt-2">
              <div class="text-muted mb-1">管理员处理意见：</div>
              <div class="opinion-text">{{ complaint.handlingOpinion || '无详细说明' }}</div>
            </div>
          </div>
        </div>

        <!-- 关联订单卡片 -->
        <div class="card mb-3">
          <div class="card-header flex justify-between items-center">
            <h3 class="card-title">📦 关联订单信息</h3>
            <button class="btn btn-outline btn-sm" @click="router.push(`/orders/${complaint.orderId}`)">
              查看原订单详情 →
            </button>
          </div>
          <div class="card-body">
            <div class="grid grid-2 gap-2">
              <div>
                <span class="text-muted">订单编号：</span>
                <router-link :to="`/orders/${complaint.orderId}`" class="link-order">
                  {{ complaint.orderNo }}
                </router-link>
              </div>
              <div>
                <span class="text-muted">服务项目：</span>
                <span>{{ complaint.serviceTitleSnapshot }}</span>
              </div>
              <div>
                <span class="text-muted">订单金额：</span>
                <span>{{ fmtMoney(complaint.orderTotalAmountCents) }}</span>
              </div>
              <div>
                <span class="text-muted">陪玩师：</span>
                <span>{{ complaint.companionNickname || '-' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 投诉原由与证据卡片 -->
        <div class="card mb-3">
          <div class="card-header">
            <h3 class="card-title">📝 投诉说明与证据</h3>
          </div>
          <div class="card-body">
            <div class="mb-3">
              <span class="text-muted">投诉类型：</span>
              <el-tag size="small">{{ typeMap[complaint.complaintType] || complaint.complaintType }}</el-tag>
            </div>
            <div class="mb-3">
              <div class="text-muted mb-1">问题描述：</div>
              <div class="desc-content">{{ complaint.description }}</div>
            </div>
            <div>
              <div class="text-muted mb-2">上传证据：</div>
              <div v-if="complaint.evidences && complaint.evidences.length" class="evidence-grid">
                <div v-for="ev in complaint.evidences" :key="ev.id" class="evidence-box">
                  <el-image
                    :src="ev.fileUrl"
                    fit="cover"
                    class="evidence-image"
                    :preview-src-list="complaint.evidences.map((e) => e.fileUrl)"
                    preview-teleported
                  />
                  <div class="evidence-name" :title="ev.fileName">{{ ev.fileName || '证据图片' }}</div>
                </div>
              </div>
              <div v-else class="text-muted fst-italic">
                未上传图片证据
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 错误/空态 -->
      <div v-else-if="failed" class="empty-state">
        <div class="empty-icon">⚠️</div>
        <h3>{{ errorMessage }}</h3>
        <p class="text-muted mt-1">请核对投诉编号或确认您是否为该投诉发起人。</p>
        <button class="btn btn-primary mt-3" @click="router.push('/profile#tab=complaints')">
          返回我的投诉
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.complaint-detail-page {
  padding: 24px 0 60px;
}
.back-bar {
  display: flex;
}
.badge-lg {
  font-size: 0.95rem;
  padding: 4px 14px;
  border-radius: 6px;
}
.badge-warning {
  background: #fef3c7;
  color: #92400e;
}
.badge-info {
  background: #e0f2fe;
  color: #0369a1;
}
.badge-success {
  background: #dcfce7;
  color: #15803d;
}
.border-success {
  border-color: #86efac;
}
.bg-success-light {
  background: #f0fdf4;
  border-bottom: 1px solid #bbf7d0;
}
.font-lg {
  font-size: 1.15rem;
}
.text-destructive {
  color: #ef4444;
}
.text-muted {
  color: var(--muted-foreground, #6b7280);
}
.opinion-box {
  background: #f8fafc;
  border-left: 4px solid #10b981;
  padding: 10px 14px;
  border-radius: 4px;
}
.opinion-text {
  color: #1e293b;
  line-height: 1.6;
  white-space: pre-wrap;
}
.desc-content {
  background: var(--muted, #f8fafc);
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 6px;
  padding: 12px;
  color: var(--foreground, #1e293b);
  line-height: 1.6;
  white-space: pre-wrap;
}
.evidence-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.evidence-box {
  width: 100px;
  text-align: center;
}
.evidence-image {
  width: 100px;
  height: 100px;
  border-radius: 6px;
  border: 1px solid var(--border, #e2e8f0);
  cursor: pointer;
}
.evidence-name {
  font-size: 0.75rem;
  color: var(--muted-foreground, #6b7280);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-order {
  color: var(--primary, #3b82f6);
  font-weight: 500;
  text-decoration: underline;
}
.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.empty-state {
  text-align: center;
  padding: 60px 0;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
</style>
