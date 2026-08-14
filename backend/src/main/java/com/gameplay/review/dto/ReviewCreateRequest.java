package com.gameplay.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交评价请求（FR-U15）。
 */
@Data
public class ReviewCreateRequest {

    /** 评分：1至5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于1")
    @Max(value = 5, message = "评分不能高于5")
    private Integer score;

    /** 评价标签（如：上分快、脾气好、准时） */
    @Size(max = 5, message = "评价标签最多5个")
    private List<@NotBlank(message = "评价标签不能为空") @Size(max = 20, message = "单个标签最长20字") String> tags;

    /** 评价内容 */
    @NotBlank(message = "评价内容不能为空")
    @Size(max = 1000, message = "评价内容最长1000字")
    private String content;
}
