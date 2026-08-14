package com.gameplay.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建预约订单（FR-U07/U08）。
 */
@Data
public class CreateOrderRequest {

    /** 陪玩师用户ID */
    @NotNull(message = "请选择陪玩师")
    private Long companionUserId;

    /** 服务项目ID */
    @NotNull(message = "请选择服务项目")
    private Long companionServiceId;

    /** 预约时长，单位分钟 */
    @NotNull(message = "请选择服务时长")
    @Min(value = 30, message = "服务时长不能低于30分钟")
    private Integer durationMinutes;

    /** 游戏区服 */
    @Size(max = 50, message = "区服长度不能超过50")
    private String gameServer;

    /** 游戏昵称 */
    @Size(max = 50, message = "游戏昵称长度不能超过50")
    private String gameNickname;

    /** 用户需求备注 */
    @Size(max = 500, message = "备注长度不能超过500")
    private String userRemark;

    /** 预约开始时间 */
    @NotNull(message = "请选择预约开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appointmentStartAt;
}
