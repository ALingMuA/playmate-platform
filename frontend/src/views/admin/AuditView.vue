<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApplications, adminAudit, type Application } from '@/api/companion'

const list = ref<Application[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const statusFilter = ref('PENDING')

async function load() {
  loading.value = true
  try {
    const result = await adminApplications(statusFilter.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

onMounted(load)

async function handleAudit(row: Application, approved: boolean) {
  let reason = ''
  if (approved) {
    await ElMessageBox.confirm(`确认通过 ${row.realName} 的入驻申请？通过后将自动授予陪玩师资格。`, '通过申请', {
      type: 'info',
    })
  } else {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回申请', {
      inputPlaceholder: '驳回原因将展示给申请人',
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '驳回原因不能为空'),
    })
    reason = value
  }
  await adminAudit(row.id, approved, reason)
  ElMessage.success(approved ? '已通过' : '已驳回')
  await load()
}

const statusMap: Record<string, { label: string; type: 'info' | 'success' | 'danger' }> = {
  PENDING: { label: '待审核', type: 'info' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}
</script>

<template>
  <div class="audit-view">
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="onFilterChange">
        <el-radio-button value="PENDING">待审核</el-radio-button>
        <el-radio-button value="APPROVED">已通过</el-radio-button>
        <el-radio-button value="REJECTED">已驳回</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="realName" label="真实姓名" width="100" />
      <el-table-column prop="contactMobile" label="联系手机" width="130" />
      <el-table-column label="游戏能力" min-width="200">
        <template #default="{ row }">
          <el-tag v-for="(cap, i) in row.capabilities" :key="i" size="small" class="tag-mini">
            {{ cap.gameName ?? cap.gameId }} · {{ cap.rank }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="introduction" label="自我介绍" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.auditStatus]?.type" size="small">
            {{ statusMap[row.auditStatus]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核意见" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.auditReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <template v-if="row.auditStatus === 'PENDING'">
            <el-button size="small" type="success" @click="handleAudit(row, true)">通过</el-button>
            <el-button size="small" type="danger" @click="handleAudit(row, false)">驳回</el-button>
          </template>
          <span v-else class="done">已处理</span>
        </template>
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
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.tag-mini {
  margin-right: 4px;
}
.done {
  color: #909399;
  font-size: 13px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
