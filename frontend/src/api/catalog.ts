import { request } from './http'

/**
 * 目录（catalog）模块 API 封装。
 *
 * <p>对应后端 {@code /api/games}、{@code /api/service-types}、{@code /api/tags} 公开接口
 * （FR-U01 游戏分类浏览、FR-M11 服务类型展示、FR-M12 标签选择）。</p>
 */

/** 游戏基础信息（对应后端 catalog.domain.Game） */
export interface Game {
  /** 主键ID */
  id: number
  /** 游戏名称 */
  gameName: string
  /** 游戏图标地址 */
  gameIconUrl: string
  /** 游戏简介 */
  gameIntro: string
  /** 排序号，越小越靠前 */
  sortNo: number
  /** 启用状态：0否，1是 */
  enabled: number
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 服务类型（对应后端 catalog.domain.ServiceType） */
export interface ServiceType {
  /** 主键ID */
  id: number
  /** 服务类型名称 */
  typeName: string
  /** 服务类型编码（如 TEAM_UP） */
  typeCode: string
  /** 类型说明 */
  description: string
  /** 排序号 */
  sortNo: number
  /** 启用状态：0否，1是 */
  enabled: number
}

/** 标签视图（对应后端 catalog.dto.TagView） */
export interface TagView {
  /** 主键ID */
  id: number
  /** 标签名称 */
  tagName: string
  /** 标签分类：POSITION、STYLE、HERO、OTHER */
  tagCategory: string
  /** 所属游戏ID，0 表示通用标签 */
  gameId: number
  /** 是否通用标签 */
  common: boolean
  /** 排序号 */
  sortNo: number
}

/** 已启用游戏列表（FR-U01） */
export function listGames(): Promise<Game[]> {
  return request<Game[]>({ url: '/games', method: 'get' })
}

/** 游戏详情（仅启用状态，FR-U01） */
export function getGame(id: number): Promise<Game> {
  return request<Game>({ url: `/games/${id}`, method: 'get' })
}

/** 已启用服务类型列表（FR-M11 前台展示） */
export function listServiceTypes(): Promise<ServiceType[]> {
  return request<ServiceType[]>({ url: '/service-types', method: 'get' })
}

/**
 * 标签列表（FR-U05、FR-P02 选标签用）。
 * <p>gameId 为空返回全部启用标签；指定 gameId 返回该游戏标签 + 通用标签（gameId=0）。</p>
 */
export function listTags(gameId?: number): Promise<TagView[]> {
  return request<TagView[]>({
    url: '/tags',
    method: 'get',
    params: gameId != null ? { gameId } : undefined,
  })
}
