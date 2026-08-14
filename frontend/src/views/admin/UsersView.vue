<script setup lang="ts">
/**
 * 管理后台 - 用户管理（FR-M03/M04）。
 *
 * <p>按账号/昵称/手机号关键词与账号状态查询用户（分页），
 * 可对违规用户执行启用/禁用（禁用必须填写原因，FR-M04）。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminSetUserStatus, adminUsers, type UserAdminView } from '@/api/admin'

const list = ref<UserAdminView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const query = reactive({
  keyword: '',
  accountStatus: undefined as string | undefined,
})

const genderMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' }

const roleMap: Record<string, string> = {
  USER: '普通用户',
  COMPANION: '陪玩师',
  CUSTOMER_SERVICE: '客服',
  ADMIN: '管理员',
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  try {
    const result = await adminUsers(query.keyword || undefined, query.accountStatus, page.value, pageSize)
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
  query.keyword = ''
  query.accountStatus = undefined
  page.value = 1
  load()
}

// ==================== 启用/禁用（FR-M04） ====================

async function handleDisable(row: UserAdminView) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('禁用后该用户将无法登录，请填写禁用原因（必填）。', `禁用用户 ${row.username}`, {
      confirmButtonText: '确认禁用',
      cancelButtonText: '取消',
      inputPlaceholder: '禁用原因（不超过500字）',
      inputValidator: (v: string) => (v ?? '').trim().length > 0 || '禁用原因必填',
    })
    reason = result.value.trim()
  } catch {
    return // 用户取消
  }
  await adminSetUserStatus(row.id, 'DISABLED', reason)
  ElMessage.success('用户已禁用')
  await load()
}

async function handleEnable(row: UserAdminView) {
  try {
    await ElMessageBox.confirm(`确定恢复用户 ${row.username} 的登录权限吗？`, '启用用户', { type: 'info' })
  } catch {
    return
  }
  await adminSetUserStatus(row.id, 'ENABLED')
  ElMessage.success('用户已启用')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="users-page">
    <div class="page-header">
      <h1>用户管理</h1>
      <p class="sub">查询用户资料，管理账号启用/禁用状态</p>
    </div>

    <!-- 查询区 -->
    <el-card class="query-card" shadow="hover">
      <el-form :inline="true" @submit.prevent="onSearch">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="账号 / 昵称 / 手机号" clearable style="width: 220px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-select v-model="query.accountStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="正常" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 用户表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="账号" min-width="110" />
        <el-table-column prop="nickname" label="昵称" min-width="110" />
        <el-table-column prop="mobile" label="手机号" min-width="120">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="150">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ genderMap[row.gender] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles ?? []" :key="r" size="small" effect="plain" class="role-tag">
              {{ roleMap[r] ?? r }}
            </el-tag>
            <span v-if="!row.roles?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="陪玩师认证" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.companionCertificationStatus" size="small"
              :type="row.companionCertificationStatus === 'APPROVED' ? 'success' : 'info'">
              {{ row.companionCertificationStatus === 'APPROVED' ? '已认证' : row.companionCertificationStatus }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.accountStatus === 'ENABLED' ? 'success' : 'danger'" size="small">
              {{ row.accountStatus === 'ENABLED' ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="130">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="130">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.accountStatus === 'ENABLED'" size="small" type="danger" plain
              @click="handleDisable(row)">
              禁用
            </el-button>
            <el-button v-else size="small" type="success" plain @click="handleEnable(row)">
              启用
            </el-button>
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
  margin-right: 4px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
