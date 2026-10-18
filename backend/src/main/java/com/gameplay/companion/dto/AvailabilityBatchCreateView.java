package com.gameplay.companion.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 档期批量/周期生成结果视图：成功条数与被跳过的日期明细（含跳过原因）。
 */
@Data
@Builder
public class AvailabilityBatchCreateView {

    /** 成功生成的档期条数 */
    private int createdCount;

    /** 因冲突被跳过的天数 */
    private int skippedCount;

    /** 跳过明细，如"2026-10-18：与已有档期重叠" */
    private List<String> skippedDetails;
}
