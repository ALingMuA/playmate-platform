package com.gameplay.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 服务类型新增/编辑请求（FR-M11 服务类型管理）。
 */
@Data
public class ServiceTypeRequest {

    /** 服务类型名称（1～50 字符，唯一） */
    @NotBlank(message = "服务类型名称不能为空")
    @Size(max = 50, message = "服务类型名称不能超过50个字符")
    private String typeName;

    /** 服务类型编码（字母开头，可含数字与下划线，最长32位，唯一） */
    @NotBlank(message = "服务类型编码不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,31}$", message = "服务类型编码需以字母开头，仅含字母、数字、下划线，最长32位")
    private String typeCode;

    /** 类型说明（可传空串） */
    @Size(max = 200, message = "类型说明不能超过200个字符")
    private String description;

    /** 排序号，越小越靠前 */
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能超过9999")
    private Integer sortNo;

    /** 启用状态：0否，1是 */
    @Min(value = 0, message = "启用状态取值不合法（0否，1是）")
    @Max(value = 1, message = "启用状态取值不合法（0否，1是）")
    private Integer enabled;
}
