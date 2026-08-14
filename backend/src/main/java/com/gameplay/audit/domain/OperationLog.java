package com.gameplay.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志表实体（对应 `operation_log`，FR-M20）。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作者用户ID */
    private Long operatorId;

    /** 操作者角色 */
    private String operatorRole;

    /** 操作类型 */
    private String operationType;

    /** 目标对象类型 */
    private String targetType;

    /** 目标对象ID */
    private Long targetId;

    /** 变更前脱敏数据JSON */
    private String beforeData;

    /** 变更后脱敏数据JSON */
    private String afterData;

    /** 操作原因 */
    private String reason;

    /** 请求追踪ID */
    private String requestId;

    /** 操作IP地址 */
    private String ipAddress;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
