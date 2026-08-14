package com.gameplay.companion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 入驻申请提交（FR-P01/P02/P04）。
 */
@Data
public class ApplicationSubmitRequest {

    /** 真实姓名 */
    @NotBlank(message = "请填写真实姓名")
    @Size(max = 32, message = "真实姓名长度不能超过32")
    private String realName;

    /** 联系手机号 */
    @NotBlank(message = "请填写联系手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String contactMobile;

    /** 申请自我介绍 */
    @NotBlank(message = "请填写自我介绍")
    @Size(max = 1000, message = "自我介绍长度不能超过1000")
    private String introduction;

    /** 游戏能力认证列表 */
    @NotEmpty(message = "至少填写一项游戏能力")
    @Valid
    private List<GameCapabilityDto> capabilities;

    /** 能力证明图片地址 */
    private List<String> proofUrls;
}
