package com.gameplay.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 游戏新增/编辑请求（FR-M10 游戏管理）。
 *
 * <p>创建与更新共用；gameName 必填，其余字段为空时按默认值落库。</p>
 */
@Data
public class GameRequest {

    /** 游戏名称（1～50 字符，全局唯一） */
    @NotBlank(message = "游戏名称不能为空")
    @Size(max = 50, message = "游戏名称不能超过50个字符")
    private String gameName;

    /** 游戏图标地址（可传空串表示无图标） */
    @Size(max = 500, message = "游戏图标地址不能超过500个字符")
    private String gameIconUrl;

    /** 游戏简介（可传空串） */
    @Size(max = 500, message = "游戏简介不能超过500个字符")
    private String gameIntro;

    /** 排序号，越小越靠前 */
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能超过9999")
    private Integer sortNo;

    /** 启用状态：0否，1是 */
    @Min(value = 0, message = "启用状态取值不合法（0否，1是）")
    @Max(value = 1, message = "启用状态取值不合法（0否，1是）")
    private Integer enabled;
}
