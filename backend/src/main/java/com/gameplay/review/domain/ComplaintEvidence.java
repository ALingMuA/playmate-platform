package com.gameplay.review.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投诉证据表实体（对应 `complaint_evidence`，FR-U18）。
 * <p>证据仅存文件地址与原始文件名，文件实体由 file 模块管理。</p>
 */
@Data
@TableName("complaint_evidence")
public class ComplaintEvidence {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 投诉ID */
    private Long complaintId;

    /** 证据文件地址 */
    private String fileUrl;

    /** 原始文件名 */
    private String fileName;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
