<script setup lang="ts">
/**
 * 陪玩师公开主页右侧 sticky 侧栏：预约操作卡、游戏能力认证卡与「关于我」卡。
 *
 * <p>「关于我」是主页简介的唯一展示处（Hero 不再重复）；
 * 预约卡在陪玩师可接单且存在在架服务时提供一键进入预约页入口。</p>
 */
import { computed } from 'vue'
import type { PublicCompanionProfile } from '@/api/companion'
import type { BookableService } from '@/api/order'
import type { TagView } from '@/api/catalog'

const props = defineProps<{
  profile: PublicCompanionProfile
  /** gameId → 标签列表映射，用于渲染位置标签名 */
  capabilityTags: Record<number, TagView[]>
  /** 默认预约服务（第一个在架服务），无服务时为空 */
  defaultService: BookableService | null
  servicesLoading: boolean
}>()

const emit = defineEmits<{ book: [] }>()

const serviceStatusText: Record<PublicCompanionProfile['serviceStatus'], string> = {
  AVAILABLE: '可接单',
  BUSY: '当前忙碌',
  RESTING: '休息中',
  SUSPENDED: '暂停接单',
}

/** 可发起预约：可接单且已有在架服务 */
const bookable = computed(() =>
  props.profile.serviceStatus === 'AVAILABLE' && !!props.defaultService,
)

/** 预约按钮下方的原因说明 */
const bookHint = computed(() => {
  if (props.profile.serviceStatus !== 'AVAILABLE') {
    return `陪玩师当前${serviceStatusText[props.profile.serviceStatus]}，暂不可预约。`
  }
  if (props.servicesLoading) return '正在加载在架服务…'
  if (!props.defaultService) return '暂无在架可预约服务。'
  return `将从「${props.defaultService.title}」开始，可在预约页更换服务与时间。`
})

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

function positionTagName(gameId: number, tagId: number): string {
  return props.capabilityTags[gameId]?.find((tag) => tag.id === tagId)?.tagName ?? '位置标签'
}
</script>

<template>
  <div class="sidebar-stack">
    <section class="card side-card booking-card" aria-labelledby="companion-booking-heading">
      <div class="card-body">
        <h2 id="companion-booking-heading" class="side-title">预约陪玩</h2>
        <div class="booking-status">
          <span class="status-dot" :class="`is-${profile.serviceStatus.toLowerCase()}`" aria-hidden="true" />
          <span>{{ serviceStatusText[profile.serviceStatus] }}</span>
        </div>
        <dl class="booking-meta">
          <div>
            <dt>服务起价</dt>
            <dd>{{ profile.startingPriceCents != null ? fmtPrice(profile.startingPriceCents) + ' / 小时' : '—' }}</dd>
          </div>
        </dl>
        <button type="button" class="btn btn-primary booking-button" :disabled="!bookable" @click="emit('book')">
          {{ bookable ? '立即预约' : '暂不可预约' }}
        </button>
        <p class="booking-hint">{{ bookHint }}</p>
      </div>
    </section>

    <section class="card side-card" aria-labelledby="companion-capability-heading">
      <div class="card-body">
        <h2 id="companion-capability-heading" class="side-title">游戏能力</h2>
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
                <span v-for="tagId in capability.positionTagIds" :key="tagId" class="badge badge-outline">
                  {{ positionTagName(capability.gameId, tagId) }}
                </span>
              </div>
            </div>
          </article>
        </div>
        <p v-else class="side-empty">暂未公开游戏能力资料。</p>
        <p class="capability-disclaimer">以上能力均经平台审核认证后展示。</p>
      </div>
    </section>

    <section class="card side-card" aria-labelledby="companion-about-heading">
      <div class="card-body">
        <h2 id="companion-about-heading" class="side-title">关于我</h2>
        <p v-if="profile.profileIntro" class="about-text">{{ profile.profileIntro }}</p>
        <p v-else class="side-empty">陪玩师还没有填写个人介绍。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.sidebar-stack { display: grid; gap: 16px; align-content: start; }
.side-card { margin: 0; }
.side-title { margin: 0 0 10px; font-size: 15px; }
.side-empty { margin: 4px 0 0; color: var(--muted-foreground); font-size: 13px; }

.booking-card { border-color: color-mix(in srgb, var(--primary) 35%, var(--border)); }
.booking-status { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--muted-foreground); }
.status-dot.is-available { background: #16a34a; }
.status-dot.is-busy { background: #d97706; }
.status-dot.is-resting { background: var(--muted-foreground); }
.status-dot.is-suspended { background: var(--destructive); }
.booking-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 14px 0; }
.booking-meta > div { display: grid; gap: 3px; padding: 10px; border-radius: var(--radius); background: var(--muted); min-width: 0; }
.booking-meta dt { font-size: 12px; color: var(--muted-foreground); }
.booking-meta dd { margin: 0; font-size: 14px; font-weight: 700; }
.booking-button { width: 100%; }
.booking-hint { margin: 10px 0 0; font-size: 12px; color: var(--muted-foreground); line-height: 1.6; }

.capability-list { display: grid; gap: 12px; }
.capability-item { display: flex; gap: 10px; }
.capability-icon { width: 38px; height: 38px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 10px; background: var(--secondary); color: var(--secondary-foreground); font-size: 16px; font-weight: 700; }
.capability-copy { flex: 1 1 auto; min-width: 0; display: grid; gap: 4px; }
.capability-copy h3 { margin: 0; font-size: 14px; }
.capability-copy p { margin: 0; font-size: 12px; color: var(--muted-foreground); }
.capability-tags { display: flex; gap: 5px; flex-wrap: wrap; }
.capability-disclaimer { margin: 12px 0 0; padding-top: 10px; border-top: 1px dashed var(--border); font-size: 11px; color: var(--muted-foreground); }

.about-text { margin: 0; font-size: 13px; line-height: 1.7; white-space: pre-line; word-break: break-word; }
</style>
