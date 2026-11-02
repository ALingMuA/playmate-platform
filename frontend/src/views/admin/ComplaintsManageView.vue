<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminComplaintDetail, adminComplaints, adminHandleComplaint, type Complaint } from '@/api/review'
import { loadPrivateImage } from '@/api/file'

const list = ref<Complaint[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const listError = ref('')
const statusFilter = ref('PENDING')
const typeFilter = ref('')
const keyword = ref('')

const typeOptions = [
  { value: 'NO_FULFILLMENT', label: '未履约' },
  { value: 'LATE', label: '迟到' },
  { value: 'ATTITUDE', label: '态度问题' },
  { value: 'MISMATCH', label: '服务不符' },
  { value: 'OTHER', label: '其他' },
]
const statusMap: Record<string, { label: string; type: 'info' | 'warning' | 'success' }> = {
  PENDING: { label: '待处理', type: 'info' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已处理', type: 'success' },
}
const resolutionMap: Record<string, string> = {
  '': '-',
  KEEP: '维持订单',
  FULL_REFUND: '全额退款',
  PARTIAL_REFUND: '部分退款',
}

// 详情抽屉
const detailVisible = ref(false)
const current = ref<Complaint | null>(null)
const deliveryImageUrls = ref<Record<string, string>>({})
const deliveryImageErrors = ref<Record<string, boolean>>({})

// 处理对话框
const handleVisible = ref(false)
const handling = ref(false)
const handleForm = reactive({
  resolutionType: 'KEEP' as 'KEEP' | 'FULL_REFUND' | 'PARTIAL_REFUND',
  refundAmountYuan: 0,
  handlingOpinion: '',
})

async function load() {
  loading.value = true
  listError.value = ''
  try {
    const result = await adminComplaints(statusFilter.value, typeFilter.value, keyword.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } catch (err) {
    listError.value = err instanceof Error ? err.message : '投诉列表加载失败，请重试。'
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

onMounted(load)

async function openDetail(row: Complaint) {
  try {
    current.value = await adminComplaintDetail(row.id)
    deliveryImageUrls.value = {}
    deliveryImageErrors.value = {}
    await Promise.all((current.value.deliveryEvidences ?? []).map(async (evidence) => {
      try {
        deliveryImageUrls.value[evidence.fileUrl] = await loadPrivateImage(evidence.fileUrl)
      } catch {
        deliveryImageErrors.value[evidence.fileUrl] = true
      }
    }))
    detailVisible.value = true
  } catch {
    // 请求错误由 HTTP 拦截器提示，保留当前列表状态。
  }
}

function openHandle(row: Complaint) {
  current.value = row
  handleForm.resolutionType = 'KEEP'
  handleForm.refundAmountYuan = row.orderTotalAmountCents / 100
  handleForm.handlingOpinion = ''
  handleVisible.value = true
}

async function handleSubmit() {
  if (!current.value) return
  if (!handleForm.handlingOpinion.trim()) {
    ElMessage.warning('请填写处理意见')
    return
  }
  if (handleForm.resolutionType === 'PARTIAL_REFUND') {
    const refundAmountCents = Math.round(Number(handleForm.refundAmountYuan.toFixed(2)) * 100)
    if (!Number.isSafeInteger(refundAmountCents) || refundAmountCents <= 0) {
      ElMessage.warning('请填写退款金额')
      return
    }
    if (refundAmountCents > current.value.orderTotalAmountCents) {
      ElMessage.warning('退款金额不能超过订单金额')
      return
    }
  }
  const label = { KEEP: '维持订单', FULL_REFUND: '全额退款', PARTIAL_REFUND: '部分退款' }[handleForm.resolutionType]
  const refundAmountCents = Math.round(Number(handleForm.refundAmountYuan.toFixed(2)) * 100)
  try {
    await ElMessageBox.confirm(
      `确认以「${label}」处理该投诉${handleForm.resolutionType === 'PARTIAL_REFUND' ? `，退款 ¥${(refundAmountCents / 100).toFixed(2)}` : ''}？处理后将生成资金流水且不可撤销。`,
      '投诉仲裁',
      { type: 'warning' },
    )
  } catch {
    return
  }
  handling.value = true
  try {
    await adminHandleComplaint(current.value.id, {
      resolutionType: handleForm.resolutionType,
      refundAmountCents: handleForm.resolutionType === 'PARTIAL_REFUND' ? refundAmountCents : undefined,
      handlingOpinion: handleForm.handlingOpinion.trim(),
    })
    ElMessage.success('投诉已处理')
    handleVisible.value = false
    detailVisible.value = false
    await load()
  } finally {
    handling.value = false
  }
}

const fmtMoney = (cents: number) => '¥' + (cents / 100).toFixed(2)
</script>

<template>
  <div class="complaints-manage">
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" @change="onFilterChange">
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="PROCESSING">处理中</el-radio-button>
        <el-radio-button value="RESOLVED">已处理</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <el-select v-model="typeFilter" placeholder="投诉类型" clearable style="width: 130px" @change="onFilterChange">
        <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-input v-model="keyword" placeholder="按投诉说明搜索" clearable style="width: 200px" @keyup.enter="onFilterChange" @clear="onFilterChange" />
    </div>

    <el-alert v-if="listError" type="error" :title="listError" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>
    <el-table v-if="!listError" :data="list" v-loading="loading" border stripe>
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="serviceTitleSnapshot" label="服务" min-width="140" show-overflow-tooltip />
      <el-table-column label="投诉人" width="110">
        <template #default="{ row }">{{ row.complainantNickname }}</template>
      </el-table-column>
      <el-table-column label="被投诉陪玩师" width="120">
        <template #default="{ row }">{{ row.companionNickname }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ typeOptions.find((t) => t.value === row.complaintType)?.label ?? row.complaintType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="投诉说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.complaintStatus]?.type" size="small">{{ statusMap[row.complaintStatus]?.label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理结果" width="110">
        <template #default="{ row }">
          <span v-if="row.complaintStatus === 'RESOLVED'">
            {{ resolutionMap[row.resolutionType] ?? '-' }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button v-if="['PENDING', 'PROCESSING'].includes(row.complaintStatus)" size="small" type="primary" @click="openHandle(row)">
            处理
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

    <!-- 投诉详情抽屉 -->
    <el-drawer v-model="detailVisible" title="投诉详情" size="480px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="服务">{{ current.serviceTitleSnapshot }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ fmtMoney(current.orderTotalAmountCents) }}</el-descriptions-item>
        <el-descriptions-item label="投诉人">{{ current.complainantNickname }}</el-descriptions-item>
        <el-descriptions-item label="被投诉陪玩师">{{ current.companionNickname }}</el-descriptions-item>
        <el-descriptions-item label="投诉类型">
          {{ typeOptions.find((t) => t.value === current?.complaintType)?.label ?? current?.complaintType ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="投诉说明">{{ current.description }}</el-descriptions-item>
        <el-descriptions-item label="证据">
          <div v-if="current.evidences?.length" class="evidence-list">
            <el-image
              v-for="ev in current.evidences"
              :key="ev.id"
              :src="ev.fileUrl"
              fit="cover"
              class="evidence-img"
              :preview-src-list="current.evidences.map((e) => e.fileUrl)"
              preview-teleported
            />
          </div>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="陪玩师履约凭证">
          <div v-if="current.deliveryEvidences?.length" class="evidence-list">
            <template v-for="evidence in current.deliveryEvidences" :key="evidence.id">
              <el-image
                v-if="deliveryImageUrls[evidence.fileUrl]"
                :src="deliveryImageUrls[evidence.fileUrl]"
                fit="cover"
                class="evidence-img"
                :preview-src-list="Object.values(deliveryImageUrls)"
                preview-teleported
              />
              <span v-else>{{ deliveryImageErrors[evidence.fileUrl] ? '图片加载失败' : '凭证加载中…' }}</span>
            </template>
          </div>
          <span v-else>陪玩师未上传履约凭证</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusMap[current.complaintStatus]?.label }}</el-descriptions-item>
        <el-descriptions-item v-if="current?.complaintStatus === 'RESOLVED'" label="处理结果">
          {{ resolutionMap[current?.resolutionType ?? ''] ?? '-' }}
          <span v-if="current?.resolutionType === 'PARTIAL_REFUND'">（{{ fmtMoney(current?.refundAmountCents ?? 0) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.handlingOpinion" label="处理意见">{{ current.handlingOpinion }}</el-descriptions-item>
        <el-descriptions-item label="投诉时间">{{ current.createdAt?.slice(0, 16) }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="current && ['PENDING', 'PROCESSING'].includes(current.complaintStatus)" #footer>
        <el-button type="primary" @click="handleVisible = true; detailVisible = false">去处理</el-button>
      </template>
    </el-drawer>

    <!-- 仲裁处理对话框（FR-M18/M19） -->
    <el-dialog v-model="handleVisible" title="投诉仲裁" width="520px">
      <el-form label-width="90px">
        <el-form-item label="处理方式" required>
          <el-radio-group v-model="handleForm.resolutionType">
            <el-radio value="KEEP">维持订单</el-radio>
            <el-radio value="FULL_REFUND">全额退款</el-radio>
            <el-radio value="PARTIAL_REFUND">部分退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handleForm.resolutionType === 'PARTIAL_REFUND'" label="退款金额" required>
          <el-input-number v-model="handleForm.refundAmountYuan" :min="0.01" :max="current ? current.orderTotalAmountCents / 100 : 0.01" :precision="2" :step="1" style="width: 200px" />
          <span class="hint">元（当前订单 {{ fmtMoney(current ? current.orderTotalAmountCents : 0) }}）</span>
        </el-form-item>
        <el-form-item label="处理意见" required>
          <el-input v-model="handleForm.handlingOpinion" type="textarea" :rows="3" maxlength="1000" show-word-limit
            placeholder="处理意见将展示给投诉人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="handleSubmit">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.evidence-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.evidence-img {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}
.hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
