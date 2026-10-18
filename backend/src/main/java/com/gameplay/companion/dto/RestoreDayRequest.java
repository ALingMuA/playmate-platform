package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 恢复按作息请求（单日撤销）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestoreDayRequest {

    /** 待恢复的日期（yyyy-MM-dd） */
    @NotNull(message = "请指定待恢复的日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}
