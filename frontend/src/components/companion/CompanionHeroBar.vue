<script setup lang="ts">
/**
 * 陪玩师公开主页顶部紧凑身份横条。
 *
 * <p>一行呈现头像、展示名、接单状态、资格徽章与核心数据（评分/完成单/在架服务/起价），
 * 不展示大段简介（简介统一由侧栏「关于我」卡展示，避免重复）。</p>
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { PublicCompanionProfile } from '@/api/companion'

const props = defineProps<{
  profile: PublicCompanionProfile
  servicesTotal: number
  servicesLoading: boolean
  isOwner: boolean
  isFavorited: boolean
  favoriteLoading: boolean
  favoriteSaving: boolean
  ratingValue: string | null
}>()

const emit = defineEmits<{ toggleFavorite: [] }>()

const router = useRouter()
const avatarFailed = ref(false)

const serviceStatusText: Record<PublicCompanionProfile['serviceStatus'], string> = {
  AVAILABLE: '可接单',
  BUSY: '当前忙碌',
  RESTING: '休息中',
  SUSPENDED: '暂停接单',
}

/** 平台陪玩资格状态，不代表每项能力证明均已核验 */
function certificationText(): string {
  if (props.profile.certificationStatus === 'APPROVED') return '平台资格已通过'
  if (props.profile.certificationStatus === 'SUSPENDED') return '平台资格已暂停'
  return ''
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}
</script>

<template>
  <section class="hero-bar" aria-labelledby="companion-name">
    <nav class="hero-crumb" aria-label="位置导航">
      <router-link to="/companions">‹ 返回找陪玩</router-link>
      <span aria-hidden="true">/</span>
      <span>陪玩师主页</span>
    </nav>

    <div class="hero-main">
      <img
        v-if="profile.avatarUrl && !avatarFailed"
        :src="profile.avatarUrl"
        :alt="`${profile.displayName}的头像`"
        class="hero-avatar"
        @error="avatarFailed = true"
      >
      <div v-else class="hero-avatar hero-avatar-fallback" aria-hidden="true">
        {{ profile.displayName?.trim().charAt(0) || '陪' }}
      </div>

      <div class="hero-copy">
        <h1 id="companion-name">{{ profile.displayName || '陪玩师' }}</h1>
        <div class="hero-badges">
          <span class="status-pill" :class="`is-${profile.serviceStatus.toLowerCase()}`">
            <i aria-hidden="true" />{{ serviceStatusText[profile.serviceStatus] }}
          </span>
          <span v-if="certificationText()" class="badge badge-secondary">{{ certificationText() }}</span>
        </div>
      </div>

      <div class="hero-actions">
        <button
          v-if="isOwner"
          type="button"
          class="btn btn-primary btn-sm"
          @click="router.push('/profile#tab=companion')"
        >
          编辑主页
        </button>
        <button
          type="button"
          class="btn btn-outline btn-sm"
          :disabled="favoriteLoading || favoriteSaving"
          :aria-pressed="isFavorited"
          @click="emit('toggleFavorite')"
        >
          {{ favoriteSaving ? '处理中…' : isFavorited ? '已关注' : '关注' }}
        </button>
      </div>
    </div>

    <dl class="hero-stats">
      <div>
        <dt>评分</dt>
        <dd>{{ ratingValue ?? '—' }}<small v-if="ratingValue"> / 5</small></dd>
      </div>
      <div>
        <dt>完成订单</dt>
        <dd>{{ profile.completedOrderCount }}</dd>
      </div>
      <div>
        <dt>在架服务</dt>
        <dd>{{ servicesLoading && !servicesTotal ? '…' : servicesTotal }}</dd>
      </div>
      <div>
        <dt>服务起价</dt>
        <dd>
          {{ profile.startingPriceCents != null ? fmtPrice(profile.startingPriceCents) : '—' }}<small v-if="profile.startingPriceCents != null"> / 小时</small>
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.hero-bar { display: grid; gap: 14px; padding: 18px 20px 16px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--card); box-shadow: var(--shadow-card); }
.hero-crumb { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--muted-foreground); }
.hero-crumb a { color: var(--muted-foreground); text-decoration: none; }
.hero-crumb a:hover, .hero-crumb a:focus-visible { color: var(--primary); }
.hero-main { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.hero-avatar { width: 72px; height: 72px; border-radius: 20px; object-fit: cover; flex: 0 0 auto; border: 1px solid var(--border); }
.hero-avatar-fallback { display: grid; place-items: center; background: var(--secondary); color: var(--secondary-foreground); font-size: 28px; font-weight: 700; }
.hero-copy { flex: 1 1 220px; min-width: 0; display: grid; gap: 8px; }
.hero-copy h1 { margin: 0; font-size: 24px; line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hero-badges { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hero-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }

.status-pill { display: inline-flex; align-items: center; gap: 6px; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: var(--muted); color: var(--muted-foreground); }
.status-pill i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.status-pill.is-available { background: color-mix(in srgb, #16a34a 12%, var(--card)); color: #15803d; }
.status-pill.is-busy { background: color-mix(in srgb, #d97706 12%, var(--card)); color: #b45309; }
.status-pill.is-resting { background: var(--muted); color: var(--muted-foreground); }
.status-pill.is-suspended { background: color-mix(in srgb, var(--destructive) 10%, var(--card)); color: var(--destructive); }

.hero-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 0; padding-top: 14px; border-top: 1px solid var(--border); }
.hero-stats > div { display: grid; gap: 3px; min-width: 0; padding: 0 4px; }
.hero-stats dt { font-size: 12px; color: var(--muted-foreground); }
.hero-stats dd { margin: 0; font-size: 19px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hero-stats small { font-size: 12px; font-weight: 400; color: var(--muted-foreground); }

@media (max-width: 640px) {
  .hero-bar { padding: 14px 16px; }
  .hero-avatar { width: 56px; height: 56px; border-radius: 16px; }
  .hero-copy h1 { font-size: 20px; }
  .hero-actions { margin-left: 0; }
  .hero-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
