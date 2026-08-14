package com.gameplay.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告新增/编辑请求（FR-M13）。
 */
@Data
public class AnnouncementRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题过长")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容过长")
    private String content;
}
