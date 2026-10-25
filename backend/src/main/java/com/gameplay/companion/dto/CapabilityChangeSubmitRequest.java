package com.gameplay.companion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 能力变更申请提交（陪玩师端）。
 *
 * <p>游戏能力认证由平台审核：陪玩师提交新的能力列表与证明材料，
 * 管理员审核通过后整体覆盖陪玩主页的游戏能力；审核期间公开展示原能力不变。</p>
 */
@Data
public class CapabilityChangeSubmitRequest {

    /** 变更后的完整游戏能力列表（审核通过后整体覆盖） */
    @NotEmpty(message = "至少填写一项游戏能力")
    @Valid
    @Size(max = 6, message = "游戏能力最多 6 项")
    private List<GameCapabilityDto> capabilities;

    /** 能力证明图片地址 */
    @Size(max = 9, message = "证明图片最多 9 张")
    private List<String> proofUrls;
}
