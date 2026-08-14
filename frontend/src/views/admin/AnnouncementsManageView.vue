<script setup lang="ts">
/**
 * 管理后台 - 公告管理（FR-M13）。
 *
 * <p>公告状态机：DRAFT → PUBLISHED → REVOKED；仅草稿可编辑/删除，仅已发布可撤回。
 * 新增公告默认为草稿，发布后游客可在用户端查看（FR-A08）。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminAnnouncements,
  adminCreateAnnouncement,
  adminDeleteAnnouncement,
  adminPublishAnnouncement,
  adminRevokeAnnouncement,
  adminUpdateAnnouncement,
  type AnnouncementView,
} from '@/api/admin'

const list = ref<AnnouncementView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const status = ref<string | undefined>(undefined)

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  REVOKED: { label: '已撤回', type: 'warning' },
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  try {
    const result = await adminAnnouncements(status.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onStatusChange() {
  page.value = 1
  load()
}

// ==================== 新增/编辑 ====================

const editVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: 0, title: '', content: '' })

function openCreate() {
  form.id = 0
  form.title = ''
  form.content = ''
  editVisible.value = true
}

function openEdit(row: AnnouncementView) {
  form.id = row.id
  form.title = row.title
  form.content = row.content
  editVisible.value = true
}

async function handleSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  saving.value = true
  try {
    const payload = { title: form.title.trim(), content: form.content.trim() }
    if (form.id) {
      await adminUpdateAnnouncement(form.id, payload)
      ElMessage.success('公告已更新')
    } else {
      await adminCreateAnnouncement(payload)
      ElMessage.success('公告已创建（草稿）')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 发布/撤回/删除 ====================

async function handlePublish(row: AnnouncementView) {
  await ElMessageBox.confirm(`发布后所有用户可见，确定发布公告「${row.title}」吗？`, '发布公告', { type: 'info' })
  await adminPublishAnnouncement(row.id)
  ElMessage.success('公告已发布')
  await load()
}

async function handleRevoke(row: AnnouncementView) {
  await ElMessageBox.confirm('撤回后用户端不再展示该公告。', '撤回公告', { type: 'warning' })
  await adminRevokeAnnouncement(row.id)
  ElMessage.success('公告已撤回')
  await load()
}

async function handleDelete(row: AnnouncementView) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？删除后不可恢复。`, '删除公告', { type: 'warning' })
  await adminDeleteAnnouncement(row.id)
  ElMessage.success('公告已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="announcements-page">
    <div class="page-header">
      <h1>公告管理</h1>
      <p class="sub">发布平台规则、维护与活动公告（仅已发布对用户可见）</p>
    </div>

    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="hover">
      <div class="toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="onStatusChange">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已撤回" value="REVOKED" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增公告</el-button>
      </div>
    </el-card>

    <!-- 公告表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.publishStatus]?.type as any) ?? 'info'" size="small">
              {{ statusMap[row.publishStatus]?.label ?? row.publishStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="130">
          <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="130">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.publishStatus === 'DRAFT'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.publishStatus === 'DRAFT'" size="small" type="success" @click="handlePublish(row)">
              发布
            </el-button>
            <el-button v-if="row.publishStatus === 'PUBLISHED'" size="small" type="warning" @click="handleRevoke(row)">
              撤回
            </el-button>
            <el-button v-if="row.publishStatus === 'DRAFT'" size="small" type="danger" @click="handleDelete(row)">
              删除
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑公告' : '新增公告'" width="620px">
      <el-form label-width="70px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="8" maxlength="5000" show-word-limit
            placeholder="公告正文内容" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
        title="保存后为草稿状态，需手动发布后用户才可见" class="save-tip" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
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
.toolbar-card {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
}
.table-card {
  min-height: 400px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.save-tip {
  margin-top: 8px;
}
</style>
