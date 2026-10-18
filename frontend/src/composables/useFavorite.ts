import { ref, toValue, watch, type MaybeRefOrGetter } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { addFavorite, getFavoriteStatus, removeFavorite } from '@/api/favorite'
import { useUserStore } from '@/stores/user'

/** 公开主页与列表共用的收藏状态、登录引导和失败回滚。 */
export function useFavorite(companionUserId: MaybeRefOrGetter<number>) {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const isFavorited = ref(false)
  const loading = ref(false)
  const saving = ref(false)
  let stateVersion = 0

  watch(() => toValue(companionUserId), () => {
    stateVersion += 1
    isFavorited.value = false
    loading.value = false
    saving.value = false
  })

  async function refresh() {
    const requestVersion = ++stateVersion
    if (!userStore.isLoggedIn) {
      isFavorited.value = false
      return
    }
    const id = toValue(companionUserId)
    loading.value = true
    try {
      const status = await getFavoriteStatus(id)
      if (requestVersion === stateVersion) isFavorited.value = status
    } finally {
      if (requestVersion === stateVersion) loading.value = false
    }
  }

  async function toggle() {
    if (!userStore.isLoggedIn) {
      await router.push({ path: '/login', query: { redirect: route.fullPath } })
      return
    }
    if (saving.value || loading.value) return

    const requestVersion = stateVersion
    const id = toValue(companionUserId)
    const previous = isFavorited.value
    const next = !previous
    isFavorited.value = next
    saving.value = true
    try {
      if (next) await addFavorite(id)
      else await removeFavorite(id)
    } catch (error) {
      if (requestVersion === stateVersion) isFavorited.value = previous
      throw error
    } finally {
      if (requestVersion === stateVersion) saving.value = false
    }
  }

  return { isFavorited, loading, saving, refresh, toggle }
}
