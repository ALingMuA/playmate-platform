<script setup lang="ts">
/**
 * 评价订单（对齐 docs/06-原型与架构图/frontend-prototype/review.html）。
 *
 * <p>对已完成订单提交 1~5 星评分、标签与文字评价（FR-U15），
 * 每个订单只能评价一次。</p>
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userOrderDetail, type Order } from '@/api/order'
import { reviewByOrder, submitReview } from '@/api/review'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.orderId))
const order = ref<Order | null>(null)
const loading = ref(true)
const submitting = ref(false)
const loadError = ref('')

const form = reactive({
  score: 5,
  tags: [] as string[],
  content: '',
})

const TAG_OPTIONS = ['上分快', '脾气好', '准时', '技术强', '耐心', '幽默风趣', '沟通顺畅']

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

let loadSeq = 0

async function handleSubmit() {
  if (submitting.value) return
  if (!form.content.trim()) {
    ElMessage.warning('请填写评价内容')
    return
  }
  submitting.value = true
  try {
    await submitReview(orderId.value, {
      score: form.score,
      tags: form.tags,
      content: form.content.trim(),
    })
    ElMessage.success('评价成功，感谢反馈')
    await router.replace('/orders')
  } catch {
    // http.ts 已统一提示错误
  } finally {
    submitting.value = false
  }
}

async function loadOrder() {
  const id = orderId.value
  const seq = ++loadSeq
  loading.value = true
  order.value = null
  loadError.value = ''
  if (!Number.isSafeInteger(id) || id <= 0) {
    loadError.value = '订单链接无效，请从订单列表重新进入评价。'
    loading.value = false
    return
  }
  try {
    const [orderData, existed] = await Promise.all([
      userOrderDetail(id),
      reviewByOrder(id).catch(() => null),
    ])
    if (seq !== loadSeq || orderId.value !== id) return
    order.value = orderData
    if (existed) {
      ElMessage.warning('该订单已评价')
      await router.replace('/orders')
    }
  } catch {
    if (seq === loadSeq) loadError.value = '订单信息加载失败，请重试。'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

onMounted(loadOrder)
watch(orderId, () => {
  form.score = 5
  form.tags = []
  form.content = ''
  void loadOrder()
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">评价订单</h1>
        <p class="page-subtitle">对本次服务进行评价，帮助其他玩家选择</p>
      </div>

      <template v-if="order">
        <!-- 订单信息 -->
        <div class="card mb-2">
          <div class="card-body">
            <div class="order-body">
              <div class="order-thumb">🎮</div>
              <div class="order-info">
                <div class="order-title">{{ order.serviceTitleSnapshot }}</div>
                <div class="order-meta">
                  {{ fmtTime(order.appointmentStartAt) }} · {{ order.durationMinutes }} 分钟
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 评价表单 -->
        <div class="card">
          <div class="card-body">
            <div class="form-group">
              <label class="form-label">总体评分</label>
              <div class="star-rating">
                <button v-for="i in 5" :key="i" type="button" class="star" :class="{ on: form.score >= i }"
                  @click="form.score = i">★</button>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">评价标签</label>
              <div class="checkbox-group">
                <div v-for="t in TAG_OPTIONS" :key="t" class="checkbox-pill">
                  <input type="checkbox" :id="'tag-' + t" :value="t" v-model="form.tags" />
                  <label :for="'tag-' + t">{{ t }}</label>
                </div>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">评价内容</label>
              <textarea v-model="form.content" class="form-control" rows="4" maxlength="1000"
                placeholder="说说这次陪玩体验吧（1~1000字）"></textarea>
            </div>
            <div class="flex gap-1">
              <button class="btn btn-primary" :disabled="submitting" @click="handleSubmit">
                {{ submitting ? '提交中…' : '提交评价' }}
              </button>
              <button class="btn btn-ghost" @click="router.push('/orders')">返回订单列表</button>
            </div>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>{{ loadError || '订单不存在或当前账号无权评价' }}</p>
        <button v-if="loadError.includes('重试')" class="btn btn-outline mt-2" @click="loading = true; loadOrder()">重试</button>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
    </div>
  </div>
</template>
