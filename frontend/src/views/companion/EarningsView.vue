<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { earningsLedgers, earningsOverview, type Earnings, type Ledger } from '@/api/companion'

const overview = ref<Earnings | null>(null)
const ledgers = ref<Ledger[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

async function load() {
  overview.value = await earningsOverview()
  const result = await earningsLedgers(page.value, pageSize)
  ledgers.value = result.records
  total.value = result.total
}

onMounted(load)

const ledgerTypeMap: Record<string, { label: string; type: 'success' | 'danger' | 'warning' | 'info' }> = {
  PAYMENT: { label: '支付', type: 'danger' },
  REFUND: { label: '退款', type: 'success' },
  SETTLEMENT: { label: '收益结算', type: 'success' },
  ADJUSTMENT: { label: '调整', type: 'warning' },
}
</script>

<template>
  <div class="earnings-view">
    <el-row :gutter="16" class="stat-row" v-if="overview">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">可用余额（元）</div>
          <div class="stat-value">¥{{ (overview.balanceCents / 100).toFixed(2) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">冻结金额（元）</div>
          <div class="stat-value">¥{{ (overview.frozenCents / 100).toFixed(2) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">累计已结算收益（元）</div>
          <div class="stat-value">¥{{ (overview.totalIncomeCents / 100).toFixed(2) }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">完成订单 / 评分</div>
          <div class="stat-value">
            {{ overview.completedOrderCount }} 单
            <span class="rating">{{ overview.ratingAvg ?? '-' }} 分（{{ overview.ratingCount }} 条）</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>收益明细（FR-P19）</template>
      <el-table :data="ledgers" v-loading="loading" border stripe>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="ledgerTypeMap[row.ledgerType]?.type" size="small">
              {{ ledgerTypeMap[row.ledgerType]?.label ?? row.ledgerType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="80">
          <template #default="{ row }">
            <span :class="row.direction === 'IN' ? 'in' : 'out'">
              {{ row.direction === 'IN' ? '入账' : '出账' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="金额（元）" width="120">
          <template #default="{ row }">
            <span :class="row.direction === 'IN' ? 'in' : 'out'">
              {{ row.direction === 'IN' ? '+' : '-' }}{{ (row.amountCents / 100).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="余额（元）" width="110">
          <template #default="{ row }">¥{{ (row.balanceAfterCents / 100).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="流水号" min-width="200" show-overflow-tooltip />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </el-card>
  </div>
</template>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  margin-bottom: 12px;
}
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 8px;
}
.rating {
  font-size: 13px;
  color: #e6a23c;
}
.in {
  color: #67c23a;
  font-weight: 600;
}
.out {
  color: #f56c6c;
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
