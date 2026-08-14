import { request, type PageResult } from '@/api/http'

/** 评价视图（FR-U15/U16、FR-M08） */
export interface Review {
  id: number
  orderId: number
  userId: number
  userNickname: string
  companionUserId: number
  score: number
  tags: string[]
  content: string
  displayStatus: 'VISIBLE' | 'HIDDEN'
  createdAt: string
}

/** 投诉证据（FR-U18） */
export interface ComplaintEvidence {
  id: number
  fileUrl: string
  fileName: string
  createdAt: string
}

/** 投诉视图（FR-U19、FR-M18） */
export interface Complaint {
  id: number
  orderId: number
  orderNo: string
  serviceTitleSnapshot: string
  orderTotalAmountCents: number
  complainantUserId: number
  complainantNickname: string
  companionUserId: number
  companionNickname: string
  complaintType: 'NO_FULFILLMENT' | 'LATE' | 'ATTITUDE' | 'MISMATCH' | 'OTHER'
  description: string
  complaintStatus: 'PENDING' | 'PROCESSING' | 'RESOLVED'
  resolutionType: '' | 'KEEP' | 'FULL_REFUND' | 'PARTIAL_REFUND'
  refundAmountCents: number
  handledBy: number
  handlingOpinion: string
  handledAt?: string
  createdAt: string
  evidences: ComplaintEvidence[]
}

/** 提交评价（FR-U15） */
export function submitReview(orderId: number, data: {
  score: number
  tags?: string[]
  content: string
}): Promise<Review> {
  return request<Review>({ url: '/reviews', method: 'post', params: { orderId }, data })
}

/** 我的评价分页 */
export function myReviews(page = 1, size = 10): Promise<PageResult<Review>> {
  return request<PageResult<Review>>({ url: '/reviews/mine', method: 'get', params: { page, size } })
}

/** 陪玩师公开评价分页（FR-U16，游客可访问） */
export function companionReviews(companionUserId: number, page = 1, size = 10): Promise<PageResult<Review>> {
  return request<PageResult<Review>>({
    url: `/reviews/companion/${companionUserId}`,
    method: 'get',
    params: { page, size },
  })
}

/** 某订单的评价（判断是否已评价） */
export function reviewByOrder(orderId: number): Promise<Review | null> {
  return request<Review | null>({ url: `/reviews/order/${orderId}`, method: 'get' })
}

/** 发起投诉（FR-U17/U18） */
export function createComplaint(orderId: number, data: {
  complaintType: string
  description: string
  evidences?: { fileUrl: string; fileName: string }[]
}): Promise<Complaint> {
  return request<Complaint>({ url: '/complaints', method: 'post', params: { orderId }, data })
}

/** 我的投诉分页（FR-U19 售后进度） */
export function myComplaints(page = 1, size = 10): Promise<PageResult<Complaint>> {
  return request<PageResult<Complaint>>({ url: '/complaints/mine', method: 'get', params: { page, size } })
}

/** 投诉详情 */
export function complaintDetail(id: number): Promise<Complaint> {
  return request<Complaint>({ url: `/complaints/${id}`, method: 'get' })
}

// ==================== 管理端（FR-M08/M18/M19） ====================

/** 管理端评价分页查询 */
export function adminReviews(keyword?: string, score?: number, displayStatus?: string, page = 1, size = 10): Promise<PageResult<Review>> {
  return request<PageResult<Review>>({
    url: '/admin/reviews',
    method: 'get',
    params: { keyword, score, displayStatus, page, size },
  })
}

/** 屏蔽/恢复评价（FR-M08） */
export function adminSetReviewDisplay(id: number, displayStatus: 'VISIBLE' | 'HIDDEN'): Promise<Review> {
  return request<Review>({
    url: `/admin/reviews/${id}/display-status`,
    method: 'put',
    data: { displayStatus },
  })
}

/** 管理端投诉分页查询 */
export function adminComplaints(status?: string, complaintType?: string, keyword?: string, page = 1, size = 10): Promise<PageResult<Complaint>> {
  return request<PageResult<Complaint>>({
    url: '/admin/complaints',
    method: 'get',
    params: { status, complaintType, keyword, page, size },
  })
}

/** 投诉仲裁（FR-M18/M19） */
export function adminHandleComplaint(id: number, data: {
  resolutionType: 'KEEP' | 'FULL_REFUND' | 'PARTIAL_REFUND'
  refundAmountCents?: number
  handlingOpinion: string
}): Promise<Complaint> {
  return request<Complaint>({
    url: `/admin/complaints/${id}/handle`,
    method: 'post',
    data,
  })
}
