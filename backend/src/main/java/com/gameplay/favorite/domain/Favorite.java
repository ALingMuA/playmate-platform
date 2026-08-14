package com.gameplay.favorite.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 陪玩师收藏表实体（对应 `favorite`，FR-U06）。
 */
@Data
@TableName("favorite")
public class Favorite {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 被收藏陪玩师ID */
    private Long companionUserId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
