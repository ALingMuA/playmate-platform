<script setup lang="ts">
/**
 * 游客可访问的陪玩师公开主页（侧栏店铺式布局）。
 *
 * <p>顶部紧凑身份横条 + 左侧主内容流（服务/档期/评价，服务卡直达预约）
 * + 右侧 sticky 侧栏（预约卡/能力认证/关于我）。数据编排与竞态防护在本页，
 * 展示细节下沉到 components/companion 子组件。</p>
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/http'
import {
  getPublicCompanionProfile,
  listPublicAvailabilities,
  type PublicAvailability,
  type PublicCompanionProfile,
} from '@/api/companion'
import { listTags, type TagView } from '@/api/catalog'
import { listBookableServices, type BookableService } from '@/api/order'
import { companionReviews, type Review } from '@/api/review'
import { useFavorite } from '@/composables/useFavorite'
import { useUserStore } from '@/stores/user'
import CompanionHeroBar from '@/components/companion/CompanionHeroBar.vue'
import CompanionServiceList from '@/components/companion/CompanionServiceList.vue'
import CompanionAvailabilityCard from '@/components/companion/CompanionAvailabilityCard.vue'
import CompanionReviewSection from '@/components/companion/CompanionReviewSection.vue'
import CompanionSidebar from '@/components/companion/CompanionSidebar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const companionUserId = computed(() => Number(route.params.userId))
const isOwner = computed(() => userStore.user?.id === companionUserId.value)
const {
  isFavorited,
  loading: favoriteLoading,
  saving: favoriteSaving,
  refresh: refreshFavoriteStatus,
  toggle: toggleFavoriteState,
} = useFavorite(companionUserId)
const profile = ref<PublicCompanionProfile | null>(null)
const profileLoading = ref(true)
const profileUnavailable = ref(false)
const profileError = ref('')
const capabilityTags = ref<Record<number, TagView[]>>({})

const services = ref<BookableService[]>([])
const servicesTotal = ref(0)
const servicesPage = ref(1)
const servicesPageSize = 6
const servicesLoading = ref(false)
const servicesError = ref('')

const reviews = ref<Review[]>([])
const reviewsTotal = ref(0)
const reviewsPage = ref(1)
const reviewsPageSize = 10
const reviewsLoading = ref(false)
const reviewsError = ref('')
const availabilities = ref<PublicAvailability[]>([])
const availabilitiesLoading = ref(false)
const availabilitiesError = ref('')

let routeRequestId = 0
let servicesRequestId = 0
let reviewsRequestId = 0

/** 侧栏与移动端吸底条的默认预约服务（第一个在架服务） */
const defaultService = computed(() => services.value[0] ?? null)

const serviceAvailable = computed(() => profile.value?.serviceStatus === 'AVAILABLE')

const serviceStatusText = computed(() => {
  const labels: Record<PublicCompanionProfile['serviceStatus'], string> = {
    AVAILABLE: '可接单',
    BUSY: '当前忙碌',
    RESTING: '休息中',
    SUSPENDED: '暂停接单',
  }
  return profile.value ? labels[profile.value.serviceStatus] : '状态未知'
})

/** 评分三级降级：平台汇总 → 当前加载样本 → 暂无 */
const ratingValue = computed(() => {
  if (profile.value?.ratingAvg != null && profile.value.ratingCount > 0) {
    return profile.value.ratingAvg.toFixed(1)
  }
  if (!reviews.value.length) return null
  const sum = reviews.value.reduce((total, review) => total + review.score, 0)
  return (sum / reviews.value.length).toFixed(1)
})

const ratingScope = computed(() => {
  if (profile.value?.ratingAvg != null && profile.value.ratingCount > 0) {
    return `平台汇总 · ${profile.value.ratingCount} 条评价`
  }
  if (profile.value?.ratingCount === 0) return '暂无已发布评价'
  if (reviews.value.length) return `当前加载 ${reviews.value.length} 条评价样本`
  return profile.value?.ratingCount ? `${profile.value.ratingCount} 条评价` : '暂无评分数据'
})

function toggleFavorite() {
  void toggleFavoriteState().catch(() => undefined)
}

// ==================== 预约动线（一步直达预约页） ====================

function goBookService(service: BookableService) {
  if (!serviceAvailable.value) return
  router.push({
    path: '/booking',
    query: { companionUserId: String(service.companionUserId), serviceId: String(service.id) },
  })
}

function goBookingAtTime(startAt: string) {
  router.push({
    path: '/booking',
    query: { companionUserId: String(companionUserId.value), startAt },
  })
}

function goDefaultBooking() {
  if (!serviceAvailable.value || !defaultService.value) return
  router.push({
    path: '/booking',
    query: { companionUserId: String(companionUserId.value), serviceId: String(defaultService.value.id) },
  })
}

// ==================== 数据加载（含竞态防护） ====================

function loadAvailabilities(expectedRouteRequest = routeRequestId) {
  if (profile.value?.serviceStatus !== 'AVAILABLE') return Promise.resolve()
  const userId = companionUserId.value
  availabilitiesLoading.value = true
  availabilitiesError.value = ''
  return listPublicAvailabilities(userId, 7)
    .then((result) => {
      if (expectedRouteRequest === routeRequestId) availabilities.value = result
    })
    .catch(() => {
      if (expectedRouteRequest === routeRequestId) availabilitiesError.value = '可约时间暂时无法加载，请稍后重试。'
    })
    .finally(() => {
      if (expectedRouteRequest === routeRequestId) availabilitiesLoading.value = false
    })
}

function profileErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    profileUnavailable.value = true
    return ''
  }
  return '主页资料暂时无法加载，请稍后重试。'
}

async function loadServices(page = 1, expectedRouteRequest = routeRequestId) {
  const requestId = ++servicesRequestId
  const userId = companionUserId.value
  servicesPage.value = page
  servicesLoading.value = true
  servicesError.value = ''
  try {
    const result = await listBookableServices(undefined, userId, page, servicesPageSize)
    if (expectedRouteRequest !== routeRequestId || requestId !== servicesRequestId) return
    services.value = result.records
    servicesTotal.value = result.total
    servicesPage.value = result.current || page
  } catch {
    if (expectedRouteRequest !== routeRequestId || requestId !== servicesRequestId) return
    servicesError.value = '服务列表加载失败，请重试。'
  } finally {
    if (expectedRouteRequest === routeRequestId && requestId === servicesRequestId) {
      servicesLoading.value = false
    }
  }
}

async function loadReviews(page = 1, expectedRouteRequest = routeRequestId) {
  const requestId = ++reviewsRequestId
  const userId = companionUserId.value
  reviewsPage.value = page
  reviewsLoading.value = true
  reviewsError.value = ''
  try {
    const result = await companionReviews(userId, page, reviewsPageSize)
    if (expectedRouteRequest !== routeRequestId || requestId !== reviewsRequestId) return
    reviews.value = result.records
    reviewsTotal.value = result.total
    reviewsPage.value = result.current || page
  } catch {
    if (expectedRouteRequest !== routeRequestId || requestId !== reviewsRequestId) return
    reviewsError.value = '评价列表加载失败，请重试。'
  } finally {
    if (expectedRouteRequest === routeRequestId && requestId === reviewsRequestId) {
      reviewsLoading.value = false
    }
  }
}

async function loadPage(rawUserId: string | string[]) {
  const requestId = ++routeRequestId
  const userId = Number(Array.isArray(rawUserId) ? rawUserId[0] : rawUserId)
  profile.value = null
  profileUnavailable.value = false
  profileError.value = ''
  capabilityTags.value = {}
  services.value = []
  servicesTotal.value = 0
  servicesPage.value = 1
  servicesLoading.value = false
  servicesError.value = ''
  reviews.value = []
  reviewsTotal.value = 0
  reviewsPage.value = 1
  reviewsLoading.value = false
  reviewsError.value = ''
  availabilities.value = []
  availabilitiesLoading.value = false
  availabilitiesError.value = ''

  if (!Number.isSafeInteger(userId) || userId <= 0) {
    profileLoading.value = false
    profileError.value = '陪玩师编号无效。'
    return
  }

  profileLoading.value = true
  try {
    const [publicProfile] = await Promise.all([
      getPublicCompanionProfile(userId),
      userStore.isLoggedIn && !userStore.user ? userStore.refreshUser().catch(() => null) : Promise.resolve(null),
    ])
    if (requestId !== routeRequestId) return
    profile.value = publicProfile
    profileLoading.value = false
    const favoriteStatus = userStore.isLoggedIn ? refreshFavoriteStatus().catch(() => undefined) : Promise.resolve()
    const gameIds = [...new Set(publicProfile.capabilities.map((capability) => capability.gameId))]
    const capabilityRequests = Promise.all(gameIds.map(async (gameId) => {
      const tags = await listTags(gameId).catch(() => [])
      if (requestId === routeRequestId) capabilityTags.value[gameId] = tags
    }))
    await Promise.all([
      loadServices(1, requestId),
      loadReviews(1, requestId),
      loadAvailabilities(requestId),
      favoriteStatus,
      capabilityRequests,
    ])
  } catch (error) {
    if (requestId !== routeRequestId) return
    profileError.value = profileErrorMessage(error)
  } finally {
    if (requestId === routeRequestId) profileLoading.value = false
  }
}

watch(() => route.params.userId, (value) => {
  void loadPage(value as string | string[])
}, { immediate: true })
</script>

<template>
  <main class="page companion-shop-page">
    <div class="container">
      <section v-if="profileLoading" class="state-card" aria-live="polite">
        <el-skeleton :rows="5" animated />
      </section>

      <section v-else-if="profileError || profileUnavailable" class="state-card" role="status">
        <div class="state-icon" aria-hidden="true">{{ profileUnavailable ? '⌕' : '!' }}</div>
        <h1>{{ profileUnavailable ? '主页暂不可见' : '暂时无法打开主页' }}</h1>
        <p>
          {{ profileUnavailable ? '该陪玩师资料不存在或当前不可公开。' : profileError }}
        </p>
        <button v-if="!profileUnavailable && profileError !== '陪玩师编号无效。'" class="btn btn-primary" @click="loadPage(route.params.userId as string | string[])">
          重试
        </button>
        <router-link class="btn btn-outline" to="/companions">返回找陪玩</router-link>
      </section>

      <template v-else-if="profile">
        <CompanionHeroBar
          :profile="profile"
          :services-total="servicesTotal"
          :services-loading="servicesLoading"
          :is-owner="isOwner"
          :is-favorited="isFavorited"
          :favorite-loading="favoriteLoading"
          :favorite-saving="favoriteSaving"
          :rating-value="ratingValue"
          @toggle-favorite="toggleFavorite"
        />

        <div class="shop-layout">
          <div class="shop-main">
            <CompanionServiceList
              :services="services"
              :total="servicesTotal"
              :page="servicesPage"
              :page-size="servicesPageSize"
              :loading="servicesLoading"
              :error="servicesError"
              :bookable="serviceAvailable"
              :status-text="serviceStatusText"
              @load="loadServices"
              @book="goBookService"
            />
            <CompanionAvailabilityCard
              :slots="availabilities"
              :loading="availabilitiesLoading"
              :error="availabilitiesError"
              :service-available="serviceAvailable"
              :status-text="serviceStatusText"
              @load="loadAvailabilities()"
              @select="goBookingAtTime"
            />
            <CompanionReviewSection
              :reviews="reviews"
              :total="reviewsTotal"
              :page="reviewsPage"
              :page-size="reviewsPageSize"
              :loading="reviewsLoading"
              :error="reviewsError"
              :rating-value="ratingValue"
              :rating-scope="ratingScope"
              :rating-count="profile.ratingCount"
              @load="loadReviews"
            />
          </div>

          <aside class="shop-side" aria-label="预约与陪玩师资料">
            <CompanionSidebar
              :profile="profile"
              :capability-tags="capabilityTags"
              :default-service="defaultService"
              :services-loading="servicesLoading"
              @book="goDefaultBooking"
            />
          </aside>
        </div>

        <!-- 移动端吸底预约条 -->
        <div class="mobile-booking-bar" :aria-hidden="true">
          <div class="mobile-booking-info">
            <strong>{{ profile.displayName || '陪玩师' }}</strong>
            <span>{{ serviceStatusText }}</span>
          </div>
          <button type="button" class="btn btn-primary" :disabled="!serviceAvailable || !defaultService" @click="goDefaultBooking">
            立即预约
          </button>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.companion-shop-page { min-height: 60vh; }

.shop-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; margin-top: 16px; }
.shop-main { display: grid; gap: 16px; min-width: 0; }
.shop-side { position: sticky; top: calc(var(--header-height) + 16px); min-width: 0; }

.state-card { display: grid; gap: 12px; justify-items: center; padding: 56px 20px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--card); text-align: center; }
.state-icon { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 50%; background: var(--muted); color: var(--muted-foreground); font-size: 20px; }
.state-card h1 { margin: 0; font-size: 18px; }
.state-card p { margin: 0; color: var(--muted-foreground); }
.state-card .btn { margin-top: 6px; }

.mobile-booking-bar { display: none; }

@media (max-width: 768px) {
  .companion-shop-page { padding-bottom: 72px; }
  .shop-layout { grid-template-columns: minmax(0, 1fr); }
  .shop-side { position: static; order: -1; }
  .mobile-booking-bar {
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--border); background: var(--card); box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.08);
  }
  .mobile-booking-info { display: grid; gap: 2px; min-width: 0; }
  .mobile-booking-info strong { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .mobile-booking-info span { font-size: 12px; color: var(--muted-foreground); }
}
</style>
