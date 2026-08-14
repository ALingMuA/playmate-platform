package com.gameplay.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投诉证据视图（FR-U18）。
 */
@Data
@Builder
public class ComplaintEvidenceView {

    /** 证据ID */
    private Long id;

    /** 文件地址 */
    private String fileUrl;

    /** 原始文件名 */
    private String fileName;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
