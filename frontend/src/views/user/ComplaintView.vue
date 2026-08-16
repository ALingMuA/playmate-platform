<script setup lang="ts">
/**
 * 发起投诉（对齐 frontend-prototype complaint.html）。
 *
 * <p>对符合条件的订单选择投诉类型、填写说明并上传图片证据（FR-U17/U18），
 * 提交后订单进入售后处理（FR-U19）。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userOrderDetail, type Order } from '@/api/order'
import { createComplaint } from '@/api/review'
import { uploadFile } from '@/api/file'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.id))
const order = ref<Order | null>(null)
const loading = ref(true)
const submitting = ref(false)

const form = reactive({
  complaintType: 'NO_FULFILLMENT',
  description: '',
  evidences: [] as { fileUrl: string; fileName: string }[],
})

const typeOptions = [
  { value: 'NO_FULFILLMENT', label: '未履约' },
  { value: 'LATE', label: '迟到' },
  { value: 'ATTITUDE', label: '态度问题' },
  { value: 'MISMATCH', label: '服务不符' },
  { value: 'OTHER', label: '其他' },
]

async function handleEvidence(file: File) {
  if (form.evidences.length >= 5) {
    ElMessage.warning('图片证据最多 5 张')
    return false
  }
  try {
    const result = await uploadFile(file, 'evidence')
    form.evidences.push({ fileUrl: result.url, fileName: result.fileName })
    ElMessage.success('证据上传成功')
  } catch {
    // 上传失败已由拦截器提示
  }
  return false
}

function removeEvidence(index: number) {
  form.evidences.splice(index, 1)
}

async function handleSubmit() {
  if (!form.description.trim()) {
    ElMessage.warning('请填写投诉说明')
    return
  }
  submitting.value = true
  try {
    await createComplaint(orderId.value, {
      complaintType: form.complaintType,
      description: form.description.trim(),
      evidences: form.evidences,
    })
    ElMessage.success('投诉已提交，订单进入售后处理')
    router.replace('/orders')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    order.value = await userOrderDetail(orderId.value)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="container" v-loading="loading">
      <!-- 页头 -->
      <div class="page-header">
        <h1 class="page-title">发起投诉</h1>
        <p class="page-subtitle" v-if="order">订单号：{{ order.orderNo }}</p>
      </div>

      <template v-if="order">
        <div class="card">
          <div class="card-body">
            <div class="form-group">
              <label class="form-label">投诉类型</label>
              <div class="radio-group">
                <div v-for="t in typeOptions" :key="t.value" class="radio-pill">
                  <input type="radio" :id="'type-' + t.value" :value="t.value" v-model="form.complaintType" />
                  <label :for="'type-' + t.value">{{ t.label }}</label>
                </div>
              </div>
            </div>
            <div class="form-group">
              <label class="form-label">投诉说明</label>
              <textarea v-model="form.description" class="form-control" rows="4" maxlength="2000"
                placeholder="请描述问题经过（最多2000字）"></textarea>
            </div>
            <div class="form-group">
              <label class="form-label">图片证据（最多5张）</label>
              <div class="flex gap-1 flex-wrap">
                <div v-for="(ev, i) in form.evidences" :key="i" class="evidence-item">
                  <el-image :src="ev.fileUrl" fit="cover" class="evidence-img"
                    :preview-src-list="[ev.fileUrl]" preview-teleported />
                  <el-button size="small" type="danger" text @click="removeEvidence(i)">移除</el-button>
                </div>
                <el-upload v-if="form.evidences.length < 5" :show-file-list="false" accept="image/*"
                  :before-upload="handleEvidence">
                  <div class="upload-placeholder" style="padding: 12px 24px">
                    <div>+ 上传图片</div>
                  </div>
                </el-upload>
              </div>
            </div>
            <div class="flex gap-1">
              <button class="btn btn-destructive" :disabled="submitting" @click="handleSubmit">
                {{ submitting ? '提交中…' : '提交投诉' }}
              </button>
              <button class="btn btn-ghost" @click="router.push('/orders')">返回订单列表</button>
            </div>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>订单不存在</p>
        <button class="btn btn-secondary mt-2" @click="router.push('/orders')">返回订单列表</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.evidence-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.evidence-img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}
</style>
