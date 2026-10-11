<script setup lang="ts">
/**
 * 用户端 - 活动详情页（/activities/:id）
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { publicActivityDetail, type ActivityView } from '@/api/activity'
import { claimCoupon } from '@/api/coupon'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activityId = computed(() => Number(route.params.id))
const activity = ref<ActivityView | null>(null)
const loading = ref(true)
const claiming = ref(false)

function fmtTime(t?: string): string {
  if (!t) return ''
  return t.length >= 10 ? t.slice(0, 10) : t
}

async function load() {
  loading.value = true
  try {
    activity.value = await publicActivityDetail(activityId.value)
  } catch {
    activity.value = null
  } finally {
    loading.value = false
  }
}

async function handleClaim() {
  if (!activity.value?.couponTemplateId) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再领取优惠券')
    router.push(`/login?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`)
    return
  }
  claiming.value = true
  try {
    await claimCoupon(activity.value.couponTemplateId)
    ElMessage.success('优惠券领取成功！快去下单使用吧')
  } catch {
    // 错误在全局拦截器已处理
  } finally {
    claiming.value = false
  }
}

function handleGoCompanions() {
  router.push('/companions')
}

function goBack() {
  router.push('/activities')
}

onMounted(() => {
  load()
})
</script>

<template>
  <div class="page activity-detail-page">
    <div class="container" v-loading="loading">
      <div class="back-bar">
        <el-button link @click="goBack">
          ← 返回活动列表
        </el-button>
      </div>

      <div v-if="!activity && !loading" class="card empty-card">
        <div class="empty-icon">⚠️</div>
        <div class="empty-text">该活动不存在或已下线</div>
        <el-button type="primary" class="mt-4" @click="goBack">返回列表</el-button>
      </div>

      <div v-else-if="activity" class="card detail-container">
        <!-- 头部 Banner 区域 -->
        <div
          class="hero-banner"
          :style="activity.bannerImage ? {
            backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.4), rgba(0, 0, 0, 0.7)), url(${activity.bannerImage})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat'
          } : {
            background: activity.bgGradient || 'linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)'
          }"
        >
          <div class="banner-badge" v-if="activity.tag">
            <span class="tag-pill" :style="{ backgroundColor: activity.tagColor || 'rgba(255,255,255,0.25)' }">
              {{ activity.tag }}
            </span>
          </div>
          <div class="banner-icon">{{ activity.icon || '🎉' }}</div>
          <h1 class="hero-title">{{ activity.title }}</h1>
          <p class="hero-subtitle">{{ activity.subtitle }}</p>
          <div class="hero-time">
            活动时间：
            <span v-if="activity.startTime || activity.endTime">
              {{ fmtTime(activity.startTime) }} ~ {{ fmtTime(activity.endTime) }}
            </span>
            <span v-else>长期有效</span>
          </div>
        </div>

        <!-- 详细内容与福利领取区 -->
        <div class="detail-body">
          <div class="section-block">
            <h2 class="section-title">活动说明</h2>
            <div class="description-text">
              {{ activity.description || '暂无详细说明。欢迎参与！' }}
            </div>
          </div>

          <!-- 专属优惠券福利卡片 -->
          <div v-if="activity.couponTemplateId" class="coupon-perk-card">
            <div class="perk-info">
              <div class="perk-badge">专属福利</div>
              <div class="perk-name">{{ activity.couponTemplateName || '活动专享优惠券' }}</div>
              <div class="perk-tip">每人限领 1 张，下单结算自动抵扣</div>
            </div>
            <div class="perk-action">
              <el-button
                type="primary"
                size="large"
                round
                :loading="claiming"
                @click="handleClaim"
              >
                立即领取
              </el-button>
            </div>
          </div>

          <!-- 底部操作按钮 -->
          <div class="action-footer">
            <el-button size="large" round @click="handleGoCompanions">
              浏览陪玩师
            </el-button>
            <el-button
              v-if="activity.actionType === 'COUPON_CLAIM'"
              type="primary"
              size="large"
              round
              @click="router.push('/coupons')"
            >
              查看我的优惠券
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.activity-detail-page {
  padding: 32px 0 64px;
}
.back-bar {
  margin-bottom: 20px;
}
.detail-container {
  overflow: hidden;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-sm);
  padding: 0;
}
.hero-banner {
  padding: 48px 40px;
  color: #fff;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.banner-badge {
  margin-bottom: 16px;
}
.tag-pill {
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 9999px;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.banner-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}
.hero-title {
  font-size: 2rem;
  font-weight: 800;
  margin-bottom: 8px;
  line-height: 1.3;
}
.hero-subtitle {
  font-size: 1.125rem;
  opacity: 0.95;
  margin-bottom: 16px;
}
.hero-time {
  font-size: 0.875rem;
  opacity: 0.85;
}
.detail-body {
  padding: 36px 40px;
}
.section-block {
  margin-bottom: 36px;
}
.section-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--foreground);
  margin-bottom: 16px;
  border-left: 4px solid var(--primary);
  padding-left: 12px;
}
.description-text {
  font-size: 1rem;
  line-height: 1.8;
  color: var(--foreground);
  white-space: pre-wrap;
}
.coupon-perk-card {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.05) 0%, rgba(249, 115, 22, 0.05) 100%);
  border: 1.5px dashed rgba(239, 68, 68, 0.4);
  border-radius: var(--radius-md);
  padding: 24px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 36px;
}
.perk-badge {
  display: inline-block;
  background: #ef4444;
  color: #fff;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
}
.perk-name {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--foreground);
  margin-bottom: 4px;
}
.perk-tip {
  font-size: 0.875rem;
  color: var(--muted-foreground);
}
.action-footer {
  display: flex;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}
.empty-card {
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
.mt-4 {
  margin-top: 16px;
}
</style>
