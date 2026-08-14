<script setup lang="ts">
/**
 * 管理后台 - 客服账号管理（FR-C01~C05）。
 *
 * <p>管理员创建客服账号（不得从前台注册）、查询、启用/禁用（禁用必填原因）、
 * 重置密码（重置后原会话失效，客服下次登录必须修改密码，FR-C04）。
 * 相关操作自动记录操作日志（FR-C05）。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateCsAccount,
  adminCsAccounts,
  adminResetCsPassword,
  adminSetCsAccountStatus,
  type CsAccountView,
} from '@/api/cs'

const list = ref<CsAccountView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const query = reactive({
  csAccount: '',
  csName: '',
  accountStatus: undefined as string | undefined,
})

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

const workStatusMap: Record<string, string> = {
  ONLINE: '在线',
  BUSY: '忙碌',
  OFFLINE: '离线',
}

async function load() {
  loading.value = true
  try {
    const result = await adminCsAccounts(
      query.csAccount || undefined,
      query.csName || undefined,
      query.accountStatus,
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
  query.csAccount = ''
  query.csName = ''
  query.accountStatus = undefined
  page.value = 1
  load()
}

// ==================== 创建客服账号（FR-C01） ====================

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  csAccount: '',
  csName: '',
  contactMobile: '',
  initialPassword: '',
})

function openCreate() {
  createForm.csAccount = ''
  createForm.csName = ''
  createForm.contactMobile = ''
  createForm.initialPassword = ''
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.csAccount.trim() || !createForm.csName.trim()) {
    ElMessage.warning('请填写客服账号与姓名')
    return
  }
  if (!createForm.contactMobile.trim()) {
    ElMessage.warning('请填写联系方式')
    return
  }
  if (createForm.initialPassword.length < 8) {
    ElMessage.warning('初始密码至少 8 位')
    return
  }
  creating.value = true
  try {
    await adminCreateCsAccount({
      csAccount: createForm.csAccount.trim(),
      csName: createForm.csName.trim(),
      contactMobile: createForm.contactMobile.trim(),
      initialPassword: createForm.initialPassword,
    })
    ElMessage.success('客服账号已创建，客服首次登录必须修改密码')
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

// ==================== 禁用/启用（FR-C03） ====================

async function handleDisable(row: CsAccountView) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '禁用后该客服立即禁止登录、终止已有会话并停止接收新咨询。请填写禁用原因（必填）。',
      `禁用客服 ${row.csName}`,
      {
        confirmButtonText: '确认禁用',
        cancelButtonText: '取消',
        inputPlaceholder: '禁用原因（不超过500字）',
        inputValidator: (v: string) => (v ?? '').trim().length > 0 || '禁用原因必填',
      },
    )
    reason = result.value.trim()
  } catch {
    return
  }
  await adminSetCsAccountStatus(row.id, 'DISABLED', reason)
  ElMessage.success('客服账号已禁用')
  await load()
}

async function handleEnable(row: CsAccountView) {
  await ElMessageBox.confirm(`确定恢复客服 ${row.csName} 的登录权限吗？`, '启用客服账号', { type: 'info' })
  await adminSetCsAccountStatus(row.id, 'ENABLED')
  ElMessage.success('客服账号已启用')
  await load()
}

// ==================== 重置密码（FR-C04） ====================

async function handleResetPassword(row: CsAccountView) {
  let newPassword = ''
  try {
    const result = await ElMessageBox.prompt(
      '重置后该客服当前登录会话立即失效，下次登录必须使用新密码并强制修改。',
      `重置 ${row.csName} 的密码`,
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputPlaceholder: '设置临时密码（8~32位）',
        inputValidator: (v: string) => (v ?? '').length >= 8 || '密码至少 8 位',
      },
    )
    newPassword = result.value
  } catch {
    return
  }
  await adminResetCsPassword(row.id, newPassword)
  ElMessage.success('密码已重置，客服下次登录须修改密码')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="cs-accounts-page">
    <div class="page-header">
      <h1>客服账号管理</h1>
      <p class="sub">客服账号由管理员创建，不得从前台注册；操作自动记录日志</p>
    </div>

    <!-- 查询区 -->
    <el-card class="query-card" shadow="hover">
      <el-form :inline="true" @submit.prevent="onSearch">
        <el-form-item label="客服账号">
          <el-input v-model="query.csAccount" placeholder="账号" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="客服姓名">
          <el-input v-model="query.csName" placeholder="姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="账号状态">
          <el-select v-model="query.accountStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="success" @click="openCreate">新建客服账号</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 账号表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="csAccount" label="账号" min-width="120" />
        <el-table-column prop="csName" label="姓名" min-width="100" />
        <el-table-column prop="contactMobile" label="联系方式" min-width="120">
          <template #default="{ row }">{{ row.contactMobile || '-' }}</template>
        </el-table-column>
        <el-table-column label="工作状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.workStatus === 'ONLINE' ? 'success' : 'info'" size="small">
              {{ workStatusMap[row.workStatus] ?? row.workStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="强制改密" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.forceChangePassword === 1" type="danger" size="small">待改密</el-tag>
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
        <el-table-column label="创建时间" width="130">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.accountStatus === 'ENABLED'" size="small" type="danger" plain
              @click="handleDisable(row)">
              禁用
            </el-button>
            <el-button v-else size="small" type="success" plain @click="handleEnable(row)">启用</el-button>
            <el-button v-if="row.accountStatus === 'ENABLED'" size="small" type="warning" plain
              @click="handleResetPassword(row)">
              重置密码
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

    <!-- 新建对话框 -->
    <el-dialog v-model="createVisible" title="新建客服账号" width="500px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="客服账号" required>
          <el-input v-model="createForm.csAccount" maxlength="32" placeholder="4~32 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="客服姓名" required>
          <el-input v-model="createForm.csName" maxlength="32" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="createForm.contactMobile" maxlength="20" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="createForm.initialPassword" type="password" show-password placeholder="8~32 位" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
        title="创建后客服首次登录必须修改密码（FR-C04），请将账号与初始密码线下告知客服" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
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
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
