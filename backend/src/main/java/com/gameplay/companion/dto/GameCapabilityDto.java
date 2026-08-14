package com.gameplay.companion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 游戏能力认证项（FR-P02）：游戏、区服、段位与擅长位置标签。
 */
@Data
public class GameCapabilityDto {

    /** 游戏ID */
    @NotNull(message = "请选择游戏")
    private Long gameId;

    /** 游戏名称（冗余展示用，服务端以 gameId 校验为准） */
    private String gameName;

    /** 游戏区服 */
    @NotBlank(message = "请填写游戏区服")
    @Size(max = 50, message = "区服长度不能超过50")
    private String server;

    /** 段位 */
    @NotBlank(message = "请填写段位")
    @Size(max = 50, message = "段位长度不能超过50")
    private String rank;

    /** 擅长位置/英雄标签ID */
    private List<Long> positionTagIds;
}
