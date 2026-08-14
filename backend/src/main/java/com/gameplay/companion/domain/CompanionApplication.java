package com.gameplay.companion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩师入驻申请表实体（对应 `companion_application`，FR-P01~P04）。
 */
@Data
@TableName("companion_application")
public class CompanionApplication {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请用户ID */
    private Long applicantUserId;

    /** 真实姓名 */
    private String realName;

    /** 联系手机号 */
    private String contactMobile;

    /** 申请自我介绍 */
    private String introduction;

    /** 游戏能力JSON数组：[{"gameId":1,"gameName":"王者荣耀","server":"微信区","rank":"王者","positionTagIds":[1,2]}] */
    private String gameCapabilityJson;

    /** 能力证明图片地址JSON数组 */
    private String proofUrlsJson;

    /** 审核状态：PENDING、APPROVED、REJECTED */
    private String auditStatus;

    /** 审核管理员ID，未审核为0 */
    private Long auditBy;

    /** 审核意见或驳回原因 */
    private String auditReason;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
