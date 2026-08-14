<script setup lang="ts">
/**
 * 管理后台 - AI 知识库管理（FR-C23）。
 *
 * <p>维护 AI 客服的常见问题与标准答案（分类、关键词、优先级），
 * 仅启用（enabled=1）的条目参与 AI 检索；未命中可靠答案时 AI 自动转人工（FR-C17/C19）。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCreateKnowledge,
  adminDeleteKnowledge,
  adminKnowledgeList,
  adminSetKnowledgeEnabled,
  adminUpdateKnowledge,
  type KnowledgeBaseView,
} from '@/api/admin'

const list = ref<KnowledgeBaseView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const categoryFilter = ref<string | undefined>(undefined)
const enabledFilter = ref<number | undefined>(undefined)

/** 常见知识分类 */
const CATEGORY_OPTIONS = ['注册登录', '服务预约', '订单状态', '取消规则', '评价投诉', '平台规范', '其他']

async function load() {
  loading.value = true
  try {
    const result = await adminKnowledgeList(categoryFilter.value, enabledFilter.value, page.value, pageSize)
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

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

// ==================== 新增/编辑 ====================

const editVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: 0,
  category: '注册登录',
  title: '',
  keywords: '',
  standardAnswer: '',
  priority: 1,
})

function openCreate() {
  form.id = 0
  form.category = '注册登录'
  form.title = ''
  form.keywords = ''
  form.standardAnswer = ''
  form.priority = 1
  editVisible.value = true
}

function openEdit(row: KnowledgeBaseView) {
  form.id = row.id
  form.category = row.category
  form.title = row.title
  form.keywords = row.keywords
  form.standardAnswer = row.standardAnswer
  form.priority = row.priority
  editVisible.value = true
}

async function handleSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入问题标题')
    return
  }
  if (!form.keywords.trim()) {
    ElMessage.warning('请输入关键词')
    return
  }
  if (!form.standardAnswer.trim()) {
    ElMessage.warning('请输入标准答案')
    return
  }
  saving.value = true
  try {
    const payload = {
      category: form.category,
      title: form.title.trim(),
      keywords: form.keywords.trim(),
      standardAnswer: form.standardAnswer.trim(),
      priority: form.priority,
    }
    if (form.id) {
      await adminUpdateKnowledge(form.id, payload)
      ElMessage.success('知识条目已更新')
    } else {
      await adminCreateKnowledge(payload)
      ElMessage.success('知识条目已新增')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 启停/删除 ====================

async function handleToggleEnabled(row: KnowledgeBaseView) {
  await adminSetKnowledgeEnabled(row.id, row.enabled !== 1)
  ElMessage.success(row.enabled !== 1 ? '已启用，参与 AI 检索' : '已停用')
  await load()
}

async function handleDelete(row: KnowledgeBaseView) {
  await ElMessageBox.confirm(`确定删除知识条目「${row.title}」吗？`, '删除知识条目', { type: 'warning' })
  await adminDeleteKnowledge(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h1>AI 知识库管理</h1>
      <p class="sub">维护 AI 客服的常见问题与标准答案（仅启用的条目参与检索）</p>
    </div>

    <!-- 工具栏 -->
    <el-card class="toolbar-card" shadow="hover">
      <div class="toolbar">
        <el-select v-model="categoryFilter" placeholder="全部分类" clearable style="width: 160px" @change="onFilterChange">
          <el-option v-for="c in CATEGORY_OPTIONS" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="enabledFilter" placeholder="全部状态" clearable style="width: 140px" @change="onFilterChange">
          <el-option label="已启用" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增知识条目</el-button>
      </div>
    </el-card>

    <!-- 知识表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="问题" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="160" show-overflow-tooltip />
        <el-table-column prop="standardAnswer" label="标准答案" min-width="240" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="130">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.enabled === 1 ? 'warning' : 'success'" plain
              @click="handleToggleEnabled(row)">
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="editVisible" :title="form.id ? '编辑知识条目' : '新增知识条目'" width="620px">
      <el-form label-width="90px">
        <el-form-item label="知识分类" required>
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in CATEGORY_OPTIONS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：如何取消已支付的订单？" />
        </el-form-item>
        <el-form-item label="关键词" required>
          <el-input v-model="form.keywords" maxlength="1000" placeholder="逗号分隔，如：取消订单,退款,取消预约" />
        </el-form-item>
        <el-form-item label="标准答案" required>
          <el-input v-model="form.standardAnswer" type="textarea" :rows="5" maxlength="4000" show-word-limit
            placeholder="AI 客服据此回答用户问题" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="100" />
          <span class="priority-tip">数值越大越优先命中</span>
        </el-form-item>
      </el-form>
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
  flex-wrap: wrap;
}
.table-card {
  min-height: 400px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.priority-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
