package com.gameplay.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发起投诉请求（FR-U17/U18）。
 */
@Data
public class ComplaintCreateRequest {

    /** 关联订单ID（由 Controller 注入，不在请求体校验） */
    private Long orderId;

    /** 投诉类型：NO_FULFILLMENT、LATE、ATTITUDE、MISMATCH、OTHER */
    @NotBlank(message = "投诉类型不能为空")
    private String complaintType;

    /** 投诉说明 */
    @NotBlank(message = "投诉说明不能为空")
    @Size(max = 2000, message = "投诉说明最长2000字")
    private String description;

    /** 图片证据（最多5张，FR-U18 有限数量） */
    @Size(max = 5, message = "图片证据最多5张")
    private List<EvidenceItem> evidences;

    /** 证据项 */
    @Data
    public static class EvidenceItem {

        /** 文件地址（file 模块上传返回的 url） */
        @NotBlank(message = "证据文件地址不能为空")
        private String fileUrl;

        /** 原始文件名 */
        @Size(max = 200, message = "文件名最长200字")
        private String fileName;
    }
}
