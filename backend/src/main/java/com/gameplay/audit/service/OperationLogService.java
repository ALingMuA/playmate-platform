package com.gameplay.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.audit.domain.OperationLog;
import com.gameplay.audit.dto.OperationLogView;
import com.gameplay.audit.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 操作审计日志服务（FR-M20）。
 *
 * <p>供管理员对用户、陪玩师、订单、投诉、配置等关键操作调用；写入失败不影响主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /** 记录操作日志（失败静默，不抛业务异常） */
    @Transactional
    public void record(Long operatorId, String operatorRole, String operationType,
                       String targetType, Long targetId, String beforeData, String afterData,
                       String reason) {
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setOperatorId(operatorId == null ? 0L : operatorId);
            logEntry.setOperatorRole(operatorRole == null ? "" : operatorRole);
            logEntry.setOperationType(operationType);
            logEntry.setTargetType(targetType);
            logEntry.setTargetId(targetId == null ? 0L : targetId);
            logEntry.setBeforeData(beforeData == null ? "" : beforeData);
            logEntry.setAfterData(afterData == null ? "" : afterData);
            logEntry.setReason(reason == null ? "" : reason);
            logEntry.setRequestId(UUID.randomUUID().toString());
            logEntry.setIpAddress("");
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("操作日志写入失败: type={}, target={}, 原因: {}", operationType, targetType, e.getMessage());
        }
    }

    /** 分页查询（FR-M20） */
    public Page<OperationLogView> page(Long operatorId, String operationType, String targetType,
                                       long page, long size) {
        Page<OperationLog> p = operationLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>()
                        .eq(operatorId != null, OperationLog::getOperatorId, operatorId)
                        .eq(StringUtils.hasText(operationType), OperationLog::getOperationType, operationType)
                        .eq(StringUtils.hasText(targetType), OperationLog::getTargetType, targetType)
                        .orderByDesc(OperationLog::getId));
        Page<OperationLogView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    private OperationLogView toView(OperationLog logEntry) {
        return OperationLogView.builder()
                .id(logEntry.getId())
                .operatorId(logEntry.getOperatorId())
                .operatorRole(logEntry.getOperatorRole())
                .operationType(logEntry.getOperationType())
                .targetType(logEntry.getTargetType())
                .targetId(logEntry.getTargetId())
                .beforeData(logEntry.getBeforeData())
                .afterData(logEntry.getAfterData())
                .reason(logEntry.getReason())
                .requestId(logEntry.getRequestId())
                .ipAddress(logEntry.getIpAddress())
                .createdAt(logEntry.getCreatedAt())
                .build();
    }
}
