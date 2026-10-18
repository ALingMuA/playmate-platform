package com.gameplay.companion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gameplay.companion.enums.AvailabilityStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 周日程批量变更请求：一次保存多个连续区间，服务端在同一事务内处理。
 */
@Data
public class AvailabilityChangeRequest {

    /** 本次提交的区间变更，最多 100 段 */
    @NotEmpty(message = "请至少选择一个时间段")
    @Size(max = 100, message = "单次最多修改100个时间段")
    private List<@Valid Item> changes;

    @Data
    public static class Item {

        @NotNull(message = "请选择开始时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startAt;

        @NotNull(message = "请选择结束时间")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endAt;

        @NotNull(message = "请选择档期状态")
        private AvailabilityStatus status;
    }
}
