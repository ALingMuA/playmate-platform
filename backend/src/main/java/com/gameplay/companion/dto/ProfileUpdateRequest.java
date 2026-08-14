package com.gameplay.companion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 陪玩主页维护（FR-P05）。
 */
@Data
public class ProfileUpdateRequest {

    /** 陪玩师展示名 */
    @NotBlank(message = "请填写展示名")
    @Size(max = 32, message = "展示名长度不能超过32")
    private String displayName;

    /** 主页简介 */
    @Size(max = 1000, message = "主页简介长度不能超过1000")
    private String profileIntro;

    /** 认证游戏能力列表（更新后随主页展示） */
    @Valid
    private List<GameCapabilityDto> capabilities;
}
