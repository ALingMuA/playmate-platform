<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listGames, listServiceTypes, listTags, type Game, type ServiceType, type TagView } from '@/api/catalog'
import {
  createService,
  listMyServices,
  setServiceShelf,
  updateService,
  type CompanionService,
  type ServicePayload,
} from '@/api/companion'
import type { PageResult } from '@/api/http'

const games = ref<Game[]>([])
const types = ref<ServiceType[]>([])
const tags = ref<TagView[]>([])
const list = ref<CompanionService[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  gameId: undefined as number | undefined,
  serviceTypeId: undefined as number | undefined,
  title: '',
  description: '',
  tagIds: [] as number[],
  priceYuan: 20,
  minDurationMinutes: 60,
})

async function load() {
  loading.value = true
  try {
    const result: PageResult<CompanionService> = await listMyServices(page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadCatalog() {
  games.value = await listGames()
  types.value = await listServiceTypes()
  tags.value = await listTags()
}

onMounted(() => {
  load()
  loadCatalog()
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    gameId: undefined,
    serviceTypeId: undefined,
    title: '',
    description: '',
    tagIds: [],
    priceYuan: 20,
    minDurationMinutes: 60,
  })
  dialogVisible.value = true
}

function openEdit(row: CompanionService) {
  editingId.value = row.id
  Object.assign(form, {
    gameId: row.gameId,
    serviceTypeId: row.serviceTypeId,
    title: row.title,
    description: row.description,
    tagIds: [...row.tagIds],
    priceYuan: row.priceCents / 100,
    minDurationMinutes: row.minDurationMinutes,
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.gameId || !form.serviceTypeId || !form.title) {
    ElMessage.warning('请填写游戏、服务类型与标题')
    return
  }
  if (form.priceYuan <= 0) {
    ElMessage.warning('价格必须大于 0')
    return
  }
  const payload: ServicePayload = {
    gameId: form.gameId,
    serviceTypeId: form.serviceTypeId,
    title: form.title,
    description: form.description,
    tagIds: form.tagIds,
    priceCents: Math.round(form.priceYuan * 100),
    minDurationMinutes: form.minDurationMinutes,
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await createService(payload)
      ElMessage.success('服务已创建，等待审核')
    } else {
      await updateService(editingId.value, payload)
      ElMessage.success('服务已更新，修改内容需重新审核')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleShelf(row: CompanionService) {
  const onShelf = row.serviceStatus !== 'ON_SHELF'
  if (onShelf) {
    await ElMessageBox.confirm('上架后用户即可预约该服务，确认上架？', '上架确认', { type: 'warning' })
  } else {
    await ElMessageBox.confirm('下架后不再产生新订单，已有订单不受影响。确认下架？', '下架确认', { type: 'warning' })
  }
  await setServiceShelf(row.id, onShelf)
  ElMessage.success(onShelf ? '已上架' : '已下架')
  await load()
}

const auditTag: Record<string, { label: string; type: 'info' | 'success' | 'danger' }> = {
  PENDING: { label: '待审核', type: 'info' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}
const shelfTag: Record<string, { label: string; type: 'success' | 'info' }> = {
  ON_SHELF: { label: '已上架', type: 'success' },
  OFF_SHELF: { label: '已下架', type: 'info' },
}
</script>

<template>
  <div class="service-manage">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增服务</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="title" label="服务标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="gameName" label="游戏" width="110" />
      <el-table-column prop="serviceTypeName" label="类型" width="100" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">¥{{ (row.priceCents / 100).toFixed(2) }}/小时</template>
      </el-table-column>
      <el-table-column prop="minDurationMinutes" label="最小时长" width="90" />
      <el-table-column label="标签" min-width="140">
        <template #default="{ row }">
          <el-tag v-for="name in row.tagNames" :key="name" size="small" class="tag-mini">{{ name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" width="90">
        <template #default="{ row }">
          <el-tooltip v-if="row.auditReason" :content="row.auditReason" placement="top">
            <el-tag :type="auditTag[row.auditStatus]?.type" size="small">
              {{ auditTag[row.auditStatus]?.label }}
            </el-tag>
          </el-tooltip>
          <el-tag v-else :type="auditTag[row.auditStatus]?.type" size="small">
            {{ auditTag[row.auditStatus]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="shelfTag[row.serviceStatus]?.type" size="small">
            {{ shelfTag[row.serviceStatus]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.serviceStatus === 'ON_SHELF' ? 'warning' : 'success'"
            :disabled="row.auditStatus !== 'APPROVED' && row.serviceStatus !== 'ON_SHELF'"
            @click="handleShelf(row)">
            {{ row.serviceStatus === 'ON_SHELF' ? '下架' : '上架' }}
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

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增服务' : '编辑服务'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="游戏" required>
          <el-select v-model="form.gameId" placeholder="选择游戏" style="width: 100%">
            <el-option v-for="g in games" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务类型" required>
          <el-select v-model="form.serviceTypeId" placeholder="选择服务类型" style="width: 100%">
            <el-option v-for="t in types" :key="t.id" :label="t.typeName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：王者荣耀 荣耀王者 带飞上分" />
        </el-form-item>
        <el-form-item label="服务说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tagIds" multiple collapse-tags placeholder="选择标签" style="width: 100%">
            <el-option v-for="t in tags" :key="t.id" :label="t.tagName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格(元/小时)" required>
          <el-input-number v-model="form.priceYuan" :min="1" :max="9999" :precision="2" :step="5" />
        </el-form-item>
        <el-form-item label="最小时长(分钟)" required>
          <el-input-number v-model="form.minDurationMinutes" :min="30" :max="600" :step="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.tag-mini {
  margin-right: 4px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
