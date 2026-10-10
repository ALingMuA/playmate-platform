import { request, type PageResult } from '@/api/http'

export interface CouponTemplateView {
  id: number
  name: string
  couponType: 'FIXED' | 'DISCOUNT'
  thresholdCents: number
  discountCents: number
  discountRateBp: number
  maxDiscountCents: number
  scopeType: 'ALL' | 'GAME' | 'SERVICE' | 'COMPANION'
  scopeId: number
  activityId?: number
  totalQuantity: number
  claimedQuantity: number
  remainingQuantity?: number
  perUserLimit: number
  validFrom: string
  validUntil: string
  publishStatus: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  createdBy: number
  publishedBy?: number
  publishedAt?: string
  createdAt: string
}

export interface CouponTemplateForm {
  name: string
  couponType: string
  thresholdCents: number
  discountCents: number
  discountRateBp: number
  maxDiscountCents: number
  scopeType: string
  scopeId: number
  activityId?: number
  totalQuantity: number
  perUserLimit: number
  validFrom: string
  validUntil: string
}

export interface UserCouponView {
  id: number
  templateId: number
  templateName: string
  couponCode: string
  couponType: 'FIXED' | 'DISCOUNT'
  thresholdCents: number
  discountCents: number
  discountRateBp: number
  maxDiscountCents: number
  scopeType: string
  scopeId: number
  status: 'AVAILABLE' | 'LOCKED' | 'USED' | 'EXPIRED'
  validFrom: string
  validUntil: string
  lockedOrderId?: number
  usedOrderId?: number
  claimedAt: string
  usedAt?: string
  isApplicable?: boolean
  expectedDiscountCents?: number
  notApplicableReason?: string
}

export interface CouponPreviewResult {
  originalAmountCents: number
  discountAmountCents: number
  payableAmountCents: number
  couponId?: number
  couponName?: string
  message: string
  available: boolean
}

// ==================== 用户端 ====================

export function claimCoupon(templateId: number): Promise<UserCouponView> {
  return request<UserCouponView>({ url: `/coupons/${templateId}/claim`, method: 'post' })
}

export function myCoupons(status?: string, page = 1, size = 10): Promise<PageResult<UserCouponView>> {
  return request<PageResult<UserCouponView>>({
    url: '/coupons/mine',
    params: { status: status || undefined, page, size },
  })
}

export function availableCoupons(companionServiceId: number, durationMinutes: number): Promise<UserCouponView[]> {
  return request<UserCouponView[]>({
    url: '/coupons/available',
    params: { companionServiceId, durationMinutes },
  })
}

export function previewCoupon(companionServiceId: number, durationMinutes: number, couponId?: number): Promise<CouponPreviewResult> {
  return request<CouponPreviewResult>({
    url: '/coupons/preview',
    method: 'post',
    data: { companionServiceId, durationMinutes, couponId },
  })
}

// ==================== 管理端 ====================

export function adminCoupons(publishStatus?: string, page = 1, size = 10): Promise<PageResult<CouponTemplateView>> {
  return request<PageResult<CouponTemplateView>>({
    url: '/admin/coupons',
    params: { publishStatus: publishStatus || undefined, page, size },
  })
}

export function adminCreateCoupon(data: CouponTemplateForm): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({ url: '/admin/coupons', method: 'post', data })
}

export function adminUpdateCoupon(id: number, data: CouponTemplateForm): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({ url: `/admin/coupons/${id}`, method: 'put', data })
}

export function adminPublishCoupon(id: number): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({ url: `/admin/coupons/${id}/publish`, method: 'post' })
}

export function adminOfflineCoupon(id: number): Promise<CouponTemplateView> {
  return request<CouponTemplateView>({ url: `/admin/coupons/${id}/offline`, method: 'post' })
}

export function adminDeleteCoupon(id: number): Promise<void> {
  return request<void>({ url: `/admin/coupons/${id}`, method: 'delete' })
}
