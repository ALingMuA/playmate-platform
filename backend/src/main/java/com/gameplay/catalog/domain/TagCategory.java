package com.gameplay.catalog.domain;

import java.util.Arrays;

/**
 * 标签分类枚举（对应 tag 表 tag_category 字段，FR-M12）。
 *
 * <p>当前支持：POSITION 游戏位置、STYLE 陪玩风格、HERO 擅长英雄、OTHER 其他。</p>
 */
public enum TagCategory {

    /** 游戏位置（如打野、中单、指挥位） */
    POSITION,

    /** 陪玩风格（如幽默、技术流、耐心教学） */
    STYLE,

    /** 擅长英雄 */
    HERO,

    /** 其他自定义分类 */
    OTHER;

    /** 判断取值是否为合法分类名 */
    public static boolean isValid(String value) {
        return value != null && Arrays.stream(values()).anyMatch(c -> c.name().equals(value));
    }
}
