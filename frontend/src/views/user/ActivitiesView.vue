<script setup lang="ts">
/**
 * 用户端 - 精彩活动中心（/activities）
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { publicActivities, type ActivityView } from '@/api/activity'
import { claimCoupon } from '@/api/coupon'

const router = useRouter()
const userStore = useUserStore()

const activities = ref<ActivityView[]>([])
const loading = ref(false)
const claimingId = ref<number | null>(null)

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 10 ? t.slice(0, 10) : t
}

async function loadData() {
  loading.value = true
  try {
    activities.value = await publicActivities()
  } finally {
    loading.value = false
  }
}

async function handleAction(item: ActivityView, e: Event) {
  e.stopPropagation()
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
    claimingId.value = item.couponTemplateId
    try {
      await claimCoupon(item.couponTemplateId)
      ElMessage.success('优惠券领取成功！可前往“我的优惠券”查看')
    } catch {
      // 错误信息全局拦截器已弹窗
    } finally {
      claimingId.value = null
    }
  } else if (item.actionType === 'COMPANION_LIST') {
    router.push('/companions')
  } else if (item.actionRoute) {
    if (item.actionRoute.startsWith('http')) {
      window.open(item.actionRoute, '_blank')
    } else {
      router.push(item.actionRoute)
    }
  } else {
    router.push(`/activities/${item.id}`)
  }
}

function goDetail(id: number) {
  router.push(`/activities/${id}`)
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page activities-page">
    <div class="container" v-loading="loading">
      <div class="page-header">
        <h1 class="page-title">精彩活动</h1>
        <p class="page-subtitle">参与平台福利活动，领取超值优惠，畅享开黑乐趣</p>
      </div>

      <div v-if="activities.length === 0 && !loading" class="empty-box card">
        <div class="empty-icon">🎉</div>
        <div class="empty-text">近期暂无进行中的活动，敬请期待！</div>
      </div>

      <div v-else class="activities-grid">
        <div
          v-for="item in activities"
          :key="item.id"
          class="card activity-card"
          @click="goDetail(item.id)"
        >
          <div
            class="card-banner"
            :style="{ background: item.bgGradient || 'linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)' }"
          >
            <div class="banner-tag" v-if="item.tag">
              <span class="tag-badge" :style="{ backgroundColor: item.tagColor || 'rgba(255,255,255,0.25)' }">
                {{ item.tag }}
              </span>
            </div>
            <div class="banner-icon">{{ item.icon || '🎁' }}</div>
            <h3 class="banner-title">{{ item.title }}</h3>
            <p class="banner-subtitle">{{ item.subtitle }}</p>
          </div>

          <div class="card-content">
            <p class="activity-desc">
              {{ item.description || '点击查看活动详情，参与获取惊喜福利！' }}
            </p>

            <div class="activity-meta">
              <span class="time-range" v-if="item.startTime || item.endTime">
                📅 {{ fmtTime(item.startTime) }} ~ {{ fmtTime(item.endTime) }}
              </span>
              <span class="time-range" v-else>
                📅 长期有效
              </span>
            </div>

            <div class="card-actions">
              <el-button
                v-if="item.actionType === 'COUPON_CLAIM'"
                type="primary"
                round
                :loading="claimingId === item.couponTemplateId"
                @click="(e: Event) => handleAction(item, e)"
              >
                立即领券
              </el-button>
              <el-button
                v-else-if="item.actionType === 'COMPANION_LIST'"
                type="primary"
                round
                @click="(e: Event) => handleAction(item, e)"
              >
                挑选陪玩
              </el-button>
              <el-button
                v-else
                type="primary"
                round
                @click="(e: Event) => handleAction(item, e)"
              >
                查看活动
              </el-button>

              <el-button link class="detail-link" @click.stop="goDetail(item.id)">
                详情 →
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.activities-page {
  padding: 32px 0 64px;
}
.activities-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 24px;
}
.activity-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.activity-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary);
}
.card-banner {
  padding: 28px 24px 20px;
  color: #fff;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 140px;
}
.banner-tag {
  position: absolute;
  top: 16px;
  left: 20px;
}
.tag-badge {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 9999px;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.banner-icon {
  font-size: 2.2rem;
  margin-bottom: 8px;
}
.banner-title {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: 4px;
  line-height: 1.3;
}
.banner-subtitle {
  font-size: 0.875rem;
  opacity: 0.9;
  line-height: 1.4;
}
.card-content {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  flex: 1;
}
.activity-desc {
  font-size: 0.9375rem;
  color: var(--muted-foreground);
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}
.activity-meta {
  font-size: 0.8125rem;
  color: var(--muted-foreground);
  margin-bottom: 16px;
}
.card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
}
.detail-link {
  color: var(--primary);
  font-weight: 500;
}
.empty-box {
  text-align: center;
  padding: 64px 0;
}
.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.empty-text {
  color: var(--muted-foreground);
}
</style>
