package com.gameplay.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.audit.dto.OperationLogView;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作日志查询接口（FR-M20）。
 */
@RestController
@RequestMapping("/api/admin/operation-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    /** 分页查询操作日志 */
    @GetMapping
    public ApiResponse<Page<OperationLogView>> list(@RequestParam(required = false) Long operatorId,
                                                    @RequestParam(required = false) String operationType,
                                                    @RequestParam(required = false) String targetType,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(operationLogService.page(operatorId, operationType, targetType, page, size));
    }
}
