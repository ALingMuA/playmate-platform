<script setup lang="ts">
/**
 * 管理后台 - 优惠券管理
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  adminCoupons,
  adminCreateCoupon,
  adminDeleteCoupon,
  adminOfflineCoupon,
  adminPublishCoupon,
  adminUpdateCoupon,
  type CouponTemplateForm,
  type CouponTemplateView,
} from '@/api/coupon'

const list = ref<CouponTemplateView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const status = ref<string | undefined>(undefined)

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下线', type: 'warning' },
}

const scopeTypeMap: Record<string, string> = {
  ALL: '全场通用',
  GAME: '指定游戏',
  SERVICE: '指定品类',
  COMPANION: '指定陪玩师',
}

function fmtMoney(cents: number): string {
  return (cents / 100).toFixed(2)
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  try {
    const result = await adminCoupons(status.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  page.value = 1
  load()
}

// ==================== 新增/编辑弹窗 ====================

const editVisible = ref(false)
const saving = ref(false)
const timeRange = ref<[Date, Date] | null>(null)

const form = reactive<{
  id: number
  name: string
  couponType: 'FIXED' | 'DISCOUNT'
  thresholdYuan: number
  discountYuan: number
  discountRateZhe: number
  maxDiscountYuan: number
  scopeType: string
  scopeId: number
  totalQuantity: number
  perUserLimit: number
}>({
  id: 0,
  name: '',
  couponType: 'FIXED',
  thresholdYuan: 50,
  discountYuan: 10,
  discountRateZhe: 8.5,
  maxDiscountYuan: 30,
  scopeType: 'ALL',
  scopeId: 0,
  totalQuantity: 500,
  perUserLimit: 1,
})

function openCreate() {
  form.id = 0
  form.name = ''
  form.couponType = 'FIXED'
  form.thresholdYuan = 50
  form.discountYuan = 10
  form.discountRateZhe = 8.5
  form.maxDiscountYuan = 30
  form.scopeType = 'ALL'
  form.scopeId = 0
  form.totalQuantity = 500
  form.perUserLimit = 1

  const now = new Date()
  const nextMonth = new Date(now.getTime() + 30 * 86400000)
  timeRange.value = [now, nextMonth]

  editVisible.value = true
}

function openEdit(row: CouponTemplateView) {
  form.id = row.id
  form.name = row.name
  form.couponType = row.couponType
  form.thresholdYuan = row.thresholdCents / 100
  form.discountYuan = row.discountCents / 100
  form.discountRateZhe = row.discountRateBp / 1000
  form.maxDiscountYuan = row.maxDiscountCents / 100
  form.scopeType = row.scopeType
  form.scopeId = row.scopeId
  form.totalQuantity = row.totalQuantity
  form.perUserLimit = row.perUserLimit

  if (row.validFrom && row.validUntil) {
    timeRange.value = [new Date(row.validFrom), new Date(row.validUntil)]
  } else {
    timeRange.value = null
  }
  editVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入优惠券名称')
    return
  }
  if (!timeRange.value || timeRange.value.length < 2) {
    ElMessage.warning('请选择优惠券有效期')
    return
  }

  saving.value = true
  try {
    const payload: CouponTemplateForm = {
      name: form.name.trim(),
      couponType: form.couponType,
      thresholdCents: Math.round(form.thresholdYuan * 100),
      discountCents: form.couponType === 'FIXED' ? Math.round(form.discountYuan * 100) : 0,
      discountRateBp: form.couponType === 'DISCOUNT' ? Math.round(form.discountRateZhe * 1000) : 10000,
      maxDiscountCents: form.couponType === 'DISCOUNT' ? Math.round(form.maxDiscountYuan * 100) : 0,
      scopeType: form.scopeType,
      scopeId: form.scopeId || 0,
      totalQuantity: form.totalQuantity,
      perUserLimit: form.perUserLimit,
      validFrom: dayjs(timeRange.value[0]).format('YYYY-MM-DD HH:mm:ss'),
      validUntil: dayjs(timeRange.value[1]).format('YYYY-MM-DD HH:mm:ss'),
    }

    if (form.id) {
      await adminUpdateCoupon(form.id, payload)
      ElMessage.success('优惠券已更新')
    } else {
      await adminCreateCoupon(payload)
      ElMessage.success('优惠券已创建（草稿）')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 状态操作 ====================

async function handlePublish(row: CouponTemplateView) {
  await ElMessageBox.confirm(`确定发布优惠券「${row.name}」吗？发布后用户可正常领取并抵扣。`, '发布优惠券', { type: 'info' })
  await adminPublishCoupon(row.id)
  ElMessage.success('优惠券已发布')
  await load()
}

async function handleOffline(row: CouponTemplateView) {
  await ElMessageBox.confirm(`确定下线优惠券「${row.name}」吗？下线后用户将无法继续领取。`, '下线优惠券', { type: 'warning' })
  await adminOfflineCoupon(row.id)
  ElMessage.success('优惠券已下线')
  await load()
}

async function handleDelete(row: CouponTemplateView) {
  await ElMessageBox.confirm(`确定删除优惠券「${row.name}」吗？删除后不可恢复。`, '删除优惠券', { type: 'warning' })
  await adminDeleteCoupon(row.id)
  ElMessage.success('优惠券已删除')
  await load()
}

onMounted(() => {
  load()
})
</script>

<template>
  <div>
    <!-- 头部筛选与操作区 -->
    <div class="flex justify-between items-center mb-4">
      <div class="flex items-center gap-2">
        <el-select
          v-model="status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="onStatusChange"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
      <div>
        <el-button type="primary" @click="openCreate">新建优惠券</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="优惠券名称" min-width="150" />
        <el-table-column label="券类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.couponType === 'FIXED' ? 'danger' : 'warning'" size="small">
              {{ row.couponType === 'FIXED' ? '满减券' : '折扣券' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优惠规则" min-width="160">
          <template #default="{ row }">
            <div v-if="row.couponType === 'FIXED'">
              <span class="text-danger font-bold">减 ¥{{ fmtMoney(row.discountCents) }}</span>
              <span class="text-xs text-muted ml-1">
                ({{ row.thresholdCents > 0 ? `满¥${fmtMoney(row.thresholdCents)}可用` : '无门槛' }})
              </span>
            </div>
            <div v-else>
              <span class="text-warning font-bold">{{ row.discountRateBp / 1000 }}折</span>
              <span class="text-xs text-muted ml-1">
                (最高减¥{{ fmtMoney(row.maxDiscountCents) }})
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="适用范围" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ scopeTypeMap[row.scopeType] || row.scopeType }}</el-tag>
            <div v-if="row.scopeId > 0" class="text-xs text-muted mt-1">ID: {{ row.scopeId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发放进度" width="140">
          <template #default="{ row }">
            <div class="text-xs">
              <div>已领：{{ row.claimedQuantity }} / {{ row.totalQuantity }}</div>
              <el-progress
                :percentage="Math.min(100, Math.round((row.claimedQuantity / (row.totalQuantity || 1)) * 100))"
                :stroke-width="6"
                class="mt-1"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="perUserLimit" label="人均限领" width="90">
          <template #default="{ row }">{{ row.perUserLimit }} 张</template>
        </el-table-column>
        <el-table-column label="有效期" width="220">
          <template #default="{ row }">
            <div class="text-xs">
              <div>起：{{ fmtTime(row.validFrom) }}</div>
              <div>止：{{ fmtTime(row.validUntil) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.publishStatus]?.type as any) ?? 'info'">
              {{ statusMap[row.publishStatus]?.label ?? row.publishStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.publishStatus !== 'PUBLISHED'"
              link
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.publishStatus === 'PUBLISHED'"
              link
              type="warning"
              @click="handleOffline(row)"
            >
              下线
            </el-button>
            <el-button
              v-if="row.publishStatus !== 'PUBLISHED'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="form.id ? '编辑优惠券' : '新建优惠券'"
      width="600px"
      destroy-on-close
    >
      <el-form label-width="110px">
        <el-form-item label="优惠券名称" required>
          <el-input v-model="form.name" placeholder="如：新人无门槛优惠券" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="券类型" required>
          <el-radio-group v-model="form.couponType">
            <el-radio value="FIXED">满减券（固定金额抵扣）</el-radio>
            <el-radio value="DISCOUNT">折扣券（比例折扣）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="门槛金额(元)" required>
          <el-input-number v-model="form.thresholdYuan" :min="0" :precision="2" :step="10" />
          <span class="text-xs text-muted ml-2">填 0 为无门槛立减</span>
        </el-form-item>

        <template v-if="form.couponType === 'FIXED'">
          <el-form-item label="减免金额(元)" required>
            <el-input-number v-model="form.discountYuan" :min="0.01" :precision="2" :step="5" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="折扣(折)" required>
            <el-input-number v-model="form.discountRateZhe" :min="1" :max="9.9" :precision="1" :step="0.5" />
            <span class="text-xs text-muted ml-2">例如 8.5 折，用户支付 85%</span>
          </el-form-item>
          <el-form-item label="最高减免(元)" required>
            <el-input-number v-model="form.maxDiscountYuan" :min="0" :precision="2" :step="10" />
            <span class="text-xs text-muted ml-2">填 0 为不封顶</span>
          </el-form-item>
        </template>

        <el-form-item label="适用范围" required>
          <el-select v-model="form.scopeType" class="w-full">
            <el-option label="全场通用" value="ALL" />
            <el-option label="指定游戏" value="GAME" />
            <el-option label="指定服务品类" value="SERVICE" />
            <el-option label="指定陪玩师" value="COMPANION" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType !== 'ALL'" label="范围对象ID" required>
          <el-input-number v-model="form.scopeId" :min="1" />
          <span class="text-xs text-muted ml-2">游戏ID / 服务品类ID / 陪玩师ID</span>
        </el-form-item>
        <el-form-item label="发放总量" required>
          <el-input-number v-model="form.totalQuantity" :min="1" :max="1000000" :step="100" />
          <span class="text-xs text-muted ml-2">张</span>
        </el-form-item>
        <el-form-item label="人均限领" required>
          <el-input-number v-model="form.perUserLimit" :min="1" :max="50" />
          <span class="text-xs text-muted ml-2">张/人</span>
        </el-form-item>
        <el-form-item label="有效时间段" required>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始生效时间"
            end-placeholder="到期失效时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.flex {
  display: flex;
}
.items-center {
  align-items: center;
}
.justify-between {
  justify-content: space-between;
}
.justify-end {
  justify-content: flex-end;
}
.gap-2 {
  gap: 8px;
}
.w-full {
  width: 100%;
}
.mb-4 {
  margin-bottom: 16px;
}
.mt-1 {
  margin-top: 4px;
}
.mt-4 {
  margin-top: 16px;
}
.ml-1 {
  margin-left: 4px;
}
.ml-2 {
  margin-left: 8px;
}
.text-xs {
  font-size: 0.75rem;
}
.font-bold {
  font-weight: 600;
}
.text-danger {
  color: #ef4444;
}
.text-warning {
  color: #f59e0b;
}
.text-muted {
  color: #9ca3af;
}
</style>
