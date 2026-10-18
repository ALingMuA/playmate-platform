<script setup lang="ts">
/**
 * 管理后台 - 平台活动管理
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  adminActivities,
  adminCreateActivity,
  adminDeleteActivity,
  adminOfflineActivity,
  adminPublishActivity,
  adminUpdateActivity,
  type ActivityForm,
  type ActivityView,
} from '@/api/activity'
import { adminCoupons, type CouponTemplateView } from '@/api/coupon'
import { uploadFile } from '@/api/file'

const list = ref<ActivityView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const status = ref<string | undefined>(undefined)
const uploadingBanner = ref(false)

const couponTemplates = ref<CouponTemplateView[]>([])

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下线', type: 'warning' },
}

const actionTypeMap: Record<string, string> = {
  COUPON_CLAIM: '优惠券领取',
  COMPANION_LIST: '找陪玩跳转',
  PAGE_NAV: '自定义链接/路由',
}

const gradientPresets = [
  { label: '蓝紫渐变（推荐）', value: 'linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)' },
  { label: '紫粉渐变', value: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
  { label: '粉红渐变', value: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' },
  { label: '青蓝渐变', value: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' },
  { label: '清新绿渐变', value: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)' },
  { label: '橙红渐变', value: 'linear-gradient(135deg, #ff9966 0%, #ff5e62 100%)' },
]

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.length >= 16 ? t.slice(0, 16) : t
}

async function load() {
  loading.value = true
  try {
    const result = await adminActivities(status.value, page.value, pageSize)
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadCoupons() {
  try {
    const res = await adminCoupons(undefined, 1, 100)
    couponTemplates.value = res.records
  } catch {
    couponTemplates.value = []
  }
}

function onStatusChange() {
  page.value = 1
  load()
}

// ==================== 新增/编辑弹窗 ====================

const editVisible = ref(false)
const saving = ref(false)
const timeRange = ref<[Date, Date] | null>(null)

const form = reactive<{
  id: number
  title: string
  subtitle: string
  description: string
  bannerImage: string
  bgGradient: string
  icon: string
  tag: string
  tagColor: string
  actionType: string
  actionRoute: string
  couponTemplateId?: number
  sortOrder: number
}>({
  id: 0,
  title: '',
  subtitle: '',
  description: '',
  bannerImage: '',
  bgGradient: gradientPresets[0].value,
  icon: '🎁',
  tag: '热门活动',
  tagColor: '#ef4444',
  actionType: 'COUPON_CLAIM',
  actionRoute: '',
  couponTemplateId: undefined,
  sortOrder: 10,
})

function openCreate() {
  form.id = 0
  form.title = ''
  form.subtitle = ''
  form.description = ''
  form.bannerImage = ''
  form.bgGradient = gradientPresets[0].value
  form.icon = '🎁'
  form.tag = '限时福利'
  form.tagColor = '#ef4444'
  form.actionType = 'COUPON_CLAIM'
  form.actionRoute = ''
  form.couponTemplateId = undefined
  form.sortOrder = 10
  timeRange.value = null
  editVisible.value = true
}

function openEdit(row: ActivityView) {
  form.id = row.id
  form.title = row.title
  form.subtitle = row.subtitle
  form.description = row.description
  form.bannerImage = row.bannerImage || ''
  form.bgGradient = row.bgGradient || gradientPresets[0].value
  form.icon = row.icon || '🎁'
  form.tag = row.tag || ''
  form.tagColor = row.tagColor || '#ef4444'
  form.actionType = row.actionType
  form.actionRoute = row.actionRoute || ''
  form.couponTemplateId = row.couponTemplateId
  form.sortOrder = row.sortOrder ?? 10
  if (row.startTime && row.endTime) {
    timeRange.value = [new Date(row.startTime), new Date(row.endTime)]
  } else {
    timeRange.value = null
  }
  editVisible.value = true
}

async function handleBannerUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件（JPG/PNG/WEBP/GIF）')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return false
  }
  uploadingBanner.value = true
  try {
    const res = await uploadFile(file, 'activity')
    form.bannerImage = res.url
    ElMessage.success('横幅背景图上传成功')
  } catch {
    // 拦截器统一处理
  } finally {
    uploadingBanner.value = false
  }
  return false
}

async function handleSave() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入活动标题')
    return
  }
  if (form.actionType === 'COUPON_CLAIM' && !form.couponTemplateId) {
    ElMessage.warning('领券类活动必须关联优惠券模版')
    return
  }

  saving.value = true
  try {
    const payload: ActivityForm = {
      title: form.title.trim(),
      subtitle: form.subtitle.trim(),
      description: form.description.trim(),
      bannerImage: form.bannerImage,
      bgGradient: form.bgGradient,
      icon: form.icon,
      tag: form.tag,
      tagColor: form.tagColor,
      actionType: form.actionType,
      actionRoute: form.actionRoute,
      couponTemplateId: form.actionType === 'COUPON_CLAIM' ? form.couponTemplateId : undefined,
      sortOrder: form.sortOrder,
      startTime: timeRange.value ? dayjs(timeRange.value[0]).format('YYYY-MM-DD HH:mm:ss') : undefined,
      endTime: timeRange.value ? dayjs(timeRange.value[1]).format('YYYY-MM-DD HH:mm:ss') : undefined,
    }

    if (form.id) {
      await adminUpdateActivity(form.id, payload)
      ElMessage.success('活动已更新')
    } else {
      await adminCreateActivity(payload)
      ElMessage.success('活动已创建（草稿）')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 状态操作 ====================

async function handlePublish(row: ActivityView) {
  await ElMessageBox.confirm(`确定发布活动「${row.title}」吗？发布后用户端首页轮播和活动中心将可见。`, '发布活动', { type: 'info' })
  await adminPublishActivity(row.id)
  ElMessage.success('活动已发布')
  await load()
}

async function handleOffline(row: ActivityView) {
  await ElMessageBox.confirm(`确定下线活动「${row.title}」吗？下线后用户端将不再展示。`, '下线活动', { type: 'warning' })
  await adminOfflineActivity(row.id)
  ElMessage.success('活动已下线')
  await load()
}

async function handleDelete(row: ActivityView) {
  await ElMessageBox.confirm(`确定删除活动「${row.title}」吗？删除后不可恢复。`, '删除活动', { type: 'warning' })
  await adminDeleteActivity(row.id)
  ElMessage.success('活动已删除')
  await load()
}

onMounted(() => {
  load()
  loadCoupons()
})
</script>

<template>
  <div>
    <!-- 头部操作区 -->
    <div class="flex justify-between items-center mb-4">
      <div class="flex items-center gap-2">
        <el-select
          v-model="status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="onStatusChange"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
      <div>
        <el-button type="primary" @click="openCreate">新建活动</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="活动名称与视觉" min-width="220">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <el-image
                v-if="row.bannerImage"
                :src="row.bannerImage"
                class="banner-table-thumb"
                fit="cover"
                :preview-src-list="[row.bannerImage]"
                preview-teleported
              />
              <span v-else class="text-xl">{{ row.icon || '🎁' }}</span>
              <div>
                <div class="font-bold flex items-center gap-1">
                  <span>{{ row.title }}</span>
                  <el-tag v-if="row.tag" size="small" :color="row.tagColor" effect="dark" style="color: #fff">
                    {{ row.tag }}
                  </el-tag>
                </div>
                <div class="text-xs text-muted">{{ row.subtitle }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="动作模式" width="160">
          <template #default="{ row }">
            <div>
              <el-tag size="small">{{ actionTypeMap[row.actionType] || row.actionType }}</el-tag>
              <div v-if="row.couponTemplateName" class="text-xs text-muted mt-1">
                券: {{ row.couponTemplateName }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" width="220">
          <template #default="{ row }">
            <div v-if="row.startTime || row.endTime" class="text-xs">
              <div>起：{{ fmtTime(row.startTime) }}</div>
              <div>止：{{ fmtTime(row.endTime) }}</div>
            </div>
            <span v-else class="text-muted text-xs">长期有效</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as any) ?? 'info'">
              {{ statusMap[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              link
              type="warning"
              @click="handleOffline(row)"
            >
              下线
            </el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      :title="form.id ? '编辑活动' : '新建活动'"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="110px">
        <el-form-item label="活动标题" required>
          <el-input v-model="form.title" placeholder="如：新人专享福利" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" placeholder="如：首单立减 50%" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="横幅背景图">
          <div class="banner-uploader-container">
            <div v-if="form.bannerImage" class="banner-preview-box">
              <img :src="form.bannerImage" class="banner-img" alt="横幅背景图" />
              <div class="banner-actions-mask">
                <el-upload
                  :show-file-list="false"
                  accept="image/*"
                  :before-upload="handleBannerUpload"
                  :disabled="uploadingBanner"
                >
                  <el-button size="small" type="primary">更换图片</el-button>
                </el-upload>
                <el-button size="small" type="danger" @click="form.bannerImage = ''">移除图片</el-button>
              </div>
            </div>
            <div v-else class="banner-upload-empty">
              <el-upload
                :show-file-list="false"
                accept="image/*"
                :before-upload="handleBannerUpload"
                :disabled="uploadingBanner"
              >
                <div class="banner-dropzone" :class="{ 'is-loading': uploadingBanner }">
                  <span v-if="uploadingBanner">⏳ 图片上传中...</span>
                  <span v-else>📷 点击上传自定义背景图</span>
                </div>
              </el-upload>
              <div class="text-xs text-muted mt-1">
                支持 JPG/PNG/WEBP/GIF，建议尺寸 800×400 或 16:9，不超过 5MB
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="卡片渐变背景">
          <el-select v-model="form.bgGradient" placeholder="选择预设渐变" class="w-full">
            <el-option
              v-for="p in gradientPresets"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            >
              <div class="flex items-center justify-between">
                <span>{{ p.label }}</span>
                <span class="inline-block w-8 h-4 rounded" :style="{ background: p.value }"></span>
              </div>
            </el-option>
          </el-select>
          <div class="text-xs text-muted mt-1">未上传背景图时，将默认展示此预设渐变色</div>
        </el-form-item>
        <el-form-item label="标签与颜色">
          <div class="flex gap-2 w-full">
            <el-input v-model="form.tag" placeholder="如：限时活动" style="flex: 1" />
            <el-color-picker v-model="form.tagColor" />
          </div>
        </el-form-item>
        <el-form-item label="图标 Emoji">
          <el-input v-model="form.icon" placeholder="输入 Emoji 图标，如 🎁 / 🏆 / 🎮" style="width: 200px" />
        </el-form-item>
        <el-form-item label="动作类型" required>
          <el-radio-group v-model="form.actionType">
            <el-radio value="COUPON_CLAIM">一键领取优惠券</el-radio>
            <el-radio value="COMPANION_LIST">挑选陪玩师</el-radio>
            <el-radio value="PAGE_NAV">自定义链接</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.actionType === 'COUPON_CLAIM'" label="关联优惠券" required>
          <el-select v-model="form.couponTemplateId" placeholder="选择要赠送的优惠券模版" class="w-full">
            <el-option
              v-for="c in couponTemplates"
              :key="c.id"
              :label="`#${c.id} ${c.name} (${c.couponType === 'FIXED' ? `减${c.discountCents/100}元` : `${c.discountRateBp/1000}折`})`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.actionType === 'PAGE_NAV'" label="跳转路由">
          <el-input v-model="form.actionRoute" placeholder="如：/games 或 https://..." />
        </el-form-item>
        <el-form-item label="活动有效时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="1" :max="999" />
          <span class="text-xs text-muted ml-2">数值越小排在越前</span>
        </el-form-item>
        <el-form-item label="活动详细介绍">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="在活动详情页展示的完整规则与介绍"
            maxlength="1000"
            show-word-limit
          />
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
.flex {
  display: flex;
}
.items-center {
  align-items: center;
}
.justify-between {
  justify-content: space-between;
}
.justify-end {
  justify-content: flex-end;
}
.gap-1 {
  gap: 4px;
}
.gap-2 {
  gap: 8px;
}
.w-full {
  width: 100%;
}
.mb-4 {
  margin-bottom: 16px;
}
.mt-1 {
  margin-top: 4px;
}
.mt-4 {
  margin-top: 16px;
}
.ml-2 {
  margin-left: 8px;
}
.text-xl {
  font-size: 1.25rem;
}
.text-xs {
  font-size: 0.75rem;
}
.font-bold {
  font-weight: 600;
}
.text-muted {
  color: #9ca3af;
}
.w-8 {
  width: 32px;
}
.h-4 {
  height: 16px;
}
.rounded {
  border-radius: 4px;
}
.inline-block {
  display: inline-block;
}
.banner-table-thumb {
  width: 48px;
  height: 36px;
  border-radius: 4px;
  flex-shrink: 0;
  border: 1px solid #e5e7eb;
}
.banner-uploader-container {
  width: 100%;
}
.banner-preview-box {
  position: relative;
  width: 100%;
  height: 140px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}
.banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.banner-actions-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.banner-preview-box:hover .banner-actions-mask {
  opacity: 1;
}
.banner-dropzone {
  border: 1.5px dashed #d1d5db;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  color: var(--primary, #4f46e5);
  cursor: pointer;
  background: #f9fafb;
  transition: all 0.2s ease;
  font-size: 0.9375rem;
  font-weight: 500;
}
.banner-dropzone:hover {
  border-color: var(--primary, #4f46e5);
  background: #f3f4f6;
}
.banner-dropzone.is-loading {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
