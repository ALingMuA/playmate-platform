package com.gameplay.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 演示余额发放请求。
 */
@Data
public class DemoWalletGrantRequest {

    /** 目标用户ID */
    @NotNull(message = "目标用户ID不能为空")
    @Positive(message = "目标用户ID必须为正数")
    private Long userId;

    /** 发放金额（分） */
    @NotNull(message = "发放金额不能为空")
    @Positive(message = "发放金额必须为正整数")
    private Long amountCents;

    /** 发放原因 */
    @NotBlank(message = "发放原因不能为空")
    @Size(max = 200, message = "发放原因不能超过200字")
    private String reason;

    /** 请求幂等唯一号 */
    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64字符")
    private String requestId;
}
