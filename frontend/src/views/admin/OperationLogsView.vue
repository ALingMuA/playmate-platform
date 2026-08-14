<script setup lang="ts">
/**
 * 管理后台 - 操作日志（FR-M20）。
 *
 * <p>记录管理员对用户、陪玩师、订单、投诉、客服账号和配置的关键操作，
 * 支持按操作类型、目标类型筛选与分页查询。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { adminOperationLogs, type OperationLogView } from '@/api/admin'

const list = ref<OperationLogView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const query = reactive({
  operatorId: undefined as number | undefined,
  operationType: undefined as string | undefined,
  targetType: undefined as string | undefined,
})

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  try {
    const result = await adminOperationLogs(
      query.operatorId,
      query.operationType,
      query.targetType,
      page.value,
      pageSize,
    )
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  query.operatorId = undefined
  query.operationType = undefined
  query.targetType = undefined
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="logs-page">
    <div class="page-header">
      <h1>操作日志</h1>
      <p class="sub">管理员关键操作审计记录（用户、陪玩师、订单、投诉、客服账号、配置）</p>
    </div>

    <!-- 查询区 -->
    <el-card class="query-card" shadow="hover">
      <el-form :inline="true" @submit.prevent="onSearch">
        <el-form-item label="操作人ID">
          <el-input-number v-model="query.operatorId" :min="1" placeholder="操作人ID" controls-position="right"
            style="width: 140px" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="query.operationType" placeholder="如：USER_DISABLE" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item label="目标类型">
          <el-input v-model="query.targetType" placeholder="如：USER / ORDER" clearable style="width: 170px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="操作人" width="150">
          <template #default="{ row }">
            {{ row.operatorId }}
            <el-tag size="small" effect="plain" class="role-tag">{{ row.operatorRole }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" type="warning" effect="plain">{{ row.operationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" min-width="130">
          <template #default="{ row }">
            {{ row.targetType }}<template v-if="row.targetId"> #{{ row.targetId }}</template>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="变更内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.afterData" :title="row.afterData">{{ row.afterData }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="130">
          <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
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
.page-header h1 {
  margin: 0 0 8px;
}
.sub {
  color: #909399;
  margin: 0 0 20px;
}
.query-card {
  margin-bottom: 16px;
}
.table-card {
  min-height: 400px;
}
.role-tag {
  margin-left: 4px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
