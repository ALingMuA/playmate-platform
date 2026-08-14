<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptOrder,
  companionOrders,
  endOrderService,
  orderDetail,
  rejectOrder,
  startOrderService,
  type Order,
} from '@/api/order'

const activeTab = ref('WAITING_ACCEPTANCE')
const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref<Order | null>(null)

async function load() {
  loading.value = true
  try {
    const status = activeTab.value === 'HISTORY' ? 'COMPLETED,CLOSED' : activeTab.value
    const result = await companionOrders(status, page.value, pageSize)
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

onMounted(load)

async function handleAccept(row: Order) {
  await ElMessageBox.confirm(`确认接受订单「${row.serviceTitleSnapshot}」？`, '接单确认', { type: 'info' })
  await acceptOrder(row.id)
  ElMessage.success('接单成功，等待服务开始')
  await load()
}

async function handleReject(row: Order) {
  const { value } = await ElMessageBox.prompt('请填写拒绝原因', '拒绝订单', {
    inputPlaceholder: '如：档期冲突、临时有事',
    inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '拒绝原因不能为空'),
  })
  await rejectOrder(row.id, value)
  ElMessage.success('已拒绝订单，款项已退还用户')
  await load()
}

async function handleStart(row: Order) {
  await ElMessageBox.confirm('确认开始服务？', '开始服务', { type: 'info' })
  await startOrderService(row.id)
  ElMessage.success('服务已开始')
  await load()
}

async function handleEnd(row: Order) {
  await ElMessageBox.confirm('确认结束服务？结束后将等待用户确认完成。', '结束服务', { type: 'info' })
  await endOrderService(row.id)
  ElMessage.success('服务已结束，等待用户确认')
  await load()
}

async function openDetail(row: Order) {
  detail.value = await orderDetail(row.id)
  detailVisible.value = true
}

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING_PAYMENT: { label: '待支付', type: 'info' },
  WAITING_ACCEPTANCE: { label: '待接单', type: 'warning' },
  WAITING_SERVICE: { label: '待服务', type: 'primary' },
  IN_SERVICE: { label: '服务中', type: 'success' },
  WAITING_CONFIRMATION: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
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
          <el-tag :type="(statusMap[row.orderStatus]?.type as any) ?? 'info'" size="small">
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
            <el-button size="small" type="success" @click="handleAccept(row)">接单</el-button>
            <el-button size="small" type="danger" @click="handleReject(row)">拒绝</el-button>
          </template>
          <el-button v-if="row.orderStatus === 'WAITING_SERVICE'" size="small" type="primary" @click="handleStart(row)">
            开始服务
          </el-button>
          <el-button v-if="row.orderStatus === 'IN_SERVICE'" size="small" type="warning" @click="handleEnd(row)">
            结束服务
          </el-button>
        </div>
      </el-card>
      <el-empty v-if="!loading && list.length === 0" description="暂无相关订单" />
    </div>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="page"
      @current-change="(p: number) => { page = p; load() }"
    />

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
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
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
