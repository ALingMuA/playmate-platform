<script setup lang="ts">
/** 游客可访问的陪玩师公开主页，展示资料、可预约服务和公开评价。 */
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

type PublicTab = 'home' | 'services' | 'reviews'

const tabs: { key: PublicTab; label: string }[] = [
  { key: 'home', label: '主页' },
  { key: 'services', label: '服务' },
  { key: 'reviews', label: '评价' },
]

function tabFromHash(hash: string): PublicTab {
  const value = hash.match(/(?:^#|&)tab=([^&]+)/)?.[1]
  return value === 'services' || value === 'reviews' ? value : 'home'
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeTab = ref<PublicTab>(tabFromHash(route.hash))

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
const avatarFailed = ref(false)
const capabilityTags = ref<Record<number, TagView[]>>({})

const services = ref<BookableService[]>([])
const servicesTotal = ref(0)
const servicesPage = ref(1)
const servicesPageSize = 6
const servicesLoading = ref(false)
const servicesError = ref('')
const selectedService = ref<BookableService | null>(null)

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

const sampleDistribution = computed(() => {
  const counts = [0, 0, 0, 0, 0]
  reviews.value.forEach((review) => {
    if (review.score >= 1 && review.score <= 5) counts[review.score - 1] += 1
  })
  return counts.map((count, index) => ({ score: index + 1, count }))
})

const selectedServiceLabel = computed(() => {
  if (!selectedService.value) return '选择一项服务后即可预约'
  return `${selectedService.value.title} · ${fmtPrice(selectedService.value.priceCents)}`
})

const canBook = computed(() =>
  profile.value?.serviceStatus === 'AVAILABLE' && servicesTotal.value > 0 && !!selectedService.value,
)

function serviceStatusText(status?: PublicCompanionProfile['serviceStatus']): string {
  const labels: Record<PublicCompanionProfile['serviceStatus'], string> = {
    AVAILABLE: '可接单',
    BUSY: '当前忙碌',
    RESTING: '休息中',
    SUSPENDED: '暂停接单',
  }
  return status ? labels[status] : '状态未知'
}

function certificationText(status?: PublicCompanionProfile['certificationStatus']): string {
  if (status === 'APPROVED') return '平台资格已通过'
  if (status === 'SUSPENDED') return '平台资格已暂停'
  return ''
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

function fmtTime(value?: string): string {
  if (!value) return ''
  return value.length >= 10 ? value.slice(0, 10) : value
}

function selectTab(tab: PublicTab) {
  activeTab.value = tab
  const hash = `#tab=${tab}`
  if (route.hash !== hash) void router.replace({ hash })
}

function moveTab(offset: number) {
  const currentIndex = tabs.findIndex((tab) => tab.key === activeTab.value)
  const nextIndex = (currentIndex + offset + tabs.length) % tabs.length
  selectTab(tabs[nextIndex].key)
  document.getElementById(`companion-tab-${tabs[nextIndex].key}`)?.focus()
}

function selectService(service: BookableService) {
  selectedService.value = service
}

function goBooking() {
  const service = selectedService.value
  if (!service || !canBook.value) return
  router.push({
    path: '/booking',
    query: { companionUserId: String(service.companionUserId), serviceId: String(service.id) },
  })
}

function goBookingAt(startAt?: string) {
  const service = selectedService.value
  if (!service || !canBook.value) {
    selectTab('services')
    return
  }
  router.push({
    path: '/booking',
    query: {
      companionUserId: String(service.companionUserId),
      serviceId: String(service.id),
      ...(startAt ? { startAt } : {}),
    },
  })
}

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

function toggleFavorite() {
  void toggleFavoriteState().catch(() => undefined)
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
    if (!selectedService.value && result.records.length) selectedService.value = result.records[0]
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
  avatarFailed.value = false
  capabilityTags.value = {}
  services.value = []
  servicesTotal.value = 0
  servicesPage.value = 1
  servicesLoading.value = false
  servicesError.value = ''
  selectedService.value = null
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

watch(() => route.hash, (hash) => {
  activeTab.value = tabFromHash(hash)
})

watch(() => route.params.userId, (value) => {
  void loadPage(value as string | string[])
}, { immediate: true })
</script>

<template>
  <main class="page companion-page">
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
        <section class="profile-hero" aria-labelledby="companion-name">
          <div class="hero-main">
            <div class="avatar-wrap">
              <img
                v-if="profile.avatarUrl && !avatarFailed"
                :src="profile.avatarUrl"
                :alt="`${profile.displayName}的头像`"
                class="profile-avatar"
                @error="avatarFailed = true"
              >
              <div v-else class="profile-avatar avatar-fallback" aria-hidden="true">
                {{ profile.displayName?.trim().charAt(0) || '陪' }}
              </div>
            </div>

            <div class="hero-copy">
              <div class="eyebrow">陪玩师主页</div>
              <h1 id="companion-name">{{ profile.displayName || '陪玩师' }}</h1>
              <div class="hero-badges">
                <span class="status-pill" :class="`status-${profile.serviceStatus.toLowerCase()}`">
                  <i aria-hidden="true" />{{ serviceStatusText(profile.serviceStatus) }}
                </span>
                <span v-if="certificationText(profile.certificationStatus)" class="qualification-pill">
                  {{ certificationText(profile.certificationStatus) }}
                </span>
              </div>
              <p v-if="profile.profileIntro" class="hero-intro">{{ profile.profileIntro }}</p>
              <p v-else class="hero-intro intro-muted">还没有填写主页介绍。</p>
              <div class="hero-actions">
                <button v-if="isOwner" type="button" class="btn btn-primary" @click="router.push('/companion/profile')">
                  编辑主页
                </button>
                <button
                  type="button"
                  class="btn btn-outline"
                  :disabled="favoriteLoading || favoriteSaving"
                  :aria-pressed="isFavorited"
                  @click="toggleFavorite"
                >
                  {{ favoriteSaving ? '处理中…' : isFavorited ? '已关注' : '关注' }}
                </button>
              </div>
            </div>
          </div>

          <div class="hero-stats" aria-label="陪玩师公开统计">
            <div class="stat-item">
              <span class="stat-label">评分</span>
              <strong>{{ ratingValue ?? '—' }}<small v-if="ratingValue"> / 5</small></strong>
              <span class="stat-note">{{ ratingScope }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">完成订单</span>
              <strong>{{ profile.completedOrderCount }}</strong>
              <span class="stat-note">已完成</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">在架服务</span>
              <strong>{{ servicesLoading && !servicesTotal ? '…' : servicesTotal }}</strong>
              <span class="stat-note">公开可预约服务</span>
            </div>
            <div class="stat-item stat-price">
              <span class="stat-label">服务起价</span>
              <strong>{{ profile.startingPriceCents != null ? fmtPrice(profile.startingPriceCents) : '—' }}</strong>
              <span class="stat-note">每小时</span>
            </div>
          </div>
        </section>

        <div class="page-toolbar">
          <div class="tab-list" role="tablist" aria-label="陪玩师主页内容">
            <button
              v-for="tab in tabs"
              :id="`companion-tab-${tab.key}`"
              :key="tab.key"
              type="button"
              role="tab"
              :aria-selected="activeTab === tab.key"
              :aria-controls="`companion-panel-${tab.key}`"
              :tabindex="activeTab === tab.key ? 0 : -1"
              class="tab-button"
              :class="{ active: activeTab === tab.key }"
              @click="selectTab(tab.key)"
              @keydown.right.prevent="moveTab(1)"
              @keydown.left.prevent="moveTab(-1)"
            >
              {{ tab.label }}
              <span v-if="tab.key === 'services' && !servicesLoading" class="tab-count">{{ servicesTotal }}</span>
              <span v-if="tab.key === 'reviews' && profile.ratingCount" class="tab-count">{{ profile.ratingCount }}</span>
            </button>
          </div>
          <div class="toolbar-booking">
            <span class="selected-service-label">{{ selectedServiceLabel }}</span>
            <button class="btn btn-primary" :disabled="!canBook" @click="goBooking">
              {{ profile.serviceStatus === 'AVAILABLE' ? '立即预约' : serviceStatusText(profile.serviceStatus) }}
            </button>
          </div>
        </div>

        <section
          v-show="activeTab === 'home'"
          id="companion-panel-home"
          role="tabpanel"
          aria-labelledby="companion-tab-home"
          class="content-panel"
        >
          <div class="home-grid">
            <div class="content-card intro-card">
              <div class="section-heading">
                <div>
                  <span class="section-kicker">ABOUT</span>
                  <h2>关于我</h2>
                </div>
              </div>
              <p v-if="profile.profileIntro" class="intro-body">{{ profile.profileIntro }}</p>
              <div v-else class="empty-inline">
                <span class="empty-mark" aria-hidden="true">✦</span>
                <p>陪玩师还没有填写个人介绍。</p>
              </div>
            </div>

            <div class="content-card capability-card">
              <div class="section-heading">
                <div>
                  <span class="section-kicker">CAPABILITIES</span>
                  <h2>游戏能力</h2>
                </div>
                <span class="section-count">{{ profile.capabilities?.length ?? 0 }} 项</span>
              </div>
              <div v-if="profile.capabilities?.length" class="capability-list">
                <article v-for="(capability, index) in profile.capabilities" :key="`${capability.gameId}-${index}`" class="capability-item">
                  <div class="capability-icon" aria-hidden="true">{{ capability.gameName?.trim().charAt(0) || '游' }}</div>
                  <div class="capability-copy">
                    <h3>{{ capability.gameName || `游戏 #${capability.gameId}` }}</h3>
                    <p>
                      <span v-if="capability.server">{{ capability.server }}</span>
                      <span v-if="capability.server && capability.rank"> · </span>
                      <span v-if="capability.rank">{{ capability.rank }}</span>
                      <span v-if="!capability.server && !capability.rank">能力信息待补充</span>
                    </p>
                    <div v-if="capability.positionTagIds?.length" class="capability-tags">
                      <span v-for="tagId in capability.positionTagIds" :key="tagId" class="service-tag">
                        {{ capabilityTags[capability.gameId]?.find((tag) => tag.id === tagId)?.tagName ?? '位置标签' }}
                      </span>
                    </div>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">
                <span class="empty-mark" aria-hidden="true">✦</span>
                <p>暂未公开游戏能力资料。</p>
              </div>
              <p class="capability-disclaimer">平台资格状态不代表每项游戏能力均已单独核验。</p>
            </div>
          </div>

          <div class="content-card home-services">
            <div class="section-heading">
              <div>
                <span class="section-kicker">SERVICES</span>
                <h2>可预约服务</h2>
              </div>
              <button type="button" class="text-action" @click="selectTab('services')">查看全部 <span aria-hidden="true">→</span></button>
            </div>
            <div v-if="servicesLoading && !services.length" class="inline-loading"><el-skeleton :rows="2" animated /></div>
            <div v-else-if="servicesError" class="inline-error" role="status">
              <span>{{ servicesError }}</span>
              <button type="button" class="text-action" @click="loadServices(servicesPage)">重试</button>
            </div>
            <div v-else-if="services.length" class="service-preview-grid">
              <button
                v-for="service in services.slice(0, 3)"
                :key="service.id"
                type="button"
                class="service-preview"
                @click="selectService(service); selectTab('services')"
              >
                <span class="service-game">{{ service.gameName }} · {{ service.serviceTypeName }}</span>
                <strong>{{ service.title }}</strong>
                <span class="service-preview-bottom">
                  <span>{{ service.minDurationMinutes }} 分钟起</span>
                  <b>{{ fmtPrice(service.priceCents) }}</b>
                </span>
              </button>
            </div>
            <div v-else class="empty-inline">
              <span class="empty-mark" aria-hidden="true">✦</span>
              <p>目前没有公开的可预约服务，主页资料仍可浏览。</p>
            </div>
          </div>

          <div class="content-card schedule-card">
            <div class="section-heading">
              <div>
                <span class="section-kicker">NEXT 7 DAYS</span>
                <h2>未来七日可约时间</h2>
              </div>
              <span v-if="profile.serviceStatus !== 'AVAILABLE'" class="section-count">
                {{ serviceStatusText(profile.serviceStatus) }}
              </span>
            </div>
            <div v-if="profile.serviceStatus !== 'AVAILABLE'" class="empty-inline">
              <span class="empty-mark" aria-hidden="true">✦</span>
              <p>当前状态下暂不展示可约时间。</p>
            </div>
            <div v-else-if="availabilitiesLoading" class="inline-loading"><el-skeleton :rows="2" animated /></div>
            <div v-else-if="availabilitiesError" class="inline-error" role="status">
              <span>{{ availabilitiesError }}</span>
              <button type="button" class="text-action" @click="loadAvailabilities()">重试</button>
            </div>
            <div v-else-if="availabilities.length" class="availability-list">
              <button v-for="slot in availabilities" :key="slot.startAt" type="button" class="availability-slot"
                @click="goBookingAt(slot.startAt)">
                <span class="availability-date">{{ slot.startAt.slice(0, 10) }}</span>
                <strong>{{ slot.startAt.slice(11, 16) }} - {{ slot.endAt.slice(11, 16) }}</strong>
                <span class="availability-action">选择时间</span>
              </button>
            </div>
            <div v-else class="empty-inline">
              <span class="empty-mark" aria-hidden="true">✦</span>
              <p>未来七日暂未设置可约时间。</p>
            </div>
          </div>
        </section>

        <section
          v-show="activeTab === 'services'"
          id="companion-panel-services"
          role="tabpanel"
          aria-labelledby="companion-tab-services"
          class="content-panel"
        >
          <div class="content-card">
            <div class="section-heading service-heading">
              <div>
                <span class="section-kicker">BOOKABLE SERVICES</span>
                <h2>选择服务</h2>
                <p class="section-subtitle">选择后可继续前往预约；预约时间由预约页面与服务端再次确认。</p>
              </div>
              <span class="section-count">共 {{ servicesTotal }} 项</span>
            </div>

            <div v-if="servicesLoading && !services.length" class="inline-loading"><el-skeleton :rows="4" animated /></div>
            <div v-else-if="servicesError" class="state-inline-error" role="alert">
              <p>{{ servicesError }}</p>
              <button type="button" class="btn btn-outline" @click="loadServices(servicesPage)">重新加载</button>
            </div>
            <div v-else-if="services.length" class="service-grid" :aria-busy="servicesLoading">
              <button
                v-for="service in services"
                :key="service.id"
                type="button"
                class="service-card"
                :class="{ selected: selectedService?.id === service.id }"
                :aria-pressed="selectedService?.id === service.id"
                @click="selectService(service)"
              >
                <span class="service-card-top">
                  <span class="service-game">{{ service.gameName }} · {{ service.serviceTypeName }}</span>
                  <span v-if="selectedService?.id === service.id" class="selected-mark" aria-label="已选择">✓</span>
                </span>
                <strong class="service-title">{{ service.title }}</strong>
                <span v-if="service.description" class="service-description">{{ service.description }}</span>
                <span v-if="service.tagNames?.length" class="service-tags">
                  <span v-for="tag in service.tagNames" :key="tag" class="service-tag">{{ tag }}</span>
                </span>
                <span class="service-card-bottom">
                  <span>至少 {{ service.minDurationMinutes }} 分钟</span>
                  <strong>{{ fmtPrice(service.priceCents) }}<small> / 小时</small></strong>
                </span>
              </button>
            </div>
            <div v-else class="empty-state-block">
              <span class="empty-mark" aria-hidden="true">✦</span>
              <h3>暂时没有可预约服务</h3>
              <p>你仍可查看陪玩师的公开资料与游戏能力。</p>
            </div>

            <el-pagination
              v-if="servicesTotal > servicesPageSize"
              class="pagination"
              background
              layout="prev, pager, next"
              :total="servicesTotal"
              :page-size="servicesPageSize"
              :current-page="servicesPage"
              :disabled="servicesLoading"
              @current-change="loadServices"
            />
          </div>
        </section>

        <section
          v-show="activeTab === 'reviews'"
          id="companion-panel-reviews"
          role="tabpanel"
          aria-labelledby="companion-tab-reviews"
          class="content-panel"
        >
          <div class="review-layout">
            <aside class="content-card rating-card">
              <span class="section-kicker">RATING</span>
              <h2>评价概况</h2>
              <div class="rating-main">
                <strong>{{ ratingValue ?? '—' }}</strong>
                <span class="rating-stars" aria-label="评分汇总">★★★★★</span>
                <span class="rating-scope">{{ ratingScope }}</span>
              </div>
              <div v-if="reviews.length" class="sample-histogram">
                <h3>当前加载评价星级分布（样本）</h3>
                <div v-for="item in [...sampleDistribution].reverse()" :key="item.score" class="histogram-row">
                  <span>{{ item.score }} 星</span>
                  <span class="histogram-track"><i :style="{ width: `${(item.count / reviews.length) * 100}%` }" /></span>
                  <b>{{ item.count }}</b>
                </div>
              </div>
              <p v-else class="rating-empty">评价样本加载后会显示本页分布。</p>
            </aside>

            <div class="content-card reviews-card">
              <div class="section-heading">
                <div>
                  <span class="section-kicker">REVIEWS</span>
                  <h2>玩家评价</h2>
                </div>
                <span class="section-count">共 {{ profile.ratingCount }} 条</span>
              </div>

              <div v-if="reviewsLoading && !reviews.length" class="inline-loading"><el-skeleton :rows="4" animated /></div>
              <div v-else-if="reviewsError" class="state-inline-error" role="alert">
                <p>{{ reviewsError }}</p>
                <button type="button" class="btn btn-outline" @click="loadReviews(reviewsPage)">重新加载</button>
              </div>
              <div v-else-if="reviews.length" class="review-list" :aria-busy="reviewsLoading">
                <article v-for="review in reviews" :key="review.id" class="review-item">
                  <div class="review-avatar" aria-hidden="true">{{ review.userNickname?.trim().charAt(0) || '匿' }}</div>
                  <div class="review-content">
                    <div class="review-topline">
                      <strong>{{ review.userNickname || '匿名玩家' }}</strong>
                      <time :datetime="review.createdAt">{{ fmtTime(review.createdAt) }}</time>
                    </div>
                    <div class="review-score" :aria-label="`${review.score} 星`">
                      <span v-for="star in 5" :key="star" :class="{ muted: star > review.score }">★</span>
                      <span v-if="review.tags?.length" class="review-tags">{{ review.tags.join(' · ') }}</span>
                    </div>
                    <p v-if="review.content" class="review-text">{{ review.content }}</p>
                    <p v-else class="review-text review-muted">未填写文字评价</p>
                  </div>
                </article>
              </div>
              <div v-else class="empty-state-block review-empty">
                <span class="empty-mark" aria-hidden="true">✦</span>
                <h3>还没有公开评价</h3>
                <p>完成服务并提交的公开评价会显示在这里。</p>
              </div>

              <el-pagination
                v-if="reviewsTotal > reviewsPageSize"
                class="pagination"
                background
                layout="prev, pager, next"
                :total="reviewsTotal"
                :page-size="reviewsPageSize"
                :current-page="reviewsPage"
                :disabled="reviewsLoading"
                @current-change="loadReviews"
              />
            </div>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>

<style scoped>
.companion-page {
  background: #f6f8fb;
  min-height: calc(100vh - var(--header-height));
  padding-top: 28px;
}

.profile-hero,
.content-card,
.state-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: 0 8px 28px rgba(20, 37, 63, 0.045);
}

.profile-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 390px);
  gap: 28px;
  align-items: center;
  padding: clamp(22px, 4vw, 42px);
  overflow: hidden;
  position: relative;
}

.profile-hero::after {
  content: '';
  position: absolute;
  width: 260px;
  height: 260px;
  right: 25%;
  top: -185px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(66, 133, 244, 0.12), transparent 70%);
  pointer-events: none;
}

.hero-main { display: flex; align-items: center; gap: 22px; min-width: 0; }
.avatar-wrap { flex: 0 0 auto; }
.profile-avatar {
  width: 112px;
  height: 112px;
  border-radius: 24px;
  object-fit: cover;
  background: #eaf1ff;
}
.avatar-fallback {
  display: grid;
  place-items: center;
  color: #2e68c7;
  font-size: 42px;
  font-weight: 700;
  background: linear-gradient(135deg, #e8f0ff, #d8e6ff);
}
.hero-copy { min-width: 0; }
.eyebrow, .section-kicker {
  color: #6381a8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}
.hero-copy h1 { margin-top: 4px; font-size: clamp(25px, 3vw, 34px); line-height: 1.25; overflow-wrap: anywhere; }
.hero-badges { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.status-pill, .qualification-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.status-pill { color: #58687c; background: #f0f3f7; }
.status-pill i { width: 7px; height: 7px; border-radius: 50%; background: #8b98a9; }
.status-available { color: #15805b; background: #e8f7f1; }
.status-available i { background: #20a876; }
.status-busy { color: #a66008; background: #fff5e4; }
.status-busy i { background: #e9a333; }
.status-resting, .status-suspended { color: #6c7380; background: #eff1f4; }
.qualification-pill { color: #4168a7; background: #edf3ff; }
.hero-intro { max-width: 690px; margin-top: 14px; color: #566579; line-height: 1.75; white-space: pre-line; overflow-wrap: anywhere; }
.intro-muted { color: #98a2af; }
.hero-actions { display: flex; gap: 9px; margin-top: 13px; }

.hero-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); position: relative; z-index: 1; }
.stat-item { min-width: 0; padding: 5px 16px; border-left: 1px solid #edf0f4; }
.stat-item:first-child { border-left: 0; }
.stat-label, .stat-note { display: block; color: #8793a2; font-size: 12px; }
.stat-item strong { display: block; margin: 7px 0 3px; color: #1b2a3e; font-size: 25px; line-height: 1.1; }
.stat-item strong small { color: #8995a4; font-size: 12px; font-weight: 500; }
.stat-note { font-size: 11px; white-space: nowrap; }

.page-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: 22px 0 18px; }
.tab-list { display: flex; align-items: center; gap: 5px; overflow-x: auto; scrollbar-width: thin; }
.tab-button {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 43px;
  padding: 0 17px;
  border: 0;
  border-radius: 11px;
  color: #738196;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  transition: color 0.18s ease, background 0.18s ease;
}
.tab-button:hover { color: #2e68c7; background: #eaf1ff; }
.tab-button.active { color: #245fbf; background: #e8f0ff; }
.tab-count { display: inline-grid; min-width: 20px; height: 20px; place-items: center; padding: 0 5px; border-radius: 10px; background: rgba(66, 133, 244, 0.1); font-size: 11px; }
.toolbar-booking { display: flex; align-items: center; gap: 12px; min-width: 0; }
.selected-service-label { max-width: 240px; color: #8290a1; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.toolbar-booking .btn { white-space: nowrap; }
.toolbar-booking .btn:disabled { cursor: not-allowed; opacity: 0.55; }
.content-panel { min-width: 0; }
.home-grid { display: grid; grid-template-columns: minmax(0, 1.12fr) minmax(320px, 0.88fr); gap: 18px; margin-bottom: 18px; }
.content-card { min-width: 0; padding: clamp(20px, 3vw, 30px); }
.section-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 22px; }
.section-heading h2, .rating-card h2 { margin-top: 4px; color: #1e2c40; font-size: 20px; }
.section-subtitle { margin-top: 5px; color: #7c8999; font-size: 13px; }
.section-count { flex: 0 0 auto; padding: 5px 10px; border-radius: 999px; color: #687b95; background: #f1f4f8; font-size: 12px; }
.intro-body { color: #536175; line-height: 1.9; white-space: pre-line; overflow-wrap: anywhere; }
.empty-inline { display: flex; align-items: center; gap: 11px; min-height: 72px; color: #8995a3; }
.empty-inline p { font-size: 13px; }
.empty-mark { display: inline-grid; flex: 0 0 auto; width: 34px; height: 34px; place-items: center; border-radius: 11px; color: #7395c7; background: #eef4ff; font-size: 16px; }
.capability-list { display: grid; gap: 10px; }
.capability-item { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid #edf0f4; border-radius: 13px; }
.capability-icon { display: grid; flex: 0 0 auto; width: 40px; height: 40px; place-items: center; border-radius: 12px; color: #426da8; background: #edf3ff; font-size: 16px; font-weight: 700; }
.capability-copy { min-width: 0; }
.capability-copy h3 { color: #35465d; font-size: 14px; }
.capability-copy p { margin-top: 4px; color: #8591a0; font-size: 12px; }
.capability-tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }
.capability-disclaimer { margin-top: 14px; color: #99a3af; font-size: 11px; line-height: 1.6; }
.text-action { padding: 3px 0; border: 0; color: #3675d1; background: transparent; font-size: 13px; font-weight: 600; }
.text-action:hover { color: #174f9d; }
.service-preview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.service-preview { display: flex; flex-direction: column; gap: 10px; min-width: 0; padding: 15px; text-align: left; border: 1px solid #e9edf3; border-radius: 13px; background: #fff; transition: border-color 0.18s ease, transform 0.18s ease; }
.service-preview:hover { border-color: #a8c7f8; transform: translateY(-2px); }
.service-game { display: block; color: #8390a0; font-size: 11px; }
.service-preview strong { color: #31435b; font-size: 14px; overflow-wrap: anywhere; }
.service-preview-bottom { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-top: auto; color: #8a96a4; font-size: 11px; }
.service-preview-bottom b { color: #e17b42; font-size: 15px; }
.schedule-card { margin-top: 18px; }
.availability-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.availability-slot { display: grid; grid-template-columns: 1fr auto; gap: 5px 12px; padding: 13px 15px; border: 1px solid #e7ebf1; border-radius: 12px; color: #43536a; background: #fff; text-align: left; }
.availability-slot:hover { border-color: #a8c7f8; background: #f8fbff; }
.availability-date { color: #8793a2; font-size: 11px; }
.availability-slot strong { grid-column: 1; font-size: 13px; }
.availability-action { grid-column: 2; grid-row: 1 / span 2; align-self: center; color: #3675d1; font-size: 12px; font-weight: 600; }
.service-heading { margin-bottom: 24px; }
.service-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.service-card { display: flex; flex-direction: column; align-items: stretch; min-width: 0; min-height: 205px; padding: 19px; text-align: left; border: 1px solid #e7ebf1; border-radius: 15px; background: #fff; transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease; }
.service-card:hover { border-color: #9dbef0; box-shadow: 0 8px 22px rgba(46, 104, 199, 0.08); transform: translateY(-2px); }
.service-card.selected { border-color: #6194e3; box-shadow: 0 0 0 3px rgba(66, 133, 244, 0.1); }
.service-card-top, .service-card-bottom { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.selected-mark { display: grid; flex: 0 0 auto; width: 21px; height: 21px; place-items: center; border-radius: 50%; color: #fff; background: #4285f4; font-size: 12px; }
.service-title { display: block; margin-top: 13px; color: #26384e; font-size: 17px; overflow-wrap: anywhere; }
.service-description { display: -webkit-box; margin-top: 8px; overflow: hidden; color: #748195; font-size: 12px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.service-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.service-tag { padding: 3px 8px; border-radius: 999px; color: #6681a5; background: #f0f4fa; font-size: 10px; }
.service-card-bottom { margin-top: auto; padding-top: 17px; color: #8793a1; font-size: 12px; }
.service-card-bottom strong { color: #df7540; font-size: 19px; }
.service-card-bottom small { color: #939daa; font-size: 11px; font-weight: 400; }
.pagination { justify-content: center; margin-top: 26px; }
.empty-state-block { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 205px; text-align: center; color: #8390a0; }
.empty-state-block .empty-mark { margin-bottom: 12px; }
.empty-state-block h3 { color: #46566c; font-size: 16px; }
.empty-state-block p { margin-top: 6px; font-size: 13px; }
.review-layout { display: grid; grid-template-columns: minmax(260px, 0.72fr) minmax(0, 1.55fr); gap: 18px; align-items: start; }
.rating-card h2 { margin-bottom: 23px; }
.rating-main { display: flex; flex-direction: column; align-items: flex-start; padding-bottom: 21px; border-bottom: 1px solid #edf0f4; }
.rating-main > strong { color: #1f3047; font-size: 44px; line-height: 1.05; }
.rating-stars, .review-score { color: #f2aa38; letter-spacing: 2px; }
.rating-stars { margin-top: 7px; font-size: 17px; }
.rating-scope { margin-top: 6px; color: #8693a3; font-size: 12px; }
.sample-histogram { margin-top: 20px; }
.sample-histogram h3 { margin-bottom: 12px; color: #68788d; font-size: 12px; font-weight: 600; }
.histogram-row { display: grid; grid-template-columns: 38px minmax(50px, 1fr) 20px; gap: 9px; align-items: center; margin: 9px 0; color: #8994a2; font-size: 11px; }
.histogram-row b { color: #65748a; text-align: right; font-weight: 500; }
.histogram-track { height: 7px; border-radius: 5px; background: #eff2f6; overflow: hidden; }
.histogram-track i { display: block; height: 100%; border-radius: inherit; background: #f2b64e; }
.rating-empty { margin-top: 16px; color: #98a2af; font-size: 12px; }
.review-list { display: flex; flex-direction: column; }
.review-item { display: flex; gap: 13px; padding: 19px 0; border-bottom: 1px solid #eef1f5; }
.review-item:first-child { padding-top: 2px; }
.review-item:last-child { border-bottom: 0; }
.review-avatar { display: grid; flex: 0 0 auto; width: 38px; height: 38px; place-items: center; border-radius: 50%; color: #637d9f; background: #edf2f8; font-size: 14px; font-weight: 600; }
.review-content { min-width: 0; flex: 1; }
.review-topline { display: flex; justify-content: space-between; gap: 10px; }
.review-topline strong { color: #3c4c62; font-size: 13px; }
.review-topline time { flex: 0 0 auto; color: #9aa4af; font-size: 11px; }
.review-score { margin-top: 5px; font-size: 13px; letter-spacing: 1px; }
.review-score .muted { color: #d9dee5; }
.review-tags { margin-left: 8px; color: #79889b; font-size: 11px; letter-spacing: 0; }
.review-text { margin-top: 9px; color: #58677b; font-size: 13px; line-height: 1.75; white-space: pre-line; overflow-wrap: anywhere; }
.review-muted { color: #9aa4af; }
.review-empty { min-height: 245px; }
.state-card { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; max-width: 650px; min-height: 290px; margin: 50px auto; padding: 32px; text-align: center; }
.state-card h1 { color: #34465e; font-size: 21px; }
.state-card p { max-width: 420px; color: #8290a1; font-size: 13px; }
.state-card .btn { margin-top: 3px; }
.state-icon { display: grid; width: 50px; height: 50px; place-items: center; border-radius: 16px; color: #6d8db8; background: #eef4fc; font-size: 25px; font-weight: 700; }
.state-inline-error { display: flex; flex-direction: column; align-items: center; gap: 13px; min-height: 160px; justify-content: center; color: #7f8997; font-size: 13px; }
.inline-error { display: flex; align-items: center; gap: 14px; color: #7e8997; font-size: 13px; }
.inline-loading { padding: 10px 0; }
.intro-card, .capability-card, .home-services, .rating-card, .reviews-card { animation: panel-in 0.28s ease both; }
.tab-button:focus-visible, .service-card:focus-visible, .service-preview:focus-visible, .text-action:focus-visible, .btn:focus-visible { outline: 3px solid rgba(66, 133, 244, 0.48); outline-offset: 2px; }

@keyframes panel-in { from { opacity: 0.65; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

@media (max-width: 900px) {
  .profile-hero { grid-template-columns: 1fr; gap: 24px; }
  .hero-stats { max-width: 600px; }
  .stat-item:first-child { padding-left: 0; }
  .home-grid { grid-template-columns: 1fr; }
}

@media (max-width: 680px) {
  .companion-page { padding: 16px 0 calc(90px + env(safe-area-inset-bottom)); }
  .container { padding-left: 16px; padding-right: 16px; }
  .profile-hero { padding: 20px 17px; border-radius: 15px; }
  .hero-main { align-items: flex-start; gap: 14px; }
  .profile-avatar { width: 72px; height: 72px; border-radius: 18px; }
  .avatar-fallback { font-size: 29px; }
  .hero-copy h1 { font-size: 23px; }
  .hero-intro { margin-top: 10px; font-size: 13px; }
  .hero-badges { margin-top: 9px; }
  .status-pill, .qualification-pill { min-height: 25px; padding: 3px 8px; font-size: 10px; }
  .hero-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); row-gap: 13px; margin: 0 -5px; }
  .stat-item { padding: 4px 9px; }
  .stat-item strong { font-size: 21px; }
  .stat-note { white-space: normal; line-height: 1.4; }
  .page-toolbar { display: block; margin: 14px 0; }
  .tab-list { padding: 4px 0 9px; }
  .tab-button { min-height: 39px; padding: 0 13px; }
  .toolbar-booking { position: fixed; z-index: 80; right: 0; bottom: 0; left: 0; justify-content: space-between; gap: 12px; padding: 10px 16px calc(10px + env(safe-area-inset-bottom)); border-top: 1px solid #e9edf3; background: rgba(255, 255, 255, 0.96); backdrop-filter: blur(12px); }
  .selected-service-label { flex: 1; max-width: none; }
  .toolbar-booking .btn { min-width: 112px; }
  .content-card { padding: 19px 16px; border-radius: 15px; }
  .home-grid, .review-layout { gap: 13px; }
  .section-heading { margin-bottom: 17px; }
  .section-heading h2, .rating-card h2 { font-size: 18px; }
  .service-preview-grid { grid-template-columns: 1fr; }
  .service-preview { min-height: 122px; }
  .availability-list { grid-template-columns: 1fr; }
  .service-grid { grid-template-columns: 1fr; gap: 10px; }
  .service-card { min-height: 180px; padding: 16px; }
  .review-layout { grid-template-columns: 1fr; }
  .rating-card { display: grid; grid-template-columns: minmax(0, 1fr) minmax(130px, 0.85fr); column-gap: 15px; }
  .rating-card > .section-kicker, .rating-card > h2 { grid-column: 1 / -1; }
  .rating-card h2 { margin-bottom: 15px; }
  .rating-main { padding: 0; border: 0; }
  .sample-histogram { margin-top: 0; }
  .review-item { gap: 10px; }
  .review-topline { flex-wrap: wrap; }
  .review-topline time { margin-left: auto; }
  .state-card { min-height: 250px; margin: 30px auto; padding: 24px 18px; }
}

@media (max-width: 390px) {
  .hero-main { align-items: center; }
  .profile-avatar { width: 60px; height: 60px; border-radius: 15px; }
  .avatar-fallback { font-size: 24px; }
  .hero-copy h1 { font-size: 20px; }
  .stat-item { padding-right: 6px; padding-left: 6px; }
  .stat-label, .stat-note { font-size: 10px; }
  .stat-item strong { font-size: 18px; }
  .rating-card { display: block; }
  .rating-card h2 { margin-bottom: 13px; }
  .sample-histogram { margin-top: 19px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}
</style>
