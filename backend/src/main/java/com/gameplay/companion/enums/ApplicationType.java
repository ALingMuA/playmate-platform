package com.gameplay.companion.enums;

/**
 * 陪玩师申请类型：区分入驻申请与陪玩师发起的能力变更申请。
 *
 * <p>两者复用 companion_application 表与同一审核后台；能力变更审核通过后
 * 覆盖陪玩主页的游戏能力，不再重复授予 COMPANION 角色。</p>
 */
public enum ApplicationType {

    /** 入驻申请（FR-P01~P04）：通过后创建陪玩主页并授予 COMPANION 角色 */
    ENTRY,

    /** 能力变更申请：通过后覆盖陪玩主页的游戏能力认证 */
    CAPABILITY_UPDATE
}
