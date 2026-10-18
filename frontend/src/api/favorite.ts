import { request, type PageResult } from '@/api/http'

/** 收藏列表只包含服务端真实返回的公开资料字段。 */
export interface FavoriteView {
  companionUserId: number
  displayName: string
  profileIntro: string
  ratingAvg: number | null
  ratingCount: number | null
  serviceStatus: 'AVAILABLE' | 'BUSY' | 'RESTING' | 'SUSPENDED' | null
  createdAt: string
}

export function listFavorites(page = 1, size = 10): Promise<PageResult<FavoriteView>> {
  return request<PageResult<FavoriteView>>({
    url: '/favorites',
    method: 'get',
    params: { page, size },
  })
}

export function addFavorite(companionUserId: number): Promise<void> {
  return request<void>({ url: `/favorites/${companionUserId}`, method: 'post' })
}

export function removeFavorite(companionUserId: number): Promise<void> {
  return request<void>({ url: `/favorites/${companionUserId}`, method: 'delete' })
}

/** 精确读取当前登录用户是否已收藏指定陪玩师。 */
export function getFavoriteStatus(companionUserId: number): Promise<boolean> {
  return request<boolean>({
    url: `/favorites/${companionUserId}/status`,
    method: 'get',
  })
}
