<script setup lang="ts">
/**
 * 目录管理（管理后台）：游戏管理 FR-M10 / 服务类型管理 FR-M11 / 标签管理 FR-M12。
 *
 * <p>这三类"服务目录"是陪玩师发布服务时只能**选择**、不能定义的基础数据，
 * 因此统一由管理员在此维护：新增、编辑、排序、启用/停用、删除。</p>
 *
 * <p>删除护栏（后端）：存在当前态引用（陪玩服务、陪玩师认证能力）时返回
 * 409 CATALOG_IN_USE，此时应改为"停用"——停用后不再出现在陪玩师的选择列表中，
 * 也不会产生新订单。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Game, ServiceType } from '@/api/catalog'
import {
  TAG_CATEGORIES,
  adminCreateGame,
  adminCreateServiceType,
  adminCreateTag,
  adminDeleteGame,
  adminDeleteServiceType,
  adminDeleteTag,
  adminGames,
  adminServiceTypes,
  adminTags,
  adminUpdateGame,
  adminUpdateServiceType,
  adminUpdateTag,
  type Tag,
} from '@/api/adminCatalog'

const activeTab = ref('game')

// 标签的"所属游戏"下拉：需要含停用游戏的完整列表，用公开接口 + 自行兜底
const gameOptions = ref<Game[]>([])

// ==================== 游戏（FR-M10） ====================

const games = ref<Game[]>([])
const gameLoading = ref(false)
const gameQuery = reactive({ keyword: '', enabled: undefined as number | undefined })

async function loadGames() {
  gameLoading.value = true
  try {
    games.value = await adminGames(gameQuery.keyword || undefined, gameQuery.enabled)
  } finally {
    gameLoading.value = false
  }
}

// ==================== 服务类型（FR-M11） ====================

const types = ref<ServiceType[]>([])
const typeLoading = ref(false)
const typeQuery = reactive({ keyword: '', enabled: undefined as number | undefined })

async function loadTypes() {
  typeLoading.value = true
  try {
    types.value = await adminServiceTypes(typeQuery.keyword || undefined, typeQuery.enabled)
  } finally {
    typeLoading.value = false
  }
}

// ==================== 标签（FR-M12） ====================

const tags = ref<Tag[]>([])
const tagLoading = ref(false)
const tagQuery = reactive({
  keyword: '',
  category: undefined as string | undefined,
  gameId: undefined as number | undefined,
  enabled: undefined as number | undefined,
})

async function loadTags() {
  tagLoading.value = true
  try {
    tags.value = await adminTags({
      keyword: tagQuery.keyword || undefined,
      category: tagQuery.category,
      gameId: tagQuery.gameId,
      enabled: tagQuery.enabled,
    })
  } finally {
    tagLoading.value = false
  }
}

// ==================== 展示辅助 ====================

function gameNameOf(gameId: number): string {
  if (!gameId) return '通用'
  return gameOptions.value.find((g) => g.id === gameId)?.gameName ?? `游戏#${gameId}`
}

function categoryLabel(category: string): string {
  return TAG_CATEGORIES.find((c) => c.value === category)?.label ?? category
}

async function reloadAll() {
  // 标签页的游戏下拉需要完整列表（含停用），用于维护历史标签
  gameOptions.value = await adminGames(undefined, undefined).catch(() => [])
}

onMounted(async () => {
  await Promise.all([reloadAll(), loadGames(), loadTypes(), loadTags()])
})

// ==================== 新增/编辑弹窗 ====================

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const dialogKind = ref<'game' | 'type' | 'tag'>('game')

const gameForm = reactive({ gameName: '', gameIconUrl: '', gameIntro: '', sortNo: 0, enabled: 1 })
const typeForm = reactive({ typeName: '', typeCode: '', description: '', sortNo: 0, enabled: 1 })
const tagForm = reactive({ tagName: '', tagCategory: 'POSITION', gameId: 0, sortNo: 0, enabled: 1 })

function openCreate(kind: 'game' | 'type' | 'tag') {
  dialogKind.value = kind
  editingId.value = null
  if (kind === 'game') {
    Object.assign(gameForm, { gameName: '', gameIconUrl: '', gameIntro: '', sortNo: 0, enabled: 1 })
  } else if (kind === 'type') {
    Object.assign(typeForm, { typeName: '', typeCode: '', description: '', sortNo: 0, enabled: 1 })
  } else {
    Object.assign(tagForm, { tagName: '', tagCategory: 'POSITION', gameId: 0, sortNo: 0, enabled: 1 })
  }
  dialogVisible.value = true
}

function openEditGame(row: Game) {
  dialogKind.value = 'game'
  editingId.value = row.id
  Object.assign(gameForm, {
    gameName: row.gameName,
    gameIconUrl: row.gameIconUrl ?? '',
    gameIntro: row.gameIntro ?? '',
    sortNo: row.sortNo ?? 0,
    enabled: row.enabled ?? 1,
  })
  dialogVisible.value = true
}

function openEditType(row: ServiceType) {
  dialogKind.value = 'type'
  editingId.value = row.id
  Object.assign(typeForm, {
    typeName: row.typeName,
    typeCode: row.typeCode,
    description: row.description ?? '',
    sortNo: row.sortNo ?? 0,
    enabled: row.enabled ?? 1,
  })
  dialogVisible.value = true
}

function openEditTag(row: Tag) {
  dialogKind.value = 'tag'
  editingId.value = row.id
  Object.assign(tagForm, {
    tagName: row.tagName,
    tagCategory: row.tagCategory,
    gameId: row.gameId ?? 0,
    sortNo: row.sortNo ?? 0,
    enabled: row.enabled ?? 1,
  })
  dialogVisible.value = true
}

function validate(): boolean {
  if (dialogKind.value === 'game') {
    if (!gameForm.gameName.trim()) {
      ElMessage.warning('请填写游戏名称')
      return false
    }
  } else if (dialogKind.value === 'type') {
    if (!typeForm.typeName.trim() || !typeForm.typeCode.trim()) {
      ElMessage.warning('请填写服务类型名称与编码')
      return false
    }
    if (!/^[A-Za-z_]+$/.test(typeForm.typeCode.trim())) {
      ElMessage.warning('编码只能包含字母与下划线，如 TEAM_UP')
      return false
    }
  } else if (!tagForm.tagName.trim()) {
    ElMessage.warning('请填写标签名称')
    return false
  }
  return true
}

async function handleSave() {
  if (!validate()) return
  saving.value = true
  try {
    if (dialogKind.value === 'game') {
      const payload = {
        gameName: gameForm.gameName.trim(),
        gameIconUrl: gameForm.gameIconUrl,
        gameIntro: gameForm.gameIntro,
        sortNo: gameForm.sortNo,
        enabled: gameForm.enabled,
      }
      if (editingId.value == null) await adminCreateGame(payload)
      else await adminUpdateGame(editingId.value, payload)
      await loadGames()
    } else if (dialogKind.value === 'type') {
      const payload = {
        typeName: typeForm.typeName.trim(),
        typeCode: typeForm.typeCode.trim().toUpperCase(),
        description: typeForm.description,
        sortNo: typeForm.sortNo,
        enabled: typeForm.enabled,
      }
      if (editingId.value == null) await adminCreateServiceType(payload)
      else await adminUpdateServiceType(editingId.value, payload)
      await loadTypes()
    } else {
      const payload = {
        tagName: tagForm.tagName.trim(),
        tagCategory: tagForm.tagCategory,
        gameId: tagForm.gameId ?? 0,
        sortNo: tagForm.sortNo,
        enabled: tagForm.enabled,
      }
      if (editingId.value == null) await adminCreateTag(payload)
      else await adminUpdateTag(editingId.value, payload)
      await loadTags()
    }
    ElMessage.success(editingId.value == null ? '已新增' : '已更新')
    dialogVisible.value = false
    await reloadAll()
  } finally {
    saving.value = false
  }
}

// ==================== 启用/停用与删除 ====================

/**
 * 启用/停用：后端 PUT 为全量更新，所以要把既有字段一起回填。
 * 停用是可逆的"软操作"，也是被引用目录项的唯一可行处置方式。
 */
async function toggleEnabled(kind: 'game' | 'type' | 'tag', row: Game | ServiceType | Tag) {
  const next = row.enabled === 1 ? 0 : 1
  if (kind === 'game') {
    const g = row as Game
    await adminUpdateGame(g.id, {
      gameName: g.gameName,
      gameIconUrl: g.gameIconUrl,
      gameIntro: g.gameIntro,
      sortNo: g.sortNo,
      enabled: next,
    })
    await loadGames()
  } else if (kind === 'type') {
    const t = row as ServiceType
    await adminUpdateServiceType(t.id, {
      typeName: t.typeName,
      typeCode: t.typeCode,
      description: t.description,
      sortNo: t.sortNo,
      enabled: next,
    })
    await loadTypes()
  } else {
    const t = row as Tag
    await adminUpdateTag(t.id, {
      tagName: t.tagName,
      tagCategory: t.tagCategory,
      gameId: t.gameId,
      sortNo: t.sortNo,
      enabled: next,
    })
    await loadTags()
  }
  ElMessage.success(next === 1 ? '已启用' : '已停用，前台不再展示该目录项')
  await reloadAll()
}

async function handleDelete(kind: 'game' | 'type' | 'tag', row: Game | ServiceType | Tag) {
  const label = kind === 'game' ? (row as Game).gameName : kind === 'type' ? (row as ServiceType).typeName : (row as Tag).tagName
  try {
    await ElMessageBox.confirm(
      `确认删除「${label}」？删除不可恢复。若该项已被陪玩服务或陪玩师能力引用，后台会拒绝删除，请改用停用。`,
      '删除目录项',
      { type: 'warning', confirmButtonText: '确认删除' },
    )
  } catch {
    return
  }
  if (kind === 'game') {
    await adminDeleteGame(row.id)
    await loadGames()
  } else if (kind === 'type') {
    await adminDeleteServiceType(row.id)
    await loadTypes()
  } else {
    await adminDeleteTag(row.id)
    await loadTags()
  }
  ElMessage.success('已删除')
  await reloadAll()
}
</script>

<template>
  <div class="catalog-view">
    <el-tabs v-model="activeTab">
      <!-- 游戏管理（FR-M10） -->
      <el-tab-pane label="游戏管理" name="game">
        <div class="toolbar">
          <el-input
            v-model="gameQuery.keyword"
            placeholder="按游戏名称搜索"
            clearable
            style="width: 200px"
            @keyup.enter="loadGames"
            @clear="loadGames"
          />
          <el-select v-model="gameQuery.enabled" placeholder="全部状态" clearable style="width: 130px" @change="loadGames">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadGames">查询</el-button>
          <el-button type="primary" plain @click="openCreate('game')">新增游戏</el-button>
        </div>

        <el-table :data="games" v-loading="gameLoading" border stripe>
          <el-table-column prop="gameName" label="游戏名称" min-width="140" />
          <el-table-column prop="gameIntro" label="简介" min-width="220" show-overflow-tooltip />
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled === 1"
                active-text="启用"
                inactive-text="停用"
                inline-prompt
                @change="toggleEnabled('game', row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditGame(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete('game', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 服务类型管理（FR-M11） -->
      <el-tab-pane label="服务类型管理" name="type">
        <div class="toolbar">
          <el-input
            v-model="typeQuery.keyword"
            placeholder="按类型名称搜索"
            clearable
            style="width: 200px"
            @keyup.enter="loadTypes"
            @clear="loadTypes"
          />
          <el-select v-model="typeQuery.enabled" placeholder="全部状态" clearable style="width: 130px" @change="loadTypes">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadTypes">查询</el-button>
          <el-button type="primary" plain @click="openCreate('type')">新增服务类型</el-button>
        </div>
        <el-alert
          class="hint"
          type="info"
          :closable="false"
          title="服务类型是陪玩师发布服务时只能选择、不能自定义的目录项；停用后不再出现在陪玩师的选择列表中，已创建服务保留类型名并标注「已停用」。"
        />

        <el-table :data="types" v-loading="typeLoading" border stripe>
          <el-table-column prop="typeName" label="类型名称" min-width="140" />
          <el-table-column prop="typeCode" label="编码" width="150" />
          <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled === 1"
                active-text="启用"
                inactive-text="停用"
                inline-prompt
                @change="toggleEnabled('type', row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditType(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete('type', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 标签管理（FR-M12） -->
      <el-tab-pane label="标签管理" name="tag">
        <div class="toolbar">
          <el-input
            v-model="tagQuery.keyword"
            placeholder="按标签名称搜索"
            clearable
            style="width: 180px"
            @keyup.enter="loadTags"
            @clear="loadTags"
          />
          <el-select v-model="tagQuery.category" placeholder="全部分类" clearable style="width: 140px" @change="loadTags">
            <el-option v-for="c in TAG_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <el-select v-model="tagQuery.gameId" placeholder="全部游戏" clearable style="width: 160px" @change="loadTags">
            <el-option label="通用标签" :value="0" />
            <el-option v-for="g in gameOptions" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
          <el-select v-model="tagQuery.enabled" placeholder="全部状态" clearable style="width: 130px" @change="loadTags">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadTags">查询</el-button>
          <el-button type="primary" plain @click="openCreate('tag')">新增标签</el-button>
        </div>

        <el-table :data="tags" v-loading="tagLoading" border stripe>
          <el-table-column prop="tagName" label="标签名称" min-width="140" />
          <el-table-column label="分类" width="120">
            <template #default="{ row }">{{ categoryLabel(row.tagCategory) }}</template>
          </el-table-column>
          <el-table-column label="所属游戏" width="140">
            <template #default="{ row }">{{ gameNameOf(row.gameId) }}</template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled === 1"
                active-text="启用"
                inactive-text="停用"
                inline-prompt
                @change="toggleEnabled('tag', row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditTag(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete('tag', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="(editingId == null ? '新增' : '编辑') + (dialogKind === 'game' ? '游戏' : dialogKind === 'type' ? '服务类型' : '标签')"
      width="560px"
    >
      <el-form v-if="dialogKind === 'game'" :model="gameForm" label-width="100px">
        <el-form-item label="游戏名称" required>
          <el-input v-model="gameForm.gameName" maxlength="50" show-word-limit placeholder="如：云顶之弈" />
        </el-form-item>
        <el-form-item label="图标地址">
          <el-input v-model="gameForm.gameIconUrl" maxlength="500" placeholder="图片 URL，选填" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="gameForm.gameIntro" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="gameForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="gameForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <el-form v-else-if="dialogKind === 'type'" :model="typeForm" label-width="100px">
        <el-form-item label="类型名称" required>
          <el-input v-model="typeForm.typeName" maxlength="50" show-word-limit placeholder="如：排位上分" />
        </el-form-item>
        <el-form-item label="类型编码" required>
          <el-input v-model="typeForm.typeCode" maxlength="32" placeholder="如 RANK_BOOST（字母与下划线，自动转大写）" />
        </el-form-item>
        <el-form-item label="类型说明">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="typeForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="typeForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <el-form v-else :model="tagForm" label-width="100px">
        <el-form-item label="标签名称" required>
          <el-input v-model="tagForm.tagName" maxlength="50" show-word-limit placeholder="如：打野" />
        </el-form-item>
        <el-form-item label="标签分类" required>
          <el-select v-model="tagForm.tagCategory" style="width: 100%">
            <el-option v-for="c in TAG_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属游戏">
          <el-select v-model="tagForm.gameId" style="width: 100%">
            <el-option label="通用标签（所有游戏可见）" :value="0" />
            <el-option v-for="g in gameOptions" :key="g.id" :label="g.gameName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="tagForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="tagForm.enabled" :active-value="1" :inactive-value="0" />
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
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.hint {
  margin-bottom: 12px;
}
</style>
