import { request } from '@/api/http'
import type { Game, ServiceType } from '@/api/catalog'

/**
 * 目录管理 API 封装（管理端，FR-M10 游戏管理 / FR-M11 服务类型管理 / FR-M12 标签管理）。
 *
 * <p>对应后端 {@code /api/admin/games}、{@code /api/admin/service-types}、{@code /api/admin/tags}，
 * 均要求 ADMIN 角色（类级 {@code @PreAuthorize}）。管理端列表返回**含停用项**的实体，
 * 与前台公开接口（只返回启用项）不同。</p>
 */

/** 标签实体（管理端返回的是实体，前台返回的是 TagView） */
export interface Tag {
  id: number
  tagName: string
  /** 分类：POSITION 位置、STYLE 风格、HERO 擅长英雄、OTHER 其他 */
  tagCategory: string
  /** 所属游戏ID，0 表示通用标签 */
  gameId: number
  sortNo: number
  /** 启用状态：0否，1是 */
  enabled: number
  createdAt?: string
  updatedAt?: string
}

/** 标签分类选项（与后端 TagCategory 枚举一致） */
export const TAG_CATEGORIES = [
  { value: 'POSITION', label: '游戏位置' },
  { value: 'STYLE', label: '陪玩风格' },
  { value: 'HERO', label: '擅长英雄' },
  { value: 'OTHER', label: '其他' },
] as const

export interface GamePayload {
  gameName: string
  gameIconUrl?: string
  gameIntro?: string
  sortNo?: number
  enabled?: number
}

export interface ServiceTypePayload {
  typeName: string
  typeCode: string
  description?: string
  sortNo?: number
  enabled?: number
}

export interface TagPayload {
  tagName: string
  tagCategory: string
  gameId?: number
  sortNo?: number
  enabled?: number
}

// ==================== 游戏（FR-M10） ====================

export function adminGames(keyword?: string, enabled?: number): Promise<Game[]> {
  return request<Game[]>({ url: '/admin/games', method: 'get', params: { keyword, enabled } })
}

export function adminCreateGame(data: GamePayload): Promise<Game> {
  return request<Game>({ url: '/admin/games', method: 'post', data })
}

export function adminUpdateGame(id: number, data: GamePayload): Promise<Game> {
  return request<Game>({ url: `/admin/games/${id}`, method: 'put', data })
}

export function adminDeleteGame(id: number): Promise<void> {
  return request<void>({ url: `/admin/games/${id}`, method: 'delete' })
}

// ==================== 服务类型（FR-M11） ====================

export function adminServiceTypes(keyword?: string, enabled?: number): Promise<ServiceType[]> {
  return request<ServiceType[]>({ url: '/admin/service-types', method: 'get', params: { keyword, enabled } })
}

export function adminCreateServiceType(data: ServiceTypePayload): Promise<ServiceType> {
  return request<ServiceType>({ url: '/admin/service-types', method: 'post', data })
}

export function adminUpdateServiceType(id: number, data: ServiceTypePayload): Promise<ServiceType> {
  return request<ServiceType>({ url: `/admin/service-types/${id}`, method: 'put', data })
}

export function adminDeleteServiceType(id: number): Promise<void> {
  return request<void>({ url: `/admin/service-types/${id}`, method: 'delete' })
}

// ==================== 标签（FR-M12） ====================

export function adminTags(params?: {
  keyword?: string
  category?: string
  gameId?: number
  enabled?: number
}): Promise<Tag[]> {
  return request<Tag[]>({ url: '/admin/tags', method: 'get', params })
}

export function adminCreateTag(data: TagPayload): Promise<Tag> {
  return request<Tag>({ url: '/admin/tags', method: 'post', data })
}

export function adminUpdateTag(id: number, data: TagPayload): Promise<Tag> {
  return request<Tag>({ url: `/admin/tags/${id}`, method: 'put', data })
}

export function adminDeleteTag(id: number): Promise<void> {
  return request<void>({ url: `/admin/tags/${id}`, method: 'delete' })
}
