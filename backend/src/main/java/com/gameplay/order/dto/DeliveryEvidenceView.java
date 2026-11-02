package com.gameplay.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 履约凭证视图。 */
@Data
@Builder
public class DeliveryEvidenceView {

    private Long id;
    private String fileUrl;
    private String fileName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
