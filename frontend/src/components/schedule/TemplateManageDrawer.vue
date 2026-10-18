<script setup lang="ts">
import { computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { AvailabilityTemplate } from '@/api/companion'

const props = defineProps<{
  modelValue: boolean
  templates?: AvailabilityTemplate[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'edit-template', template: AvailabilityTemplate): void
  (e: 'create-template'): void
  (e: 'toggle-status', id: number, enabled: boolean): void
  (e: 'delete-template', id: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const templateList = computed(() => props.templates || [])

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
  <el-drawer
    v-model="visible"
    title="管理长期接单作息"
    size="520px"
    destroy-on-close
  >
    <div class="drawer-content">
      <el-alert
        title="长期作息自动滚动说明"
        type="info"
        :closable="false"
        show-icon
        description="系统每日凌晨自动根据启用的常规作息按日查漏补缺填充未来 28 天档期。可添加多条规则（如工作日与周末不同作息）。若某天需调整，直接在周看板对应日期微调或请假即可。"
        class="mb-4"
      />

      <div class="drawer-header-actions mb-4">
        <el-button type="primary" @click="emit('create-template')">
          + 添加新规则
        </el-button>
      </div>

      <div v-if="templateList.length > 0" class="template-cards-list">
        <el-card
          v-for="t in templateList"
          :key="t.id"
          class="template-item-card"
          shadow="never"
        >
          <div class="card-top-row">
            <div class="title-with-tag">
              <span class="tpl-title">{{ t.templateName }}</span>
              <el-tag :type="t.enabled ? 'success' : 'danger'" size="small">
                {{ t.enabled ? '已启用' : '已暂停' }}
              </el-tag>
            </div>
            <div class="switch-box">
              <el-switch
                :model-value="t.enabled"
                :loading="loading"
                inline-prompt
                active-text="开"
                inactive-text="关"
                @change="emit('toggle-status', t.id, Boolean($event))"
              />
            </div>
          </div>

          <el-descriptions :column="1" size="small" border class="tpl-desc">
            <el-descriptions-item label="作息时段">
              {{ t.ruleText }}
            </el-descriptions-item>
            <el-descriptions-item label="生效区间">
              {{ t.effectiveFrom }} 至 {{ t.effectiveUntil || '长期有效' }}
            </el-descriptions-item>
            <el-descriptions-item label="已排期至">
              {{ t.lastGeneratedUntil || '已同步' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="card-bottom-actions">
            <el-button type="primary" plain size="small" @click="emit('edit-template', t)">
              编辑规则
            </el-button>
            <el-button type="danger" plain size="small" @click="handleDelete(t)">
              删除规则
            </el-button>
          </div>
        </el-card>
      </div>

      <div v-else class="empty-drawer">
        <el-empty description="当前尚未配置常规作息规则">
          <el-button type="primary" @click="emit('create-template')">
            立即配置常规作息
          </el-button>
        </el-empty>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-content {
  display: flex;
  flex-direction: column;
}

.mb-4 {
  margin-bottom: 16px;
}

.drawer-header-actions {
  display: flex;
  justify-content: flex-end;
}

.template-cards-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.template-item-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
  background-color: #fafbfc;
}

:deep(.template-item-card .el-card__body) {
  padding: 14px 16px;
}

.card-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.title-with-tag {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tpl-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tpl-desc {
  margin-bottom: 12px;
}

.card-bottom-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.empty-drawer {
  padding: 40px 0;
}
</style>
