<script setup lang="ts">
/**
 * 首页（对齐 frontend-prototype index.html：Hero + 公告 + 功能入口 + 推荐陪玩师）。
 *
 * <p>游客可访问；数据来自公告公开接口与可预约服务接口。</p>
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicAnnouncements, type AnnouncementView } from '@/api/admin'
import { listBookableServices, type BookableService } from '@/api/order'

const router = useRouter()

/** 平台公告（仅已发布，FR-A08） */
const announcements = ref<AnnouncementView[]>([])
const expanded = ref<Record<number, boolean>>({})

/** 推荐可预约服务 */
const recommended = ref<BookableService[]>([])

/** 功能入口（原型 feature-grid） */
const features = [
  { path: '/games', icon: '🎮', title: '游戏列表', desc: '海量游戏，任你选择' },
  { path: '/companions', icon: '🤝', title: '找陪玩', desc: '大神陪练，随时开黑' },
  { path: '/orders', icon: '📋', title: '我的订单', desc: '预约支付，一键完成' },
  { path: '/support', icon: '💬', title: '在线客服', desc: 'AI 应答，可转人工' },
]

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 16 ? t.slice(0, 16) : t
}

function fmtPrice(cents: number): string {
  return '¥' + (cents / 100).toFixed(cents % 100 === 0 ? 0 : 2)
}

onMounted(async () => {
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
</script>

<template>
  <div class="home">
    <!-- Hero（原型 hero） -->
    <section class="hero">
      <div class="container hero-inner">
        <div>
          <h1 class="hero-title">
            找到属于你的<br /><span>最佳游戏搭档</span>
          </h1>
          <p class="hero-desc">技术大神、娱乐伙伴、耐心教练，应有尽有。一键预约，畅享开黑乐趣。</p>
          <div class="hero-actions">
            <button class="btn btn-primary btn-lg" @click="router.push('/companions')">开始找陪玩</button>
            <button class="btn btn-outline btn-lg" @click="router.push('/games')">浏览游戏</button>
          </div>
        </div>
        <div class="hero-visual">
          <div class="hero-stat"><div class="num">12,000+</div><div class="label">注册陪玩师</div></div>
          <div class="hero-stat"><div class="num">50+</div><div class="label">覆盖游戏</div></div>
          <div class="hero-stat"><div class="num">98%</div><div class="label">好评率</div></div>
          <div class="hero-stat"><div class="num">24h</div><div class="label">在线客服</div></div>
        </div>
      </div>
    </section>

    <div class="container">
      <!-- 平台公告（FR-A08） -->
      <section v-if="announcements.length" class="section" style="padding-bottom: 0">
        <div class="card">
          <div class="card-body">
            <div class="card-title mb-2">📢 平台公告</div>
            <div v-for="a in announcements" :key="a.id" class="announce-item">
              <div class="announce-title-row" @click="expanded[a.id] = !expanded[a.id]">
                <span class="announce-title">{{ a.title }}</span>
                <span class="announce-time">{{ fmtTime(a.publishedAt) }}</span>
              </div>
              <div v-if="expanded[a.id]" class="announce-content">{{ a.content }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 功能入口（原型 feature-grid） -->
      <section class="section">
        <div class="feature-grid">
          <div v-for="f in features" :key="f.path" class="feature-card" @click="router.push(f.path)">
            <div class="feature-icon">{{ f.icon }}</div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-desc">{{ f.desc }}</div>
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
