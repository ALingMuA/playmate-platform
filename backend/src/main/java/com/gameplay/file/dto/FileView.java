package com.gameplay.file.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传结果视图（概要设计 4.7）。
 */
@Data
@Builder
public class FileView {

    /** 可访问 URL（如 /api/files/evidence/20260811/uuid.jpg） */
    private String url;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long sizeBytes;
}
