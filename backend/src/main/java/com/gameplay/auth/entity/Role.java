package com.gameplay.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表实体（对应 DDL 1.1 节 `role` 表）。
 * 角色编码：USER、COMPANION、CUSTOMER_SERVICE、ADMIN。
 */
@Data
@TableName("role")
public class Role {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码：USER、COMPANION、CUSTOMER_SERVICE、ADMIN */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 启用状态：0否，1是 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
