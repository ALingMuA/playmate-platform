<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { walletLedgers, walletMe, type WalletLedger } from '@/api/order'

const wallet = ref<{ balanceCents: number; frozenCents: number; totalIncomeCents: number } | null>(null)
const rows = ref<WalletLedger[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const walletLoading = ref(false)
const ledgerLoading = ref(false)
const walletFailed = ref(false)
const ledgersFailed = ref(false)

function money(cents: number) { return `¥${(cents / 100).toFixed(2)}` }

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

async function loadLedgers() {
  ledgerLoading.value = true
  ledgersFailed.value = false
  try {
    const result = await walletLedgers(page.value, pageSize)
    rows.value = result.records
    total.value = result.total
  } catch {
    ledgersFailed.value = true
  } finally {
    ledgerLoading.value = false
  }
}

function load() { void loadWallet(); void loadLedgers() }

onMounted(load)
</script>

<template>
  <section aria-label="我的钱包">
    <div class="wallet-grid">
      <div class="wallet-card" v-loading="walletLoading"><div class="wallet-value">{{ wallet ? money(wallet.balanceCents) : '—' }}</div><div class="wallet-label">可用余额</div></div>
      <div class="wallet-card" v-loading="walletLoading"><div class="wallet-value">{{ wallet ? money(wallet.frozenCents) : '—' }}</div><div class="wallet-label">冻结金额</div></div>
      <div class="wallet-card" v-loading="walletLoading"><div class="wallet-value">{{ wallet ? money(wallet.totalIncomeCents) : '—' }}</div><div class="wallet-label">累计收入</div></div>
    </div>
    <el-alert v-if="walletFailed" class="mt-1" type="error" :closable="false" title="钱包概览暂时无法加载">
      <template #default><el-button link type="primary" @click="loadWallet">重试</el-button></template>
    </el-alert>
    <div class="card mt-2">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-1">
          <h3 class="card-title">资金流水</h3>
        </div>
        <el-empty v-if="ledgersFailed" description="资金流水暂时无法加载">
          <el-button type="primary" @click="loadLedgers">重试</el-button>
        </el-empty>
        <div v-else-if="rows.length" v-loading="ledgerLoading" class="profile-ledger-list mt-2">
          <article v-for="row in rows" :key="row.businessNo" class="profile-ledger-row">
            <div>
              <strong>{{ row.remark || row.ledgerType }}</strong>
              <div class="text-muted">{{ row.createdAt?.slice(0, 16) }} · {{ row.businessNo }}</div>
            </div>
            <strong :class="row.direction === 'IN' ? 'text-success' : ''">
              {{ row.direction === 'IN' ? '+' : '−' }}{{ money(row.amountCents) }}
            </strong>
          </article>
        </div>
        <el-empty v-else-if="!ledgerLoading" description="暂无资金流水" />
        <div v-if="total > pageSize" class="pagination">
          <button class="page-link" :disabled="page <= 1" @click="page--; loadLedgers()">‹</button>
          <span class="page-link">{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
          <button class="page-link" :disabled="page >= Math.ceil(total / pageSize)" @click="page++; loadLedgers()">›</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile-ledger-list { display: grid; }
.profile-ledger-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--border); }
.profile-ledger-row:last-child { border-bottom: 0; }
</style>
