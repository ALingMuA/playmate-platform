package com.gameplay.companion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 服务项目新增/编辑（FR-P07~P09）。
 */
@Data
public class ServiceRequest {

    /** 游戏ID */
    @NotNull(message = "请选择游戏")
    private Long gameId;

    /** 服务类型ID */
    @NotNull(message = "请选择服务类型")
    private Long serviceTypeId;

    /** 服务标题 */
    @NotBlank(message = "请填写服务标题")
    @Size(max = 100, message = "服务标题长度不能超过100")
    private String title;

    /** 服务说明 */
    @Size(max = 2000, message = "服务说明长度不能超过2000")
    private String description;

    /** 标签ID列表 */
    private List<Long> tagIds;

    /** 每小时价格，单位分 */
    @NotNull(message = "请填写价格")
    @Min(value = 100, message = "价格不能低于1元/小时")
    private Long priceCents;

    /** 最短服务时长，单位分钟 */
    @NotNull(message = "请填写最短服务时长")
    @Min(value = 30, message = "最短服务时长不能低于30分钟")
    private Integer minDurationMinutes;
}
