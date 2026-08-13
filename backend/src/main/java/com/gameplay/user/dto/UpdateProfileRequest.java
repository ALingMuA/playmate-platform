package com.gameplay.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新请求（FR-A05）。
 *
 * <p>所有字段可选：为 null 的字段不更新；avatarUrl、introduction 传空串表示清空；
 * nickname 不允许空白。</p>
 */
@Data
public class UpdateProfileRequest {

    /** 展示昵称（1～32 字符，不允许空白） */
    @Size(min = 1, max = 32)
    private String nickname;

    /** 头像文件地址（可传空串移除头像） */
    @Size(max = 500)
    private String avatarUrl;

    /** 性别：0未知，1男，2女 */
    @Min(0)
    @Max(2)
    private Integer gender;

    /** 个人简介（可传空串清空） */
    @Size(max = 500)
    private String introduction;
}
