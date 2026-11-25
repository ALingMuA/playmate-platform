package com.gameplay.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.audit.domain.OperationLog;
import com.gameplay.audit.dto.OperationLogView;
import com.gameplay.audit.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * 操作审计日志服务（FR-M20）。
 *
 * <p>供管理员对用户、陪玩师、订单、投诉、配置等关键操作调用；
 * 使用独立事务并截断超长字段，确保写入失败不会污染调用方主事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private static final String REQ_ID_ATTR = OperationLogService.class.getName() + ".REQUEST_ID";

    private final OperationLogMapper operationLogMapper;

    /** 记录操作日志（独立事务，失败静默，不抛业务异常） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long operatorId, String operatorRole, String operationType,
                       String targetType, Long targetId, String beforeData, String afterData,
                       String reason) {
        try {
            HttpServletRequest request = currentRequest();
            OperationLog logEntry = new OperationLog();
            logEntry.setOperatorId(operatorId == null ? 0L : operatorId);
            logEntry.setOperatorRole(truncate(operatorRole, 32));
            logEntry.setOperationType(truncate(operationType, 50));
            logEntry.setTargetType(truncate(targetType, 50));
            logEntry.setTargetId(targetId == null ? 0L : targetId);
            logEntry.setBeforeData(truncate(beforeData, 4000));
            logEntry.setAfterData(truncate(afterData, 4000));
            logEntry.setReason(truncate(reason, 500));
            logEntry.setRequestId(resolveRequestId(request));
            logEntry.setIpAddress(resolveClientIp(request));
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

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        Object cached = request.getAttribute(REQ_ID_ATTR);
        if (cached instanceof String s && StringUtils.hasText(s)) {
            return truncate(s, 64);
        }
        String headerId = request.getHeader("X-Request-Id");
        String resolved = StringUtils.hasText(headerId)
                ? headerId.trim()
                : UUID.randomUUID().toString();
        resolved = truncate(resolved, 64);
        request.setAttribute(REQ_ID_ATTR, resolved);
        return resolved;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            String first = (comma >= 0 ? xff.substring(0, comma) : xff).trim();
            if (StringUtils.hasText(first)) {
                return truncate(first, 64);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return truncate(realIp.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
