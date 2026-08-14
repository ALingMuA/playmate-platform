package com.gameplay.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩服务类型表实体（对应 sql/schema.sql 中 service_type 表，FR-M11 服务类型管理）。
 */
@Data
@TableName("service_type")
public class ServiceType {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务类型名称（唯一键 uk_st_name） */
    private String typeName;

    /** 服务类型编码（唯一键 uk_st_code，如 TEAM_UP） */
    private String typeCode;

    /** 类型说明 */
    private String description;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 启用状态：0否，1是 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
