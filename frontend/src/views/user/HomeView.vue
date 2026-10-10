<script setup lang="ts">
/**
 * 首页（活动展示 + 公告 + 推荐陪玩师）。
 *
 * <p>游客可访问；数据来自公告公开接口与可预约服务接口。</p>
 */
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'
import { listBookableServices, type BookableService } from '@/api/order'
import { publicActivities, type ActivityView } from '@/api/activity'
import { claimCoupon } from '@/api/coupon'

const router = useRouter()
const userStore = useUserStore()

/** 平台公告（仅已发布，FR-A08） */
const announcements = ref<AnnouncementView[]>([])

/** 推荐可预约服务 */
const recommended = ref<BookableService[]>([])

/** 活动展示数据 */
const defaultActivities: ActivityView[] = [
  {
    id: 1,
    title: '新人专享福利',
    subtitle: '首单立减优惠券免费领',
    description: '注册即送新人优惠券，首次预约陪玩服务享专属优惠，大神陪你畅快开黑！',
    bannerImage: '',
    bgGradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    icon: '🎁',
    tag: '限时活动',
    tagColor: '#ff6b6b',
    actionType: 'COMPANION_LIST',
    actionRoute: '/companions',
    sortOrder: 1,
    status: 'PUBLISHED',
    createdBy: 1,
    createdAt: '',
  },
  {
    id: 2,
    title: '周末狂欢赛',
    subtitle: '组队开黑赢大奖',
    description: '每周六日参与组队活动，完成指定游戏任务即可赢取积分奖励和限定称号！',
    bannerImage: '',
    bgGradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    icon: '🏆',
    tag: '每周活动',
    tagColor: '#ffa726',
    actionType: 'PAGE_NAV',
    actionRoute: '/activities',
    sortOrder: 2,
    status: 'PUBLISHED',
    createdBy: 1,
    createdAt: '',
  },
]

const activities = ref<ActivityView[]>(defaultActivities)
const claimingCouponId = ref<number | null>(null)

/** 轮播控制 */
const currentSlide = ref(0)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

const totalSlides = computed(() => activities.value.length || 1)

function goToSlide(index: number) {
  currentSlide.value = index
  resetAutoPlay()
}

function nextSlide() {
  if (totalSlides.value > 0) {
    currentSlide.value = (currentSlide.value + 1) % totalSlides.value
  }
}

function prevSlide() {
  if (totalSlides.value > 0) {
    currentSlide.value = (currentSlide.value - 1 + totalSlides.value) % totalSlides.value
  }
  resetAutoPlay()
}

function startAutoPlay() {
  autoPlayTimer = setInterval(() => {
    nextSlide()
  }, 5000)
}

function resetAutoPlay() {
  if (autoPlayTimer) clearInterval(autoPlayTimer)
  startAutoPlay()
}

function getSlideActionText(item: ActivityView): string {
  if (item.actionType === 'COUPON_CLAIM') return '立即领券'
  if (item.actionType === 'COMPANION_LIST') return '挑选陪玩'
  return '查看详情'
}

async function handleSlideAction(item: ActivityView) {
  if (item.actionType === 'COUPON_CLAIM') {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录后再领取优惠券')
      router.push(`/login?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`)
      return
    }
    if (!item.couponTemplateId) {
      ElMessage.warning('活动未绑定优惠券')
      return
    }
    claimingCouponId.value = item.couponTemplateId
    try {
      await claimCoupon(item.couponTemplateId)
      ElMessage.success('优惠券领取成功！快去下单使用吧')
    } catch {
      // 全局拦截器处理
    } finally {
      claimingCouponId.value = null
    }
    return
  }
  if (item.actionType === 'COMPANION_LIST') {
    router.push('/companions')
    return
  }
  if (item.actionRoute) {
    if (item.actionRoute.startsWith('http')) {
      window.open(item.actionRoute, '_blank')
    } else {
      router.push(item.actionRoute)
    }
    return
  }
  router.push(`/activities/${item.id}`)
}

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

onMounted(async () => {
  startAutoPlay()
  try {
    const actList = await publicActivities()
    if (actList && actList.length > 0) {
      activities.value = actList
    }
  } catch {
    // 降级使用默认活动
  }
  try {
    const result = await publicAnnouncements(1, 5)
    announcements.value = result.records
  } catch {
    announcements.value = []
  }
  try {
    const result = await listBookableServices(undefined, undefined, 1, 6)
    recommended.value = result.records
  } catch {
    recommended.value = []
  }
})

onUnmounted(() => {
  if (autoPlayTimer) clearInterval(autoPlayTimer)
})
</script>

<template>
  <div class="home">
    <!-- 活动展示轮播 -->
    <section class="activity-showcase">
      <div class="container">
        <div class="carousel-wrapper">
          <!-- 轮播主体 -->
          <div class="carousel-viewport">
            <div
              v-for="(item, index) in activities"
              :key="item.id"
              class="carousel-slide"
              :class="{ active: currentSlide === index }"
              :style="{ background: item.bgGradient }"
            >
              <div class="slide-content">
                <div class="slide-text">
                  <span class="slide-tag" :style="{ background: item.tagColor }">{{ item.tag }}</span>
                  <h2 class="slide-title" style="cursor: pointer" @click="router.push(`/activities/${item.id}`)">{{ item.title }}</h2>
                  <p class="slide-subtitle">{{ item.subtitle }}</p>
                  <p class="slide-desc">{{ item.description }}</p>
                  <button class="btn btn-lg slide-btn" :disabled="claimingCouponId === item.couponTemplateId" @click="handleSlideAction(item)">
                    {{ claimingCouponId === item.couponTemplateId ? '领取中...' : getSlideActionText(item) }}
                  </button>
                </div>
                <div class="slide-icon">{{ item.icon }}</div>
              </div>
            </div>
          </div>
          <!-- 左右箭头 -->
          <button class="carousel-arrow carousel-arrow-left" @click="prevSlide">‹</button>
          <button class="carousel-arrow carousel-arrow-right" @click="nextSlide(); resetAutoPlay()">›</button>
          <!-- 指示器 -->
          <div class="carousel-indicators">
            <button
              v-for="(_, index) in activities"
              :key="index"
              class="indicator-dot"
              :class="{ active: currentSlide === index }"
              @click="goToSlide(index)"
            />
          </div>
        </div>
      </div>
    </section>

    <div class="container">
      <!-- 平台公告（FR-A08） -->
      <section v-if="announcements.length" class="section" style="padding-bottom: 0">
        <div class="card">
          <div class="card-body">
            <div class="card-title mb-2" style="display: flex; justify-content: space-between; align-items: center;">
              <span>📢 平台公告</span>
              <span style="font-size: 0.875rem; cursor: pointer; color: var(--primary); font-weight: normal;" @click="router.push('/announcements')">更多公告 →</span>
            </div>
            <div v-for="a in announcements" :key="a.id" class="announce-item">
              <div class="announce-title-row" @click="router.push(`/announcements/${a.id}`)">
                <span class="announce-title">{{ a.title }}</span>
                <span class="announce-time">{{ fmtTime(a.publishedAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 推荐陪玩师 -->
      <section class="section" v-if="recommended.length">
        <div class="section-header">
          <h2 class="section-title">热门陪玩服务</h2>
          <span class="section-link" @click="router.push('/companions')">查看全部 →</span>
        </div>
        <div class="companion-grid">
          <div v-for="s in recommended" :key="s.id" class="companion-card"
            @click="router.push({ path: '/booking', query: { companionUserId: String(s.companionUserId), serviceId: String(s.id) } })">
            <div class="companion-header">
              <div class="companion-avatar">🎮</div>
              <div class="companion-meta">
                <div class="companion-name">{{ s.title }}</div>
                <div class="companion-level">{{ s.gameName }} · {{ s.serviceTypeName }}</div>
                <div class="companion-rating">
                  <span class="text-destructive" style="color: #f59e0b">★</span>
                  <span>{{ fmtPrice(s.priceCents) }}/小时</span>
                </div>
              </div>
            </div>
            <div class="companion-tags" v-if="s.tagNames?.length">
              <span v-for="t in s.tagNames" :key="t" class="tag">{{ t }}</span>
            </div>
            <div class="companion-footer">
              <div class="companion-price">{{ fmtPrice(s.priceCents) }}<span>/小时</span></div>
              <button class="btn btn-primary btn-sm">立即预约</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ===== 活动展示轮播 ===== */
.activity-showcase {
  padding: 32px 0 0;
}

.carousel-wrapper {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.carousel-viewport {
  position: relative;
  height: 320px;
}

.carousel-slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 0.6s ease;
  pointer-events: none;
}

.carousel-slide.active {
  opacity: 1;
  pointer-events: auto;
}

.slide-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 48px 56px;
  color: #fff;
}

.slide-text {
  max-width: 520px;
}

.slide-tag {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}

.slide-title {
  font-size: 2.25rem;
  font-weight: 800;
  margin-top: 16px;
  line-height: 1.2;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.slide-subtitle {
  font-size: 1.375rem;
  font-weight: 600;
  margin-top: 8px;
  opacity: 0.95;
}

.slide-desc {
  font-size: 0.9375rem;
  margin-top: 12px;
  opacity: 0.85;
  line-height: 1.6;
  max-width: 420px;
}

.slide-btn {
  margin-top: 24px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border: 2px solid rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(4px);
  font-weight: 700;
  transition: background 0.2s, border-color 0.2s, transform 0.15s;
}

.slide-btn:hover {
  background: rgba(255, 255, 255, 0.35);
  border-color: #fff;
  transform: translateY(-1px);
}

.slide-icon {
  font-size: 120px;
  line-height: 1;
  opacity: 0.3;
  flex-shrink: 0;
  user-select: none;
}

/* 箭头 */
.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  font-size: 24px;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: background 0.2s;
  z-index: 2;
}

.carousel-arrow:hover {
  background: rgba(255, 255, 255, 0.35);
}

.carousel-arrow-left {
  left: 16px;
}

.carousel-arrow-right {
  right: 16px;
}

/* 指示器 */
.carousel-indicators {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 2;
}

.indicator-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.6);
  background: transparent;
  cursor: pointer;
  padding: 0;
  transition: background 0.2s, border-color 0.2s, transform 0.2s;
}

.indicator-dot.active {
  background: #fff;
  border-color: #fff;
  transform: scale(1.2);
}

.indicator-dot:hover:not(.active) {
  background: rgba(255, 255, 255, 0.4);
}

/* ===== 公告 ===== */
.announce-item {
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
}
.announce-item:last-child {
  border-bottom: none;
}
.announce-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  gap: 12px;
}
.announce-title {
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--foreground);
}
.announce-title:hover {
  color: var(--primary);
}
.announce-time {
  font-size: 0.75rem;
  color: var(--muted-foreground);
  flex-shrink: 0;
}
.announce-content {
  margin-top: 6px;
  font-size: 0.875rem;
  color: var(--muted-foreground);
  white-space: pre-wrap;
  background: var(--muted);
  padding: 8px 10px;
  border-radius: var(--radius);
}
</style>
