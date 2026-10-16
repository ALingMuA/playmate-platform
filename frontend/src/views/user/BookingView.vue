<script setup lang="ts">
/**
 * 创建预约（优化版：分步指引、时长快捷选项、移动端悬浮结算）。
 *
 * <p>选择服务项目、预约时间与时长，填写区服/昵称/备注后提交订单（FR-U07/U08），
 * 支持选择可用优惠券进行平台抵扣，提交成功后跳转模拟支付页（FR-U09）。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { createOrder, listBookableServices, type BookableService } from '@/api/order'
import { availableCoupons, previewCoupon, type UserCouponView, type CouponPreviewResult } from '@/api/coupon'

const route = useRoute()
const router = useRouter()

const companionUserId = computed(() => Number(route.query.companionUserId))
const presetServiceId = computed(() => Number(route.query.serviceId))

const services = ref<BookableService[]>([])
const loading = ref(true)
const submitting = ref(false)

const form = reactive({
  serviceId: undefined as number | undefined,
  appointmentStart: '' as string | undefined,
  durationMinutes: 60,
  gameServer: '',
  gameNickname: '',
  userRemark: '',
})

/** 快捷时长选项（分钟） */
const durationPresets = [
  { label: '1 小时', minutes: 60 },
  { label: '2 小时', minutes: 120 },
  { label: '3 小时', minutes: 180 },
]
const isCustomDuration = ref(false)

function selectPresetDuration(minutes: number) {
  isCustomDuration.value = false
  form.durationMinutes = minutes
}

function enableCustomDuration() {
  isCustomDuration.value = true
}

/** 是否展开选填信息 */
const showOptionalInfo = ref(false)

const selectedService = computed(() => services.value.find((s) => s.id === form.serviceId))

/** 优惠券状态 */
const userCoupons = ref<UserCouponView[]>([])
const selectedCouponId = ref<number | undefined>(undefined)
const previewResult = ref<CouponPreviewResult | null>(null)
const couponLoading = ref(false)

/** 原价（分）= 单价 × 时长/60 */
const totalAmountCents = computed(() => {
  if (!selectedService.value || !form.durationMinutes) return 0
  return Math.round((selectedService.value.priceCents * form.durationMinutes) / 60)
})

const finalPayableCents = computed(() => {
  return previewResult.value?.payableAmountCents ?? totalAmountCents.value
})

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

async function refreshCouponsAndPreview() {
  if (!form.serviceId || !form.durationMinutes) {
    userCoupons.value = []
    previewResult.value = null
    return
  }
  couponLoading.value = true
  try {
    const list = await availableCoupons(form.serviceId, form.durationMinutes)
    userCoupons.value = list
    // 若原先选中的券不再适用，重置
    if (selectedCouponId.value) {
      const found = list.find((c) => c.id === selectedCouponId.value)
      if (!found || !found.isApplicable) {
        selectedCouponId.value = undefined
      }
    }
    await updatePreview()
  } catch {
    userCoupons.value = []
  } finally {
    couponLoading.value = false
  }
}

async function updatePreview() {
  if (!form.serviceId || !form.durationMinutes) return
  try {
    const res = await previewCoupon(form.serviceId, form.durationMinutes, selectedCouponId.value)
    previewResult.value = res
  } catch {
    previewResult.value = null
  }
}

watch(
  () => [form.serviceId, form.durationMinutes],
  () => {
    refreshCouponsAndPreview()
  },
)

watch(selectedCouponId, () => {
  updatePreview()
})

async function handleSubmit() {
  const service = selectedService.value
  if (!service) {
    ElMessage.warning('请先选择服务项目')
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
  submitting.value = true
  try {
    const order = await createOrder({
      companionUserId: service.companionUserId,
      companionServiceId: service.id,
      durationMinutes: form.durationMinutes,
      gameServer: form.gameServer,
      gameNickname: form.gameNickname,
      userRemark: form.userRemark,
      appointmentStartAt: dayjs(form.appointmentStart).format('YYYY-MM-DD HH:mm:ss'),
      couponId: selectedCouponId.value || undefined,
    })
    ElMessage.success('订单已创建，请尽快支付')
    router.push(`/payment/${order.id}`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const result = await listBookableServices(undefined, companionUserId.value, 1, 50)
    services.value = result.records
    // 预选服务
    if (presetServiceId.value && services.value.some((s) => s.id === presetServiceId.value)) {
      form.serviceId = presetServiceId.value
      const svc = services.value.find((s) => s.id === presetServiceId.value)
      if (svc) form.durationMinutes = Math.max(60, svc.minDurationMinutes)
    } else if (services.value.length) {
      form.serviceId = services.value[0].id
    }
    // 检查预设时长是否匹配预设选项
    if (!durationPresets.some((p) => p.minutes === form.durationMinutes)) {
      isCustomDuration.value = true
    }
    await refreshCouponsAndPreview()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page has-sticky-bar">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">预约下单</h1>
        <p class="page-subtitle">简单两步快速预约陪玩师，提交后进入支付</p>
      </div>

      <!-- 陪玩师直观卡片 -->
      <div class="booking-companion-card" v-if="selectedService || services.length">
        <div class="companion-avatar">🎮</div>
        <div style="flex: 1">
          <div class="booking-companion-name">
            陪玩师 #{{ companionUserId }}
            <span class="badge badge-secondary" style="margin-left: 8px">认证陪玩</span>
          </div>
          <div class="booking-companion-meta">
            {{ selectedService?.gameName || '多款游戏' }} · 极速响应 · 支持在线保障
          </div>
        </div>
        <button class="btn btn-outline btn-sm" @click="router.push(`/companions/${companionUserId}`)">
          查看主页
        </button>
      </div>

      <div class="two-col-booking">
        <!-- 左侧：分步表单 -->
        <div>
          <!-- 第 1 步：核心服务与时间 -->
          <div class="card mb-2">
            <div class="card-body">
              <div class="step-header">
                <span class="step-number">1</span>
                <h3 class="card-title" style="margin: 0">选择服务与时间（必填）</h3>
              </div>

              <!-- 服务项目 -->
              <div class="form-group">
                <label class="form-label">服务项目</label>
                <div v-if="services.length" class="radio-group">
                  <div v-for="s in services" :key="s.id" class="radio-pill">
                    <input type="radio" :id="'svc-' + s.id" :value="s.id" v-model="form.serviceId" />
                    <label :for="'svc-' + s.id">
                      <strong>{{ s.title }}</strong>
                      <span>{{ fmtPrice(s.priceCents) }}/小时</span>
                    </label>
                  </div>
                </div>
                <p v-else class="text-muted" style="font-size: 0.875rem">该陪玩师暂无可预约服务</p>
              </div>

              <!-- 开始时间 -->
              <div class="form-group">
                <label class="form-label">开始时间</label>
                <el-date-picker
                  v-model="form.appointmentStart"
                  type="datetime"
                  placeholder="点击选择上号/开黑开始时间"
                  :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000"
                  style="width: 100%"
                />
                <p class="form-hint">建议选择至少 10 分钟后的时间，以便陪玩师提前就位</p>
              </div>

              <!-- 服务时长 -->
              <div class="form-group">
                <label class="form-label">服务时长</label>
                <div class="duration-pills">
                  <button
                    v-for="p in durationPresets"
                    :key="p.minutes"
                    type="button"
                    class="duration-pill"
                    :class="{ active: !isCustomDuration && form.durationMinutes === p.minutes }"
                    @click="selectPresetDuration(p.minutes)"
                  >
                    {{ p.label }}
                  </button>
                  <button
                    type="button"
                    class="duration-pill"
                    :class="{ active: isCustomDuration }"
                    @click="enableCustomDuration"
                  >
                    自定义时长
                  </button>
                </div>

                <div v-if="isCustomDuration" class="mt-1" style="display: flex; align-items: center; gap: 10px">
                  <el-input-number
                    v-model="form.durationMinutes"
                    :min="selectedService?.minDurationMinutes || 30"
                    :max="600"
                    :step="30"
                  />
                  <span class="text-muted" style="font-size: 0.875rem">分钟（即 {{ (form.durationMinutes / 60).toFixed(1) }} 小时）</span>
                </div>
                <p v-if="selectedService" class="form-hint">
                  该项目单价 {{ fmtPrice(selectedService.priceCents) }}/小时，最短 {{ selectedService.minDurationMinutes }} 分钟
                </p>
              </div>
            </div>
          </div>

          <!-- 第 2 步：优惠券与选填信息 -->
          <div class="card mb-2">
            <div class="card-body">
              <div class="step-header">
                <span class="step-number step-optional">2</span>
                <h3 class="card-title" style="margin: 0">优惠券与补充信息（选填）</h3>
              </div>

              <!-- 优惠券选择 -->
              <div class="form-group">
                <label class="form-label">优惠券抵扣</label>
                <el-select
                  v-model="selectedCouponId"
                  placeholder="选择可用优惠券"
                  clearable
                  :loading="couponLoading"
                  style="width: 100%"
                >
                  <el-option :value="undefined" label="不使用优惠券" />
                  <el-option
                    v-for="c in userCoupons"
                    :key="c.id"
                    :value="c.id"
                    :label="`${c.templateName}（${c.isApplicable ? `可抵扣 ¥${((c.expectedDiscountCents || 0) / 100).toFixed(2)}` : c.notApplicableReason}）`"
                    :disabled="!c.isApplicable"
                  />
                </el-select>
                <p v-if="previewResult && previewResult.discountAmountCents > 0" class="form-hint" style="color: #10b981">
                  ✓ 优惠券生效中：已减免 {{ fmtPrice(previewResult.discountAmountCents) }}
                </p>
              </div>

              <!-- 选填信息折叠与展开 -->
              <div style="border-top: 1px dashed var(--border); padding-top: 14px">
                <div
                  class="flex items-center justify-between collapsible-toggle"
                  @click="showOptionalInfo = !showOptionalInfo"
                >
                  <span style="font-size: 0.875rem; font-weight: 600; color: var(--foreground)">
                    🎮 游戏账号与需求信息
                    <span class="text-muted" style="font-weight: normal; margin-left: 4px">（选填，不填也可下单）</span>
                  </span>
                  <span class="toggle-arrow" :class="{ open: showOptionalInfo }">
                    {{ showOptionalInfo ? '收起 ▲' : '填写 ▼' }}
                  </span>
                </div>

                <div v-show="showOptionalInfo" class="mt-2">
                  <div class="form-row">
                    <div class="form-group">
                      <label class="form-label">游戏区服</label>
                      <input v-model="form.gameServer" class="form-control" maxlength="50" placeholder="如：微信区 / 一区艾欧尼亚" />
                    </div>
                    <div class="form-group">
                      <label class="form-label">游戏昵称</label>
                      <input v-model="form.gameNickname" class="form-control" maxlength="50" placeholder="如：上分小能手" />
                    </div>
                  </div>

                  <div class="form-group" style="margin-bottom: 0">
                    <label class="form-label">需求备注</label>
                    <textarea
                      v-model="form.userRemark"
                      class="form-control"
                      rows="2"
                      maxlength="500"
                      placeholder="选填：如段位目标、开麦需求或指定英雄"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：订单摘要与提交 -->
        <aside class="sidebar">
          <h3 class="sidebar-title">订单明细</h3>
          <div class="sidebar-section">
            <template v-if="selectedService">
              <div class="order-title">{{ selectedService.title }}</div>
              <div class="order-meta">{{ selectedService.gameName }} · {{ selectedService.serviceTypeName }}</div>
              <div class="order-meta mt-1">
                时长 {{ form.durationMinutes }} 分钟（{{ (form.durationMinutes / 60).toFixed(1) }} 小时）
                <template v-if="form.appointmentStart">
                  <br />开黑时间：{{ dayjs(form.appointmentStart).format('MM-DD HH:mm') }}
                </template>
              </div>
            </template>
            <p v-else class="text-muted" style="font-size: 0.875rem">请在左侧选择服务项目</p>
          </div>

          <div class="sidebar-section">
            <div class="flex justify-between items-center text-muted" style="margin-bottom: 8px">
              <span>服务原价</span>
              <span>{{ fmtPrice(previewResult?.originalAmountCents || totalAmountCents) }}</span>
            </div>
            <div v-if="previewResult && previewResult.discountAmountCents > 0" class="flex justify-between items-center" style="color: #ef4444; margin-bottom: 8px">
              <span>优惠券抵扣</span>
              <span>- {{ fmtPrice(previewResult.discountAmountCents) }}</span>
            </div>
            <div class="flex justify-between items-center" style="border-top: 1px dashed var(--border); padding-top: 8px">
              <span class="text-muted">实付金额</span>
              <span class="companion-price" style="font-size: 1.75rem">
                {{ fmtPrice(finalPayableCents) }}
              </span>
            </div>
          </div>

          <button
            class="btn btn-primary btn-block btn-lg"
            :disabled="submitting || !selectedService"
            @click="handleSubmit"
          >
            {{ submitting ? '提交中…' : '立即预约下单' }}
          </button>
          <button class="btn btn-ghost btn-block mt-1" @click="router.back()">返回上一步</button>

          <p class="text-muted" style="font-size: 0.75rem; text-align: center; margin-top: 12px">
            🔒 平台担保交易 · 服务完成前资金安全托管
          </p>
        </aside>
      </div>

      <!-- 移动端底部悬浮栏 -->
      <div class="sticky-bottom-bar">
        <div class="sticky-bottom-price">
          <div>实付金额</div>
          <span class="price-value">{{ fmtPrice(finalPayableCents) }}</span>
        </div>
        <button
          class="btn btn-primary"
          style="padding: 10px 24px"
          :disabled="submitting || !selectedService"
          @click="handleSubmit"
        >
          {{ submitting ? '提交中…' : '立即预约' }}
        </button>
      </div>
    </div>
  </div>
</template>
