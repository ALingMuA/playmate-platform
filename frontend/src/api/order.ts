import { request, type PageResult } from '@/api/http'

/** 订单视图（FR-U10/U11、FR-P18） */
export interface Order {
  id: number
  orderNo: string
  userId: number
  userNickname: string
  companionUserId: number
  companionNameSnapshot: string
  companionServiceId: number
  serviceTitleSnapshot: string
  gameId: number
  serviceTypeNameSnapshot: string
  unitPriceCents: number
  durationMinutes: number
  totalAmountCents: number
  gameServer: string
  gameNickname: string
  userRemark: string
  appointmentStartAt: string
  appointmentEndAt: string
  orderStatus:
    | 'PENDING_PAYMENT'
    | 'WAITING_ACCEPTANCE'
    | 'WAITING_SERVICE'
    | 'IN_SERVICE'
    | 'WAITING_CONFIRMATION'
    | 'COMPLETED'
    | 'CLOSED'
  payExpireAt?: string
  acceptExpireAt?: string
  startedAt?: string
  endedAt?: string
  confirmedAt?: string
  closedReason: string
  createdAt: string
  statusHistories?: OrderStatusHistory[]
}

/** 订单状态历史（FR-U11） */
export interface OrderStatusHistory {
  fromStatus: string
  toStatus: string
  operatorRole: string
  actionCode: string
  reason: string
  createdAt: string
}

/** 创建订单（FR-U07） */
export function createOrder(data: {
  companionUserId: number
  companionServiceId: number
  durationMinutes: number
  gameServer?: string
  gameNickname?: string
  userRemark?: string
  appointmentStartAt: string
}): Promise<Order> {
  return request<Order>({ url: '/play-orders', method: 'post', data })
}

/** 公开可预约服务列表（FR-U02/U03） */
export interface BookableService {
  id: number
  companionUserId: number
  gameId: number
  gameName: string
  serviceTypeId: number
  serviceTypeName: string
  title: string
  description: string
  tagIds: number[]
  tagNames: string[]
  priceCents: number
  minDurationMinutes: number
  auditStatus: string
  serviceStatus: string
  createdAt: string
  updatedAt: string
}

export function listBookableServices(gameId?: number, companionUserId?: number, page = 1, size = 10): Promise<PageResult<BookableService>> {
  return request<PageResult<BookableService>>({
    url: '/companion-services',
    method: 'get',
    params: { gameId, companionUserId, page, size },
  })
}

/** 模拟支付（FR-U09） */
export function payOrder(id: number): Promise<Order> {
  return request<Order>({ url: `/play-orders/${id}/pay`, method: 'post' })
}

/** 我的订单（FR-U10） */
export function myOrders(status?: string, page = 1, size = 10): Promise<PageResult<Order>> {
  return request<PageResult<Order>>({
    url: '/play-orders/mine',
    method: 'get',
    params: { status, page, size },
  })
}

/** 取消订单（FR-U12）：未支付直接关闭，已支付（待接单/待服务）全额退款 */
export function cancelOrder(id: number, reason?: string): Promise<Order> {
  return request<Order>({
    url: `/play-orders/${id}/cancel`,
    method: 'post',
    data: { reason: reason ?? '' },
  })
}

/** 确认完成（FR-U13） */
export function confirmOrder(id: number): Promise<Order> {
  return request<Order>({ url: `/play-orders/${id}/confirm`, method: 'post' })
}

// ==================== 陪玩师端订单 ====================

/** 陪玩师订单列表（FR-P18） */
export function companionOrders(status?: string, page = 1, size = 10): Promise<PageResult<Order>> {
  return request<PageResult<Order>>({
    url: '/companion/orders',
    method: 'get',
    params: { status, page, size },
  })
}

/** 订单详情（含状态轨迹） */
export function orderDetail(id: number): Promise<Order> {
  return request<Order>({ url: `/companion/orders/${id}`, method: 'get' })
}

/** 接单（FR-P13） */
export function acceptOrder(id: number): Promise<Order> {
  return request<Order>({
    url: `/companion/orders/${id}/accept`,
    method: 'post',
    data: { confirm: true },
  })
}

/** 拒绝订单（FR-P14） */
export function rejectOrder(id: number, reason: string): Promise<Order> {
  return request<Order>({
    url: `/companion/orders/${id}/reject`,
    method: 'post',
    data: { reason },
  })
}

/** 开始服务（FR-P15） */
export function startOrderService(id: number): Promise<Order> {
  return request<Order>({ url: `/companion/orders/${id}/start`, method: 'post' })
}

/** 结束服务（FR-P16） */
export function endOrderService(id: number): Promise<Order> {
  return request<Order>({ url: `/companion/orders/${id}/end`, method: 'post' })
}

/** 钱包概览 */
export function walletMe(): Promise<{ balanceCents: number; frozenCents: number; totalIncomeCents: number }> {
  return request<{ balanceCents: number; frozenCents: number; totalIncomeCents: number }>({
    url: '/wallet/me',
    method: 'get',
  })
}