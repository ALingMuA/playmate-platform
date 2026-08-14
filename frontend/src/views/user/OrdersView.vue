<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { listGames, type Game } from '@/api/catalog'
import {
  cancelOrder,
  confirmOrder,
  createOrder,
  listBookableServices,
  myOrders,
  payOrder,
  type BookableService,
  type Order,
} from '@/api/order'
import { uploadFile } from '@/api/file'
import { createComplaint, reviewByOrder, submitReview } from '@/api/review'

const activeTab = ref('ALL')
const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

// 创建订单对话框
const createVisible = ref(false)
const games = ref<Game[]>([])
const services = ref<BookableService[]>([])
const serviceTotal = ref(0)
const svcPage = ref(1)
const creating = ref(false)
const form = reactive({
  gameId: undefined as number | undefined,
  serviceId: undefined as number | undefined,
  appointmentStart: '',
  durationMinutes: 60,
  gameServer: '',
  gameNickname: '',
  userRemark: '',
})

async function load() {
  loading.value = true
  try {
    const status = activeTab.value === 'ALL' ? undefined : activeTab.value
    const result = await myOrders(status, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  load()
}

onMounted(() => {
  load()
  listGames().then((g) => (games.value = g))
})

async function loadServices() {
  const result = await listBookableServices(form.gameId, undefined, svcPage.value, 20)
  services.value = result.records
  serviceTotal.value = result.total
}

function openCreate() {
  Object.assign(form, {
    gameId: undefined,
    serviceId: undefined,
    appointmentStart: '',
    durationMinutes: 60,
    gameServer: '',
    gameNickname: '',
    userRemark: '',
  })
  svcPage.value = 1
  createVisible.value = true
  loadServices()
}

function onGameChange() {
  form.serviceId = undefined
  svcPage.value = 1
  loadServices()
}

async function handleCreate() {
  const service = services.value.find((s) => s.id === form.serviceId)
  if (!service) {
    ElMessage.warning('请选择服务项目')
    return
  }
  if (!form.appointmentStart) {
    ElMessage.warning('请选择预约开始时间')
    return
  }
  if (form.durationMinutes < service.minDurationMinutes) {
    ElMessage.warning(`该服务最短时长为 ${service.minDurationMinutes} 分钟`)
    return
  }
  creating.value = true
  try {
    await createOrder({
      companionUserId: service.companionUserId,
      companionServiceId: form.serviceId!,
      durationMinutes: form.durationMinutes,
      gameServer: form.gameServer,
      gameNickname: form.gameNickname,
      userRemark: form.userRemark,
      appointmentStartAt: dayjs(form.appointmentStart).format('YYYY-MM-DD HH:mm:ss'),
    })
    ElMessage.success('订单已创建，请尽快支付')
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

async function handlePay(row: Order) {
  await payOrder(row.id)
  ElMessage.success('支付成功')
  await load()
}

async function handleCancel(row: Order) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      row.orderStatus === 'PENDING_PAYMENT' ? '订单未支付，取消后将直接关闭。' : '订单已支付，取消后将全额退回虚拟余额。',
      `取消订单 ${row.orderNo}`,
      {
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '请填写取消原因（选填，不超过500字）',
        inputValidator: (v: string) => (v ?? '').length <= 500 || '原因不能超过500字',
      },
    )
    reason = result.value
  } catch {
    return // 用户放弃取消
  }
  await cancelOrder(row.id, reason)
  ElMessage.success(row.orderStatus === 'PENDING_PAYMENT' ? '订单已取消' : '订单已取消，款项已退回')
  await load()
}

async function handleConfirm(row: Order) {
  await ElMessageBox.confirm('确认服务已完成？确认后陪玩师将收到收益。', '确认完成', { type: 'info' })
  await confirmOrder(row.id)
  ElMessage.success('已完成')
  await load()
}

// ==================== 评价（FR-U15） ====================

const reviewVisible = ref(false)
const reviewOrder = ref<Order | null>(null)
const submittingReview = ref(false)
const reviewForm = reactive({
  score: 5,
  tags: [] as string[],
  content: '',
})
const TAG_OPTIONS = ['上分快', '脾气好', '准时', '技术强', '耐心', '幽默风趣', '沟通顺畅']

async function openReview(row: Order) {
  // 每个订单只能评价一次：先查询是否已评价
  const existed = await reviewByOrder(row.id)
  if (existed) {
    ElMessage.warning('该订单已评价')
    return
  }
  reviewOrder.value = row
  reviewForm.score = 5
  reviewForm.tags = []
  reviewForm.content = ''
  reviewVisible.value = true
}

async function handleSubmitReview() {
  if (!reviewOrder.value) return
  if (!reviewForm.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }
  submittingReview.value = true
  try {
    await submitReview(reviewOrder.value.id, {
      score: reviewForm.score,
      tags: reviewForm.tags,
      content: reviewForm.content.trim(),
    })
    ElMessage.success('评价成功，感谢反馈')
    reviewVisible.value = false
    await load()
  } finally {
    submittingReview.value = false
  }
}

// ==================== 投诉（FR-U17/U18） ====================

const complaintVisible = ref(false)
const complaintOrder = ref<Order | null>(null)
const submittingComplaint = ref(false)
const complaintForm = reactive({
  complaintType: 'NO_FULFILLMENT',
  description: '',
  evidences: [] as { fileUrl: string; fileName: string }[],
})
const complaintTypeOptions = [
  { value: 'NO_FULFILLMENT', label: '未履约' },
  { value: 'LATE', label: '迟到' },
  { value: 'ATTITUDE', label: '态度问题' },
  { value: 'MISMATCH', label: '服务不符' },
  { value: 'OTHER', label: '其他' },
]

function openComplaint(row: Order) {
  complaintOrder.value = row
  complaintForm.complaintType = 'NO_FULFILLMENT'
  complaintForm.description = ''
  complaintForm.evidences = []
  complaintVisible.value = true
}

async function handleComplaintEvidence(file: File) {
  if (complaintForm.evidences.length >= 5) {
    ElMessage.warning('图片证据最多 5 张')
    return false
  }
  try {
    const result = await uploadFile(file, 'evidence')
    complaintForm.evidences.push({ fileUrl: result.url, fileName: result.fileName })
    ElMessage.success('证据上传成功')
  } catch {
    // 上传失败已由 http 拦截器提示
  }
  return false // 阻止 el-upload 默认上传
}

function removeEvidence(index: number) {
  complaintForm.evidences.splice(index, 1)
}

async function handleSubmitComplaint() {
  if (!complaintOrder.value) return
  if (!complaintForm.description.trim()) {
    ElMessage.warning('请填写投诉说明')
    return
  }
  submittingComplaint.value = true
  try {
    await createComplaint(complaintOrder.value.id, {
      complaintType: complaintForm.complaintType,
      description: complaintForm.description.trim(),
      evidences: complaintForm.evidences,
    })
    ElMessage.success('投诉已提交，订单进入售后处理')
    complaintVisible.value = false
    await load()
  } finally {
    submittingComplaint.value = false
  }
}

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING_PAYMENT: { label: '待支付', type: 'warning' },
  WAITING_ACCEPTANCE: { label: '待接单', type: 'info' },
  WAITING_SERVICE: { label: '待服务', type: 'primary' },
  IN_SERVICE: { label: '服务中', type: 'success' },
  WAITING_CONFIRMATION: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  AFTER_SALES: { label: '售后中', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}
</script>

<template>
  <div class="orders-view">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">预约陪玩</el-button>
      <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="待支付" name="PENDING_PAYMENT" />
        <el-tab-pane label="待服务" name="WAITING_SERVICE" />
        <el-tab-pane label="服务中" name="IN_SERVICE" />
        <el-tab-pane label="待确认" name="WAITING_CONFIRMATION" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="售后中" name="AFTER_SALES" />
        <el-tab-pane label="已关闭" name="CLOSED" />
      </el-tabs>
    </div>

    <el-card v-for="row in list" :key="row.id" class="order-card" shadow="hover" v-loading="loading">
      <div class="order-head">
        <el-tag :type="(statusMap[row.orderStatus]?.type as any) ?? 'info'" size="small">
          {{ statusMap[row.orderStatus]?.label ?? row.orderStatus }}
        </el-tag>
        <span class="order-no">{{ row.orderNo }}</span>
        <span class="order-time">{{ fmtTime(row.createdAt) }}</span>
      </div>
      <div class="order-body">
        <div class="main">
          <b>{{ row.serviceTitleSnapshot }}</b>
          <span class="meta">陪玩师：{{ row.companionNameSnapshot }}</span>
          <span class="meta">预约：{{ fmtTime(row.appointmentStartAt) }} - {{ fmtTime(row.appointmentEndAt) }}（{{ row.durationMinutes }}分钟）</span>
          <span class="meta" v-if="row.userRemark">备注：{{ row.userRemark }}</span>
          <span class="meta" v-if="row.closedReason">关闭原因：{{ row.closedReason }}</span>
        </div>
        <div class="amount">¥{{ (row.totalAmountCents / 100).toFixed(2) }}</div>
      </div>
      <div class="order-actions">
        <el-button v-if="row.orderStatus === 'PENDING_PAYMENT'" size="small" type="primary" @click="handlePay(row)">
          去支付
        </el-button>
        <el-button
          v-if="['PENDING_PAYMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE'].includes(row.orderStatus)"
          size="small"
          @click="handleCancel(row)"
        >
          取消订单
        </el-button>
        <el-button v-if="row.orderStatus === 'WAITING_CONFIRMATION'" size="small" type="success" @click="handleConfirm(row)">
          确认完成
        </el-button>
        <el-button v-if="row.orderStatus === 'COMPLETED'" size="small" type="warning" @click="openReview(row)">
          评价
        </el-button>
        <el-button
          v-if="['WAITING_CONFIRMATION', 'COMPLETED', 'AFTER_SALES'].includes(row.orderStatus)"
          size="small"
          type="danger"
          plain
          @click="openComplaint(row)"
        >
          投诉
        </el-button>
      </div>
    </el-card>
    <el-empty v-if="!loading && list.length === 0" description="暂无订单" />

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="(p: number) => { page = p; load() }"
    />

    <el-dialog v-model="createVisible" title="预约陪玩" width="620px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="游戏" required>
          <el-select v-model="form.gameId" placeholder="选择游戏" style="width: 100%" @change="onGameChange">
            <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务项目" required>
          <el-select v-model="form.serviceId" placeholder="选择服务" style="width: 100%" filterable>
            <el-option v-for="s in services" :key="s.id" :label="`${s.title}（¥${(s.priceCents / 100).toFixed(0)}/小时）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="form.appointmentStart" type="datetime" placeholder="选择预约开始时间"
            :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长(分钟)" required>
          <el-input-number v-model="form.durationMinutes" :min="30" :max="600" :step="30" />
        </el-form-item>
        <el-form-item label="游戏区服">
          <el-input v-model="form.gameServer" placeholder="如：微信区" maxlength="50" />
        </el-form-item>
        <el-form-item label="游戏昵称">
          <el-input v-model="form.gameNickname" placeholder="如：上分小能手" maxlength="50" />
        </el-form-item>
        <el-form-item label="需求备注">
          <el-input v-model="form.userRemark" type="textarea" :rows="2" maxlength="500" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">提交订单</el-button>
      </template>
    </el-dialog>

    <!-- 评价对话框（FR-U15） -->
    <el-dialog v-model="reviewVisible" title="评价订单" width="520px">
      <el-form label-width="80px">
        <el-form-item label="综合评分" required>
          <el-rate v-model="reviewForm.score" :texts="['很差', '较差', '一般', '满意', '非常满意']" show-text />
        </el-form-item>
        <el-form-item label="评价标签">
          <el-checkbox-group v-model="reviewForm.tags">
            <el-checkbox v-for="t in TAG_OPTIONS" :key="t" :value="t" :label="t" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="评价内容" required>
          <el-input v-model="reviewForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit
            placeholder="说说这次陪玩体验吧（1~1000字）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingReview" @click="handleSubmitReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 投诉对话框（FR-U17/U18） -->
    <el-dialog v-model="complaintVisible" title="发起投诉" width="560px">
      <el-form label-width="80px">
        <el-form-item label="投诉类型" required>
          <el-select v-model="complaintForm.complaintType" style="width: 100%">
            <el-option v-for="t in complaintTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="投诉说明" required>
          <el-input v-model="complaintForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit
            placeholder="请描述问题经过（最多2000字）" />
        </el-form-item>
        <el-form-item label="图片证据">
          <div class="evidence-list">
            <div v-for="(ev, i) in complaintForm.evidences" :key="i" class="evidence-item">
              <el-image :src="ev.fileUrl" fit="cover" class="evidence-img" :preview-src-list="[ev.fileUrl]" preview-teleported />
              <span class="evidence-name">{{ ev.fileName }}</span>
              <el-button size="small" type="danger" text @click="removeEvidence(i)">移除</el-button>
            </div>
            <el-upload
              v-if="complaintForm.evidences.length < 5"
              :show-file-list="false"
              accept="image/*"
              :before-upload="handleComplaintEvidence"
            >
              <el-button :disabled="complaintForm.evidences.length >= 5">上传证据（最多5张）</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="complaintVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingComplaint" @click="handleSubmitComplaint">提交投诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.tabs {
  flex: 1;
}
.order-card {
  margin-bottom: 12px;
}
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
.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.evidence-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.evidence-img {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}
.evidence-name {
  flex: 1;
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>