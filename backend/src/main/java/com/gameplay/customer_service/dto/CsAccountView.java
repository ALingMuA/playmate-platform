package com.gameplay.customer_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 客服账号视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsAccountView {

    private Long id;
    private Long userId;
    private String csAccount;
    private String csName;
    private String contactMobile;
    private String accountStatus;
    private String workStatus;
    /** 首次或重置后强制改密：0否，1是 */
    private Integer forceChangePassword;
    private Integer maxActiveConversations;
    private String disabledReason;
    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
