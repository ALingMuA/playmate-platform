<script setup lang="ts">
/**
 * 审核管理（管理后台）：
 *   Tab 1 入驻申请审核（FR-M06）：通过后自动授予陪玩师资格并创建陪玩主页；
 *   Tab 2 服务项目审核（FR-M07）：通过后陪玩师可自行上架，服务才对用户可见。
 *
 * <p>审核意见 `reason` 在后端 `AuditRequest` 中是无条件 `@NotBlank`（注释为"驳回必填"，
 * 但 Bean Validation 不区分 approved），因此**通过时也必须提交非空 reason**，
 * 否则返回 400 VALIDATION_FAILED"驳回时必须填写原因"、审核不生效。</p>
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGames, type Game } from '@/api/catalog'
import {
  adminApplications,
  adminAudit,
  adminAuditService,
  adminServices,
  type Application,
  type CompanionService,
  type GameCapability,
} from '@/api/companion'

/** 审核意见兜底文案：后端通过时也要求非空，留空时使用该文案 */
const DEFAULT_APPROVE_REASON = '符合要求'

const activeTab = ref('application')
const games = ref<Game[]>([])

const statusMap: Record<string, { label: string; type: 'info' | 'success' | 'danger' }> = {
  PENDING: { label: '待审核', type: 'info' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

const shelfMap: Record<string, { label: string; type: 'success' | 'info' }> = {
  ON_SHELF: { label: '已上架', type: 'success' },
  OFF_SHELF: { label: '未上架', type: 'info' },
}

// ==================== 入驻申请（FR-M06） ====================

const list = ref<Application[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const statusFilter = ref('PENDING')
const loadedApplication = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await adminApplications(statusFilter.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
    loadedApplication.value = true
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

// ==================== 服务项目（FR-M07） ====================

const services = ref<CompanionService[]>([])
const svcTotal = ref(0)
const svcPage = ref(1)
const svcLoading = ref(false)
const svcFilter = ref('PENDING')
const loadedService = ref(false)

async function loadServices() {
  svcLoading.value = true
  try {
    const result = await adminServices(svcFilter.value, svcPage.value, pageSize)
    services.value = result.records
    svcTotal.value = result.total
    loadedService.value = true
  } finally {
    svcLoading.value = false
  }
}

function onSvcFilterChange() {
  svcPage.value = 1
  loadServices()
}

function onTabChange(name: string | number) {
  if (name === 'service' && !loadedService.value) {
    loadServices()
  }
}

// ==================== 展示辅助 ====================

function gameName(gameId: number): string {
  return games.value.find((g) => g.id === gameId)?.gameName ?? `游戏#${gameId}`
}

function capabilityText(caps: GameCapability[] | undefined): string {
  if (!caps || caps.length === 0) return '-'
  return caps.map((c) => `${c.gameName || gameName(c.gameId)}·${c.rank}`).join('、')
}

function fmtPrice(cents: number): string {
  return `¥${(cents / 100).toFixed(2)}`
}

// ==================== 审核交互 ====================

/** 通过：带默认审核意见的输入框，留空时使用兜底文案（后端 reason 必填） */
async function askApproveReason(subject: string): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt(subject, '通过审核', {
      type: 'info',
      inputValue: DEFAULT_APPROVE_REASON,
      inputPlaceholder: '审核意见（将展示给对方）',
      confirmButtonText: '确认通过',
      inputValidator: () => true,
    })
    return (value ?? '').trim() || DEFAULT_APPROVE_REASON
  } catch {
    // 取消弹窗：不提交审核
    return null
  }
}

/** 驳回：必须填写原因 */
async function askRejectReason(): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回', {
      inputPlaceholder: '驳回原因将展示给对方',
      inputValidator: (v: string) => (v && v.trim().length > 0 ? true : '驳回原因不能为空'),
    })
    return value
  } catch {
    return null
  }
}

async function handleAudit(row: Application, approved: boolean) {
  const reason = approved
    ? await askApproveReason(`确认通过 ${row.realName} 的入驻申请？通过后将自动授予陪玩师资格。`)
    : await askRejectReason()
  if (reason == null) return
  await adminAudit(row.id, approved, reason)
  ElMessage.success(approved ? '已通过' : '已驳回')
  await load()
}

async function handleServiceAudit(row: CompanionService, approved: boolean) {
  const reason = approved
    ? await askApproveReason(`确认通过服务「${row.title}」？通过后陪玩师可自行上架。`)
    : await askRejectReason()
  if (reason == null) return
  await adminAuditService(row.id, approved, reason)
  ElMessage.success(approved ? '已通过' : '已驳回')
  await loadServices()
}

onMounted(async () => {
  games.value = await listGames().catch(() => [])
  await load()
})
</script>

<template>
  <div class="audit-view">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 入驻申请审核（FR-M06） -->
      <el-tab-pane label="入驻申请" name="application">
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
          <el-table-column label="游戏能力" min-width="180">
            <template #default="{ row }">{{ capabilityText(row.capabilities) }}</template>
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
      </el-tab-pane>

      <!-- 服务项目审核（FR-M07） -->
      <el-tab-pane label="服务项目" name="service">
        <div class="toolbar">
          <el-radio-group v-model="svcFilter" @change="onSvcFilterChange">
            <el-radio-button value="PENDING">待审核</el-radio-button>
            <el-radio-button value="APPROVED">已通过</el-radio-button>
            <el-radio-button value="REJECTED">已驳回</el-radio-button>
            <el-radio-button value="">全部</el-radio-button>
          </el-radio-group>
          <span class="hint">审核通过后由陪玩师自行上架，上架且已过审的服务才会对用户可见</span>
        </div>

        <el-table :data="services" v-loading="svcLoading" border stripe>
          <el-table-column prop="title" label="服务标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="陪玩师" width="100">
            <template #default="{ row }">#{{ row.companionUserId }}</template>
          </el-table-column>
          <el-table-column label="游戏" width="110">
            <template #default="{ row }">{{ row.gameName || gameName(row.gameId) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              {{ row.serviceTypeName }}
              <el-tag v-if="row.serviceTypeEnabled === 0" type="warning" size="small">已停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="110">
            <template #default="{ row }">
              {{ fmtPrice(row.priceCents) }}/小时
              <div class="sub">最短 {{ row.minDurationMinutes }} 分钟</div>
            </template>
          </el-table-column>
          <el-table-column label="审核" width="90">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.auditStatus]?.type" size="small">
                {{ statusMap[row.auditStatus]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上架" width="90">
            <template #default="{ row }">
              <el-tag :type="shelfMap[row.serviceStatus]?.type" size="small">
                {{ shelfMap[row.serviceStatus]?.label ?? row.serviceStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核意见" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.auditReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="150">
            <template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.auditStatus === 'PENDING'">
                <el-button size="small" type="success" @click="handleServiceAudit(row, true)">通过</el-button>
                <el-button size="small" type="danger" @click="handleServiceAudit(row, false)">驳回</el-button>
              </template>
              <span v-else class="done">已处理</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="svcTotal"
          :page-size="pageSize"
          :current-page="svcPage"
          @current-change="(p: number) => { svcPage = p; loadServices() }"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
