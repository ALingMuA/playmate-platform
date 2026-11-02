package com.gameplay.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单条陪玩师履约凭证上传引用。 */
@Data
public class DeliveryEvidenceRequest {

    @NotBlank(message = "凭证文件地址不能为空")
    @Pattern(regexp = "^/api/files/delivery/\\d{8}/[0-9a-fA-F]{32}\\.(jpg|jpeg|png|gif|webp)$",
            message = "凭证文件地址无效")
    private String fileUrl;

    @Size(max = 200, message = "文件名最长200字")
    private String fileName;
}
