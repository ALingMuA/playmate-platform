package com.gameplay.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员投诉处理请求（FR-M18/M19）。
 */
@Data
public class ComplaintHandleRequest {

    /** 处理类型：KEEP、FULL_REFUND、PARTIAL_REFUND */
    @NotBlank(message = "处理类型不能为空")
    private String resolutionType;

    /** 退款金额（分）：PARTIAL_REFUND 必填，须大于0且不超过订单金额 */
    private Long refundAmountCents;

    /** 处理意见 */
    @NotBlank(message = "处理意见不能为空")
    @Size(max = 1000, message = "处理意见最长1000字")
    private String handlingOpinion;
}
