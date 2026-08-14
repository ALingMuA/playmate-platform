package com.gameplay.catalog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏基础表实体（对应 sql/schema.sql 中 game 表，FR-U01 游戏分类浏览、FR-M10 游戏管理）。
 */
@Data
@TableName("game")
public class Game {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏名称（唯一键 uk_game_name） */
    private String gameName;

    /** 游戏图标地址 */
    private String gameIconUrl;

    /** 游戏简介 */
    private String gameIntro;

    /** 排序号，越小越靠前 */
    private Integer sortNo;

    /** 启用状态：0否，1是（停用后不允许创建新服务和订单） */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
