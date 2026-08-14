package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人工客服账号表实体（对应 `customer_service_account`，FR-C01~C05）。
 * <p>客服账号关联内部用户账号（user_id），登录口令与令牌版本复用用户体系。</p>
 */
@Data
@TableName("customer_service_account")
public class CustomerServiceAccount {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联内部用户账号ID */
    private Long userId;

    /** 客服账号名 */
    private String csAccount;

    /** 客服姓名 */
    private String csName;

    /** 客服联系方式 */
    private String contactMobile;

    /** 账号状态：ENABLED、DISABLED */
    private String accountStatus;

    /** 工作状态：ONLINE、BUSY、OFFLINE */
    private String workStatus;

    /** 首次或重置后强制改密：0否，1是 */
    private Integer forceChangePassword;

    /** 最大并行处理会话数 */
    private Integer maxActiveConversations;

    /** 禁用原因 */
    private String disabledReason;

    /** 创建管理员ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
