<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { AvailabilityTemplate } from '@/api/companion'

const props = defineProps<{
  templates?: AvailabilityTemplate[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'open-manage'): void
  (e: 'create-routine'): void
  (e: 'edit-template', template: AvailabilityTemplate): void
  (e: 'delete-template', id: number): void
  (e: 'toggle-status', id: number, enabled: boolean): void
}>()

const templateList = computed(() => props.templates || [])
const activeTemplates = computed(() => templateList.value.filter((t) => t.enabled))
const pausedTemplates = computed(() => templateList.value.filter((t) => !t.enabled))
const isConfigured = computed(() => templateList.value.length > 0)
const showPaused = ref(false)

function handleDelete(t: AvailabilityTemplate) {
  ElMessageBox.confirm(
    `删除作息规则「${t.templateName}」将清理未来由该规则自动生成的未预约档期（已产生的有效订单不受影响）。确认删除？`,
    '删除常规作息',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    emit('delete-template', t.id)
  }).catch(() => {})
}
</script>

<template>
  <el-card class="routine-card" shadow="never">
    <div class="card-inner">
      <div class="card-header-bar">
        <div class="header-left">
          <span class="card-title">我的长期作息</span>
          <el-tag v-if="!isConfigured" type="info" size="small">未设置</el-tag>
          <template v-else>
            <el-tag type="success" size="small" effect="dark">
              运行中 {{ activeTemplates.length }} 条
            </el-tag>
            <el-tag v-if="pausedTemplates.length > 0" type="info" size="small" effect="plain">
              已暂停 {{ pausedTemplates.length }} 条
            </el-tag>
          </template>
        </div>

        <div class="header-actions">
          <el-button type="primary" plain size="small" @click="emit('create-routine')">
            + 添加作息规则
          </el-button>
          <el-button v-if="isConfigured" size="small" @click="emit('open-manage')">
            管理长期作息
          </el-button>
        </div>
      </div>

      <!-- 规则列表 -->
      <div v-if="isConfigured" class="rule-list">
        <!-- 运行中的规则 -->
        <div
          v-for="t in activeTemplates"
          :key="t.id"
          class="rule-row"
        >
          <div class="rule-left">
            <span class="status-dot is-active"></span>
            <span class="rule-name">{{ t.templateName }}</span>
            <span class="rule-text">{{ t.ruleText }}</span>
            <span class="rule-validity">
              {{ t.effectiveUntil ? `截至 ${t.effectiveUntil}` : '长期有效' }}
            </span>
          </div>
          <div class="rule-right">
            <el-switch
              :model-value="t.enabled"
              :loading="loading"
              inline-prompt
              active-text="开"
              inactive-text="关"
              size="small"
              @change="emit('toggle-status', t.id, Boolean($event))"
            />
            <el-button type="primary" link size="small" @click="emit('edit-template', t)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(t)">
              删除
            </el-button>
          </div>
        </div>

        <!-- 暂停中的规则（支持折叠/展开） -->
        <div v-if="pausedTemplates.length > 0" class="paused-section">
          <div class="paused-toggle" @click="showPaused = !showPaused">
            <span class="toggle-text">已暂停规则（{{ pausedTemplates.length }} 条）</span>
            <el-button link type="info" size="small">
              {{ showPaused ? '收起' : '展开' }}
            </el-button>
          </div>

          <div v-show="showPaused" class="paused-list">
            <div
              v-for="t in pausedTemplates"
              :key="t.id"
              class="rule-row is-paused-row"
            >
              <div class="rule-left">
                <span class="status-dot is-paused"></span>
                <span class="rule-name">{{ t.templateName }}</span>
                <span class="rule-text">{{ t.ruleText }}</span>
                <span class="rule-validity">
                  {{ t.effectiveUntil ? `截至 ${t.effectiveUntil}` : '长期有效' }}
                </span>
                <el-tag size="small" type="danger" effect="plain">已暂停</el-tag>
              </div>
              <div class="rule-right">
                <el-switch
                  :model-value="t.enabled"
                  :loading="loading"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                  size="small"
                  @change="emit('toggle-status', t.id, Boolean($event))"
                />
                <el-button type="primary" link size="small" @click="emit('edit-template', t)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="handleDelete(t)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空白引导 -->
      <div v-else class="empty-routine-hint">
        <span class="empty-hint">暂无长期作息规则。设置常规接单作息后，系统将自动每日滚动安排未来 28 天，省去每天手动配置。</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.routine-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background-color: #fff;
  margin-bottom: 16px;
}

:deep(.el-card__body) {
  padding: 16px 20px;
}

.card-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;
}

.rule-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background-color: #f8fafc;
  border-radius: 6px;
  border: 1px solid #edf2f7;
  flex-wrap: wrap;
  gap: 10px;
}

.rule-row.is-paused-row {
  background-color: #fafafa;
  opacity: 0.85;
}

.rule-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.is-active {
  background-color: #2ca66f;
  box-shadow: 0 0 0 2px rgba(44, 166, 111, 0.2);
}

.status-dot.is-paused {
  background-color: #909399;
}

.rule-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.rule-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.rule-validity {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rule-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.paused-section {
  margin-top: 4px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 8px;
}

.paused-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  margin-bottom: 6px;
}

.toggle-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.paused-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-routine-hint {
  padding: 6px 0;
}

.empty-hint {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
</style>
