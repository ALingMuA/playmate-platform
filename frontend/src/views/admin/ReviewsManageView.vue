<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminReviews, adminSetReviewDisplay, type Review } from '@/api/review'

const list = ref<Review[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const keyword = ref('')
const scoreFilter = ref<number | undefined>(undefined)
const statusFilter = ref('')

async function load() {
  loading.value = true
  try {
    const result = await adminReviews(keyword.value, scoreFilter.value, statusFilter.value, page.value, pageSize)
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

async function handleToggle(row: Review) {
  const hiding = row.displayStatus === 'VISIBLE'
  await ElMessageBox.confirm(
    hiding
      ? '屏蔽后该评价不再公开展示，且不计入陪玩师评分，确定屏蔽？'
      : '恢复后该评价重新公开展示，并重新参与评分计算，确定恢复？',
    hiding ? '屏蔽评价' : '恢复评价',
    { type: 'warning' },
  )
  await adminSetReviewDisplay(row.id, hiding ? 'HIDDEN' : 'VISIBLE')
  ElMessage.success(hiding ? '已屏蔽' : '已恢复')
  await load()
}

const scoreText = (s: number) => '★★★★★'.slice(0, s) + '☆☆☆☆☆'.slice(0, 5 - s)
</script>

<template>
  <div class="reviews-manage">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="按评价内容搜索" clearable style="width: 220px" @keyup.enter="onFilterChange" @clear="onFilterChange" />
      <el-select v-model="scoreFilter" placeholder="评分" clearable style="width: 110px" @change="onFilterChange">
        <el-option v-for="s in [5, 4, 3, 2, 1]" :key="s" :label="s + ' 星'" :value="s" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="展示状态" clearable style="width: 130px" @change="onFilterChange">
        <el-option label="可见" value="VISIBLE" />
        <el-option label="已屏蔽" value="HIDDEN" />
      </el-select>
      <el-button type="primary" @click="onFilterChange">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="userNickname" label="评价用户" width="120" />
      <el-table-column prop="companionUserId" label="陪玩师ID" width="100" />
      <el-table-column label="评分" width="140">
        <template #default="{ row }">
          <span class="stars">{{ scoreText(row.score) }}</span>
          <span class="score-num">{{ row.score }}.0</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" width="200">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="tag-mini">{{ t }}</el-tag>
          <span v-if="!row.tags?.length">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="评价内容" min-width="220" show-overflow-tooltip />
      <el-table-column label="展示状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.displayStatus === 'VISIBLE' ? 'success' : 'info'" size="small">
            {{ row.displayStatus === 'VISIBLE' ? '可见' : '已屏蔽' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评价时间" width="150">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            :type="row.displayStatus === 'VISIBLE' ? 'danger' : 'success'"
            size="small"
            plain
            @click="handleToggle(row)"
          >
            {{ row.displayStatus === 'VISIBLE' ? '屏蔽' : '恢复' }}
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
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.tag-mini {
  margin-right: 4px;
}
.stars {
  color: #e6a23c;
}
.score-num {
  margin-left: 6px;
  font-size: 13px;
  color: #606266;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
