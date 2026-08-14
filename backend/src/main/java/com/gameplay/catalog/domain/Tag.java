package com.gameplay.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务标签表实体（对应 sql/schema.sql 中 tag 表，FR-M12 标签管理）。
 *
 * <p>标签用于描述陪玩师的游戏位置、擅长英雄、风格等能力，
 * 唯一键为 (game_id, tag_category, tag_name)；game_id=0 表示通用标签。</p>
 */
@Data
@TableName("tag")
public class Tag {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称 */
    private String tagName;

    /** 标签分类：POSITION（位置）、STYLE（风格）、HERO（英雄）、OTHER（其他） */
    private String tagCategory;

    /** 所属游戏ID，0表示通用标签 */
    private Long gameId;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 启用状态：0否，1是 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
