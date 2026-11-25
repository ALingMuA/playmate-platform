<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type TagProps } from 'element-plus'
import {
  acceptOrder,
  companionOrders,
  endOrderService,
  orderDetail,
  rejectOrder,
  startOrderService,
  type Order,
} from '@/api/order'
import { loadPrivateImage, uploadFile } from '@/api/file'

const activeTab = ref('WAITING_ACCEPTANCE')
const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const loadError = ref('')
const pendingActionIds = ref(new Set<number>())

const detailVisible = ref(false)
const detail = ref<Order | null>(null)
const endDialogVisible = ref(false)
const endingOrder = ref<Order | null>(null)
const ending = ref(false)
const uploadingEvidence = ref(false)
const deliveryEvidences = ref<{ fileUrl: string; fileName: string; previewUrl: string }[]>([])
const detailEvidenceImages = ref<Record<string, string>>({})
const detailEvidenceErrors = ref<Record<string, boolean>>({})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const status = activeTab.value === 'HISTORY' ? 'COMPLETED,CLOSED' : activeTab.value
    const result = await companionOrders(status, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : '订单列表加载失败，请重试。'
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  load()
}

onMounted(load)
onUnmounted(() => {
  deliveryEvidences.value.forEach((evidence) => URL.revokeObjectURL(evidence.previewUrl))
  deliveryEvidences.value = []
})

async function handleAccept(row: Order) {
  if (pendingActionIds.value.has(row.id)) return
  pendingActionIds.value = new Set(pendingActionIds.value).add(row.id)
  try {
    try { await ElMessageBox.confirm(`确认接受订单「${row.serviceTitleSnapshot}」？`, '接单确认', { type: 'info' }) } catch { return }
    await acceptOrder(row.id)
    ElMessage.success('接单成功，等待服务开始')
    await load()
  } catch {
    // http.ts 已统一提示错误
  } finally {
    const next = new Set(pendingActionIds.value); next.delete(row.id); pendingActionIds.value = next
  }
}

async function handleReject(row: Order) {
  if (pendingActionIds.value.has(row.id)) return
  pendingActionIds.value = new Set(pendingActionIds.value).add(row.id)
  try {
    let value: string
    try {
      ({ value } = await ElMessageBox.prompt('请填写拒绝原因', '拒绝订单', {
        inputPlaceholder: '如：档期冲突、临时有事',
        inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '拒绝原因不能为空'),
      }))
    } catch { return }
    await rejectOrder(row.id, value)
    ElMessage.success('已拒绝订单，款项已退还用户')
    await load()
  } catch {
    // http.ts 已统一提示错误
  } finally {
    const next = new Set(pendingActionIds.value); next.delete(row.id); pendingActionIds.value = next
  }
}

async function handleStart(row: Order) {
  if (pendingActionIds.value.has(row.id)) return
  pendingActionIds.value = new Set(pendingActionIds.value).add(row.id)
  try {
    try { await ElMessageBox.confirm('确认开始服务？', '开始服务', { type: 'info' }) } catch { return }
    await startOrderService(row.id)
    ElMessage.success('服务已开始')
    await load()
  } catch {
    // http.ts 已统一提示错误
  } finally {
    const next = new Set(pendingActionIds.value); next.delete(row.id); pendingActionIds.value = next
  }
}

function openEndDialog(row: Order) {
  deliveryEvidences.value.forEach((evidence) => URL.revokeObjectURL(evidence.previewUrl))
  endingOrder.value = row
  deliveryEvidences.value = []
  endDialogVisible.value = true
}

function cancelEndDialog() {
  if (ending.value) return
  deliveryEvidences.value.forEach((evidence) => URL.revokeObjectURL(evidence.previewUrl))
  deliveryEvidences.value = []
  endingOrder.value = null
  endDialogVisible.value = false
}

function removeDeliveryEvidence(index: number) {
  const removed = deliveryEvidences.value.splice(index, 1)[0]
  if (removed) URL.revokeObjectURL(removed.previewUrl)
}

async function handleDeliveryEvidence(file: File) {
  if (deliveryEvidences.value.length >= 5) {
    ElMessage.warning('履约凭证最多上传 5 张')
    return false
  }
  uploadingEvidence.value = true
  try {
    const result = await uploadFile(file, 'delivery')
    deliveryEvidences.value.push({ fileUrl: result.url, fileName: result.fileName, previewUrl: URL.createObjectURL(file) })
    ElMessage.success('凭证已上传')
  } catch {
    // 上传失败由统一拦截器提示，保留其他已上传凭证。
  } finally {
    uploadingEvidence.value = false
  }
  return false
}

async function submitEndService() {
  if (!endingOrder.value || ending.value || uploadingEvidence.value) return
  try {
    await ElMessageBox.confirm('确认结束服务？结束后将等待用户确认完成。履约凭证为选填。', '结束服务', { type: 'info' })
  } catch {
    return
  }
  ending.value = true
  try {
    await endOrderService(endingOrder.value.id, deliveryEvidences.value.map(({ fileUrl, fileName }) => ({ fileUrl, fileName })))
    ElMessage.success('服务已结束，等待用户确认')
    endDialogVisible.value = false
    endingOrder.value = null
    deliveryEvidences.value.forEach((evidence) => URL.revokeObjectURL(evidence.previewUrl))
    deliveryEvidences.value = []
    await load()
  } catch {
    // http.ts 已统一提示错误
  } finally {
    ending.value = false
  }
}

async function openDetail(row: Order) {
  try {
    detail.value = await orderDetail(row.id)
    detailEvidenceImages.value = {}
    detailEvidenceErrors.value = {}
    await Promise.all((detail.value.deliveryEvidences ?? []).map(async (evidence) => {
      try {
        detailEvidenceImages.value[evidence.fileUrl] = await loadPrivateImage(evidence.fileUrl)
      } catch {
        detailEvidenceErrors.value[evidence.fileUrl] = true
      }
    }))
    detailVisible.value = true
  } catch {
    // http.ts 已统一提示错误
  }
}

const statusMap: Record<string, { label: string; type: TagProps['type'] }> = {
  PENDING_PAYMENT: { label: '待支付', type: 'info' },
  WAITING_ACCEPTANCE: { label: '待接单', type: 'warning' },
  WAITING_SERVICE: { label: '待服务', type: 'primary' },
  IN_SERVICE: { label: '服务中', type: 'success' },
  WAITING_CONFIRMATION: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  AFTER_SALES: { label: '售后中', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

const actionLabels: Record<string, string> = {
  CREATE: '创建订单',
  PAY: '支付成功',
  ACCEPT: '接单',
  REJECT: '拒绝订单',
  START: '开始服务',
  END: '结束服务',
  CONFIRM: '确认完成',
  CANCEL: '取消订单',
  COMPLAINT: '发起投诉',
  ARBITRATE: '平台仲裁',
  CLOSE_EXPIRED_PAYMENT: '支付超时关闭',
  CLOSE_EXPIRED_ACCEPTANCE: '接单超时关闭',
  AUTO_CONFIRM: '自动确认完成',
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}
</script>

<template>
  <div class="order-fulfill">
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="待接单" name="WAITING_ACCEPTANCE" />
      <el-tab-pane label="待服务" name="WAITING_SERVICE" />
      <el-tab-pane label="服务中" name="IN_SERVICE" />
      <el-tab-pane label="待确认" name="WAITING_CONFIRMATION" />
      <el-tab-pane label="历史" name="HISTORY" />
    </el-tabs>

    <div v-loading="loading">
      <el-card v-for="row in list" :key="row.id" class="order-card" shadow="hover">
        <div class="order-head">
          <el-tag :type="statusMap[row.orderStatus]?.type ?? 'info'" size="small">
            {{ statusMap[row.orderStatus]?.label ?? row.orderStatus }}
          </el-tag>
          <span class="order-no">{{ row.orderNo }}</span>
          <span class="order-time">{{ fmtTime(row.createdAt) }}</span>
        </div>
        <div class="order-body">
          <div class="main">
            <b>{{ row.serviceTitleSnapshot }}</b>
            <span class="meta">下单用户：{{ row.userNickname }}</span>
            <span class="meta">预约：{{ fmtTime(row.appointmentStartAt) }} - {{ fmtTime(row.appointmentEndAt) }}（{{ row.durationMinutes }}分钟）</span>
            <span class="meta" v-if="row.userRemark">需求备注：{{ row.userRemark }}</span>
            <span class="meta" v-if="row.closedReason">关闭原因：{{ row.closedReason }}</span>
          </div>
          <div class="amount">¥{{ (row.totalAmountCents / 100).toFixed(2) }}</div>
        </div>
        <div class="order-actions">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <template v-if="row.orderStatus === 'WAITING_ACCEPTANCE'">
            <el-button size="small" type="success" :loading="pendingActionIds.has(row.id)" :disabled="pendingActionIds.has(row.id)" @click="handleAccept(row)">接单</el-button>
            <el-button size="small" type="danger" :loading="pendingActionIds.has(row.id)" :disabled="pendingActionIds.has(row.id)" @click="handleReject(row)">拒绝</el-button>
          </template>
          <el-button v-if="row.orderStatus === 'WAITING_SERVICE'" size="small" type="primary" :loading="pendingActionIds.has(row.id)" :disabled="pendingActionIds.has(row.id)" @click="handleStart(row)">
            开始服务
          </el-button>
          <el-button v-if="row.orderStatus === 'IN_SERVICE'" size="small" type="warning" @click="openEndDialog(row)">
            结束服务
          </el-button>
        </div>
      </el-card>
      <el-empty v-if="!loading && !loadError && list.length === 0" description="暂无相关订单" />
    </div>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="(p: number) => { page = p; load() }"
    />

    <el-dialog v-model="endDialogVisible" title="结束服务并提交履约凭证（选填）" width="560px" @close="cancelEndDialog">
      <p class="text-muted">可上传 0～5 张图片，单张不超过 5MB。凭证将随订单状态一并保存。</p>
      <div class="delivery-evidence-list">
        <div v-for="(evidence, index) in deliveryEvidences" :key="evidence.fileUrl" class="delivery-evidence-item">
          <el-image :src="evidence.previewUrl" fit="cover" :preview-src-list="deliveryEvidences.map((item) => item.previewUrl)" preview-teleported />
          <el-button text type="danger" @click="removeDeliveryEvidence(index)">移除</el-button>
        </div>
        <el-upload v-if="deliveryEvidences.length < 5" :show-file-list="false" accept="image/*" :before-upload="handleDeliveryEvidence">
          <el-button :loading="uploadingEvidence">上传图片（{{ deliveryEvidences.length }}/5）</el-button>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="cancelEndDialog">取消</el-button>
        <el-button type="warning" :loading="ending" :disabled="uploadingEvidence" @click="submitEndService">确认结束服务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="订单详情" width="680px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="服务">{{ detail.serviceTitleSnapshot }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.userNickname }}</el-descriptions-item>
          <el-descriptions-item label="预约时间" :span="2">{{ fmtTime(detail.appointmentStartAt) }} - {{ fmtTime(detail.appointmentEndAt) }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ (detail.totalAmountCents / 100).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ detail.durationMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="区服">{{ detail.gameServer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="游戏昵称">{{ detail.gameNickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="需求备注" :span="2">{{ detail.userRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="trace-title">状态轨迹</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(h, i) in detail.statusHistories ?? []"
            :key="i"
            :timestamp="fmtTime(h.createdAt)"
          >
            {{ actionLabels[h.actionCode] ?? h.actionCode }}
            <span class="trace-reason" v-if="h.reason">（{{ h.reason }}）</span>
          </el-timeline-item>
        </el-timeline>
        <h4 class="trace-title">陪玩师履约凭证</h4>
        <div v-if="detail.deliveryEvidences?.length" class="delivery-evidence-list">
          <template v-for="e in detail.deliveryEvidences" :key="e.id">
            <el-image v-if="detailEvidenceImages[e.fileUrl]" :src="detailEvidenceImages[e.fileUrl]" fit="cover" :preview-src-list="Object.values(detailEvidenceImages)" preview-teleported />
            <span v-else class="text-muted">{{ detailEvidenceErrors[e.fileUrl] ? '图片加载失败' : '凭证加载中…' }}</span>
          </template>
        </div>
        <span v-else class="text-muted">陪玩师未上传履约凭证</span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-card {
  margin-bottom: 12px;
}
.delivery-evidence-list { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.delivery-evidence-item { display: flex; align-items: center; gap: 6px; }
.delivery-evidence-list :deep(.el-image) { width: 72px; height: 72px; border-radius: 6px; }
.order-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.order-no {
  font-size: 13px;
  color: #606266;
}
.order-time {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.order-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.meta {
  font-size: 13px;
  color: #606266;
}
.amount {
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}
.order-actions {
  margin-top: 10px;
  text-align: right;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.trace-title {
  margin: 16px 0 8px;
}
.trace-reason {
  color: #909399;
  font-size: 12px;
}
</style>
