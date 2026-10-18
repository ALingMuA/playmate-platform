package com.gameplay.companion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 恢复按作息结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreDayResult {

    /** 清理的手动记录数 */
    private int removedManual;

    /** 清理的模板生成实例数 */
    private int removedGenerated;

    /** 重新生成的实例数 */
    private int regenerated;

    /** 跳过原因列表（如有） */
    private List<String> skipped;
}
