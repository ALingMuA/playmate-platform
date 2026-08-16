<script setup lang="ts">
/**
 * 创建预约（对齐 frontend-prototype booking.html）。
 *
 * <p>选择服务项目、预约时间与时长，填写区服/昵称/备注后提交订单（FR-U07/U08），
 * 提交成功后跳转模拟支付页（FR-U09）。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { createOrder, listBookableServices, type BookableService } from '@/api/order'

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

const selectedService = computed(() => services.value.find((s) => s.id === form.serviceId))

/** 总价（分）= 单价 × 时长/60 */
const totalAmountCents = computed(() => {
  if (!selectedService.value || !form.durationMinutes) return 0
  return Math.round((selectedService.value.priceCents * form.durationMinutes) / 60)
})

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

async function handleSubmit() {
  const service = selectedService.value
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
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">创建预约</h1>
        <p class="page-subtitle">
          预约陪玩师 #{{ companionUserId }}
          <template v-if="selectedService"> · {{ selectedService.title }}</template>
        </p>
      </div>

      <div class="two-col">
        <!-- 左侧：表单 -->
        <div class="card">
          <div class="card-body">
            <h3 class="card-title mb-2">预约信息</h3>

            <div class="form-group">
              <label class="form-label">服务项目</label>
              <div v-if="services.length" class="radio-group">
                <div v-for="s in services" :key="s.id" class="radio-pill">
                  <input type="radio" :id="'svc-' + s.id" :value="s.id" v-model="form.serviceId" />
                  <label :for="'svc-' + s.id">{{ s.title }}（{{ fmtPrice(s.priceCents) }}/小时）</label>
                </div>
              </div>
              <p v-else class="text-muted" style="font-size: 0.875rem">该陪玩师暂无可预约服务</p>
            </div>

            <div class="form-group">
              <label class="form-label">开始时间</label>
              <el-date-picker
                v-model="form.appointmentStart"
                type="datetime"
                placeholder="选择预约开始时间"
                :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000"
                style="width: 100%"
              />
            </div>

            <div class="form-group">
              <label class="form-label">服务时长（分钟）</label>
              <el-input-number v-model="form.durationMinutes" :min="30" :max="600" :step="30" />
              <p v-if="selectedService" class="form-hint">
                该服务最短 {{ selectedService.minDurationMinutes }} 分钟，按小时计费
              </p>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">游戏区服</label>
                <input v-model="form.gameServer" class="form-control" maxlength="50" placeholder="如：微信区" />
              </div>
              <div class="form-group">
                <label class="form-label">游戏昵称</label>
                <input v-model="form.gameNickname" class="form-control" maxlength="50" placeholder="如：上分小能手" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">需求备注</label>
              <textarea v-model="form.userRemark" class="form-control" rows="3" maxlength="500"
                placeholder="选填，描述你的需求" />
            </div>
          </div>
        </div>

        <!-- 右侧：订单摘要（原型 sidebar） -->
        <aside class="sidebar">
          <h3 class="sidebar-title">订单摘要</h3>
          <div class="sidebar-section">
            <template v-if="selectedService">
              <div class="order-title">{{ selectedService.title }}</div>
              <div class="order-meta">{{ selectedService.gameName }} · {{ selectedService.serviceTypeName }}</div>
              <div class="order-meta mt-1">
                时长 {{ form.durationMinutes }} 分钟
                <template v-if="form.appointmentStart">
                  · {{ dayjs(form.appointmentStart).format('MM-DD HH:mm') }}
                </template>
              </div>
            </template>
            <p v-else class="text-muted" style="font-size: 0.875rem">请先选择服务项目</p>
          </div>
          <div class="sidebar-section">
            <div class="flex justify-between items-center">
              <span class="text-muted">应付金额</span>
              <span class="companion-price" style="font-size: 1.75rem">{{ fmtPrice(totalAmountCents) }}</span>
            </div>
          </div>
          <button class="btn btn-primary btn-block btn-lg" :disabled="submitting || !selectedService"
            @click="handleSubmit">
            {{ submitting ? '提交中…' : '提交订单' }}
          </button>
          <button class="btn btn-ghost btn-block mt-1" @click="router.back()">返回</button>
        </aside>
      </div>
    </div>
  </div>
</template>
