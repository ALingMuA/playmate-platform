package com.gameplay.customer_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客服领取会话请求（FR-C25：乐观锁 expectedVersion）。
 */
@Data
public class ConversationClaimRequest {

    /** 期望会话版本号（乐观锁） */
    @NotNull(message = "expectedVersion 不能为空")
    @Min(value = 0, message = "expectedVersion 不合法")
    private Integer expectedVersion;

    /** 领取备注 */
    @Size(max = 300, message = "领取备注过长")
    private String claimRemark;
}
