<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listFavorites, type FavoriteView } from '@/api/favorite'
import { myOrders, walletMe, type Order } from '@/api/order'

const router = useRouter()
const orders = ref<Order[]>([])
const favorites = ref<FavoriteView[]>([])
const wallet = ref<{ balanceCents: number; frozenCents: number; totalIncomeCents: number } | null>(null)
const ordersLoading = ref(false)
const favoritesLoading = ref(false)
const walletLoading = ref(false)
const ordersFailed = ref(false)
const favoritesFailed = ref(false)
const walletFailed = ref(false)

async function loadOrders() {
  ordersLoading.value = true
  ordersFailed.value = false
  try {
    orders.value = (await myOrders(undefined, 1, 3)).records
  } catch {
    ordersFailed.value = true
  } finally {
    ordersLoading.value = false
  }
}

async function loadFavorites() {
  favoritesLoading.value = true
  favoritesFailed.value = false
  try {
    favorites.value = (await listFavorites(1, 3)).records
  } catch {
    favoritesFailed.value = true
  } finally {
    favoritesLoading.value = false
  }
}

async function loadWallet() {
  walletLoading.value = true
  walletFailed.value = false
  try {
    wallet.value = await walletMe()
  } catch {
    walletFailed.value = true
  } finally {
    walletLoading.value = false
  }
}

function money(cents: number) { return `¥${(cents / 100).toFixed(2)}` }

function orderTime(value: string) { return value?.slice(0, 16) ?? '' }
function orderStatus(status: Order['orderStatus']) {
  return ({
    PENDING_PAYMENT: '待支付', WAITING_ACCEPTANCE: '待接单', WAITING_SERVICE: '待服务',
    IN_SERVICE: '服务中', WAITING_CONFIRMATION: '待确认', COMPLETED: '已完成',
    AFTER_SALES: '售后中', CLOSED: '已关闭',
  } as Record<string, string>)[status] ?? status
}

onMounted(() => { void loadOrders(); void loadFavorites(); void loadWallet() })
</script>

<template>
  <div class="profile-overview-grid">
    <section class="card">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-1">
          <h3 class="card-title">近期订单</h3>
          <button class="btn btn-ghost btn-sm" @click="router.push('/profile#tab=orders')">查看全部</button>
        </div>
        <div v-if="ordersLoading" class="profile-overview-loading">加载近期订单…</div>
        <div v-else-if="ordersFailed" class="profile-overview-loading">
          <span>订单暂时无法加载</span><button class="btn btn-ghost btn-sm" @click="loadOrders">重试</button>
        </div>
        <div v-else-if="orders.length" class="profile-overview-list">
          <button v-for="order in orders" :key="order.id" class="profile-overview-row"
            @click="router.push(`/orders/${order.id}`)">
            <span><strong>{{ order.serviceTitleSnapshot }}</strong><small>{{ orderTime(order.appointmentStartAt) }}</small></span>
            <span class="badge badge-outline">{{ orderStatus(order.orderStatus) }}</span>
          </button>
        </div>
        <el-empty v-else description="还没有订单" :image-size="72">
          <el-button type="primary" @click="router.push('/companions')">去找陪玩</el-button>
        </el-empty>
      </div>
    </section>

    <section class="card">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-1">
          <h3 class="card-title">我的收藏</h3>
          <button class="btn btn-ghost btn-sm" @click="router.push('/profile#tab=favorites')">查看全部</button>
        </div>
        <div v-if="favoritesLoading" class="profile-overview-loading">加载收藏…</div>
        <div v-else-if="favoritesFailed" class="profile-overview-loading">
          <span>收藏暂时无法加载</span><button class="btn btn-ghost btn-sm" @click="loadFavorites">重试</button>
        </div>
        <div v-else-if="favorites.length" class="profile-overview-list">
          <button v-for="favorite in favorites" :key="favorite.companionUserId" class="profile-overview-row"
            @click="router.push(`/companions/${favorite.companionUserId}`)">
            <span><strong>{{ favorite.displayName || `陪玩师 #${favorite.companionUserId}` }}</strong>
              <small>{{ favorite.profileIntro || '查看公开主页' }}</small></span>
            <span class="text-muted">{{ favorite.ratingAvg ?? '暂无评分' }}</span>
          </button>
        </div>
        <el-empty v-else description="还没有收藏的陪玩师" :image-size="72">
          <el-button type="primary" @click="router.push('/companions')">去发现陪玩师</el-button>
        </el-empty>
      </div>
    </section>

    <section class="card">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-1">
          <h3 class="card-title">钱包概览</h3>
          <button class="btn btn-ghost btn-sm" @click="router.push('/profile#tab=wallet')">查看流水</button>
        </div>
        <div v-if="walletLoading" class="profile-overview-loading">加载钱包概览…</div>
        <div v-else-if="walletFailed" class="profile-overview-loading">
          <span>钱包概览暂时无法加载</span><button class="btn btn-ghost btn-sm" @click="loadWallet">重试</button>
        </div>
        <div v-else-if="wallet" class="profile-wallet-summary">
          <div><strong>{{ money(wallet.balanceCents) }}</strong><small>可用余额</small></div>
          <div><strong>{{ money(wallet.frozenCents) }}</strong><small>冻结金额</small></div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.profile-overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.profile-overview-grid > .card { min-width: 0; margin: 0; }
.profile-overview-list { display: grid; margin-top: 12px; }
.profile-overview-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; width: 100%; padding: 12px 0; border: 0; border-bottom: 1px solid var(--border); background: transparent; color: inherit; text-align: left; cursor: pointer; }
.profile-overview-row:last-child { border-bottom: 0; }
.profile-overview-row > span:first-child { min-width: 0; display: grid; gap: 4px; }
.profile-overview-row small { color: var(--muted-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.profile-overview-loading { min-height: 110px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--muted-foreground); }
.profile-wallet-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 18px; }
.profile-wallet-summary > div { min-width: 0; display: grid; gap: 6px; padding: 13px; border-radius: 10px; background: var(--muted); }
.profile-wallet-summary strong { overflow: hidden; text-overflow: ellipsis; font-size: 20px; }
.profile-wallet-summary small { color: var(--muted-foreground); }
@media (max-width: 980px) { .profile-overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .profile-overview-grid { grid-template-columns: 1fr; } }
</style>
