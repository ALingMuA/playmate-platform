package com.gameplay.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 标签新增/编辑请求（FR-M12 标签管理）。
 */
@Data
public class TagRequest {

    /** 标签名称（1～50 字符，同一游戏同一分类下唯一） */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过50个字符")
    private String tagName;

    /** 标签分类：POSITION、STYLE、HERO、OTHER */
    @NotBlank(message = "标签分类不能为空")
    @Pattern(regexp = "^(POSITION|STYLE|HERO|OTHER)$", message = "标签分类仅支持 POSITION、STYLE、HERO、OTHER")
    private String tagCategory;

    /** 所属游戏ID，0表示通用标签 */
    @Min(value = 0, message = "所属游戏ID不能小于0")
    private Long gameId;

    /** 排序号，越小越靠前 */
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能超过9999")
    private Integer sortNo;

    /** 启用状态：0否，1是 */
    @Min(value = 0, message = "启用状态取值不合法（0否，1是）")
    @Max(value = 1, message = "启用状态取值不合法（0否，1是）")
    private Integer enabled;
}
