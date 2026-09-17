package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.domain.ServiceType;
import com.gameplay.catalog.domain.Tag;
import com.gameplay.catalog.mapper.GameMapper;
import com.gameplay.catalog.mapper.ServiceTypeMapper;
import com.gameplay.catalog.mapper.TagMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionService;
import com.gameplay.companion.dto.ServiceRequest;
import com.gameplay.companion.dto.ServiceView;
import com.gameplay.companion.enums.ServiceAuditStatus;
import com.gameplay.companion.enums.ShelfStatus;
import com.gameplay.companion.mapper.CompanionServiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 陪玩服务项目管理服务（FR-P07~P09）。
 *
 * <p>新增与编辑后的服务进入待审核；历史订单使用下单快照，服务修改不影响已有订单。
 * 只有审核通过（APPROVED）且上架（ON_SHELF）的服务可被预约。</p>
 */
@Service
@RequiredArgsConstructor
public class CompanionServiceService {

    private final CompanionServiceMapper serviceMapper;
    private final GameMapper gameMapper;
    private final ServiceTypeMapper serviceTypeMapper;
    private final TagMapper tagMapper;
    private final ObjectMapper objectMapper;

    /** 我的服务分页（FR-P18 服务列表） */
    public Page<ServiceView> listMy(Long userId, long page, long size) {
        Page<CompanionService> p = serviceMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<CompanionService>()
                        .eq(CompanionService::getCompanionUserId, userId)
                        .orderByDesc(CompanionService::getId));
        Page<ServiceView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /** 新增服务项目（FR-P07），创建后待审核 */
    @Transactional
    public ServiceView create(Long userId, ServiceRequest req) {
        validateRefs(req);
        CompanionService service = new CompanionService();
        service.setCompanionUserId(userId);
        applyRequest(service, req);
        service.setAuditStatus(ServiceAuditStatus.PENDING.name());
        service.setServiceStatus(ShelfStatus.OFF_SHELF.name());
        service.setAuditBy(0L);
        service.setAuditReason("");
        serviceMapper.insert(service);
        return toView(service);
    }

    /** 编辑服务项目（FR-P08）：重要信息变更后重新进入待审核（FR-P09） */
    @Transactional
    public ServiceView update(Long userId, Long serviceId, ServiceRequest req) {
        CompanionService service = requireOwnService(userId, serviceId);
        validateRefs(req);
        applyRequest(service, req);
        service.setAuditStatus(ServiceAuditStatus.PENDING.name());
        service.setAuditReason("");
        service.setAuditBy(0L);
        service.setAuditAt(null);
        serviceMapper.updateById(service);
        return toView(service);
    }

    /** 上架/下架（FR-P08）：上架要求审核已通过 */
    @Transactional
    public ServiceView setShelf(Long userId, Long serviceId, boolean onShelf) {
        CompanionService service = requireOwnService(userId, serviceId);
        if (onShelf && !ServiceAuditStatus.APPROVED.name().equals(service.getAuditStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_SERVICE_NOT_APPROVED);
        }
        service.setServiceStatus(onShelf ? ShelfStatus.ON_SHELF.name() : ShelfStatus.OFF_SHELF.name());
        serviceMapper.updateById(service);
        return toView(service);
    }

    /** 我的服务详情 */
    public ServiceView getMyDetail(Long userId, Long serviceId) {
        return toView(requireOwnService(userId, serviceId));
    }

    /**
     * 公开可预约服务分页（FR-U02/U03）：仅 APPROVED + ON_SHELF，可按游戏/陪玩师筛选。
     *
     * <p>同时要求所属游戏处于启用状态（FR-M10）：管理员停用游戏后，其已上架服务不再对用户可见；
     * 用子查询完成过滤，保证分页 total 仍然准确（若改为查询后过滤会破坏分页计数）。</p>
     */
    public Page<ServiceView> listBookable(Long gameId, Long companionUserId, long page, long size) {
        LambdaQueryWrapper<CompanionService> wrapper = new LambdaQueryWrapper<CompanionService>()
                .eq(CompanionService::getAuditStatus, ServiceAuditStatus.APPROVED.name())
                .eq(CompanionService::getServiceStatus, ShelfStatus.ON_SHELF.name())
                .inSql(CompanionService::getGameId, "SELECT id FROM game WHERE enabled = 1")
                .orderByDesc(CompanionService::getId);
        if (gameId != null) {
            wrapper.eq(CompanionService::getGameId, gameId);
        }
        if (companionUserId != null) {
            wrapper.eq(CompanionService::getCompanionUserId, companionUserId);
        }
        Page<CompanionService> p = serviceMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ServiceView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /** 管理端：服务分页（FR-M09 服务项目审核），可按审核状态筛选 */
    public Page<ServiceView> adminPage(String auditStatus, long page, long size) {
        LambdaQueryWrapper<CompanionService> wrapper = new LambdaQueryWrapper<CompanionService>()
                .orderByDesc(CompanionService::getId);
        if (auditStatus != null && !auditStatus.isBlank()) {
            wrapper.eq(CompanionService::getAuditStatus, auditStatus);
        }
        Page<CompanionService> p = serviceMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ServiceView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /** 管理端：审核通过/驳回（FR-M09）；通过后陪玩师可自行上架 */
    @Transactional
    public void adminAudit(Long serviceId, boolean approved, String reason, Long adminId) {
        CompanionService service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new BusinessException(ErrorCode.COMPANION_SERVICE_NOT_FOUND);
        }
        if (!ServiceAuditStatus.PENDING.name().equals(service.getAuditStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_SERVICE_REJECTED, "该服务不在待审核状态");
        }
        service.setAuditStatus(approved
                ? ServiceAuditStatus.APPROVED.name()
                : ServiceAuditStatus.REJECTED.name());
        service.setAuditBy(adminId);
        service.setAuditReason(reason == null ? "" : reason);
        service.setAuditAt(LocalDateTime.now());
        serviceMapper.updateById(service);
    }

    /** 按ID读取服务（供订单模块校验，包含审核/上架状态） */
    public CompanionService requireServiceById(Long serviceId) {
        CompanionService service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new BusinessException(ErrorCode.COMPANION_SERVICE_NOT_FOUND);
        }
        return service;
    }

    private CompanionService requireOwnService(Long userId, Long serviceId) {
        CompanionService service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new BusinessException(ErrorCode.COMPANION_SERVICE_NOT_FOUND);
        }
        if (!service.getCompanionUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return service;
    }

    private void validateRefs(ServiceRequest req) {
        Game game = gameMapper.selectById(req.getGameId());
        if (game == null || !Integer.valueOf(1).equals(game.getEnabled())) {
            throw new BusinessException(ErrorCode.GAME_NOT_FOUND, "游戏不存在或已停用");
        }
        ServiceType type = serviceTypeMapper.selectById(req.getServiceTypeId());
        if (type == null || !Integer.valueOf(1).equals(type.getEnabled())) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_NOT_FOUND, "服务类型不存在或已停用");
        }
        if (req.getTagIds() != null) {
            for (Long tagId : req.getTagIds()) {
                if (tagMapper.selectById(tagId) == null) {
                    throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
                }
            }
        }
    }

    private void applyRequest(CompanionService service, ServiceRequest req) {
        service.setGameId(req.getGameId());
        service.setServiceTypeId(req.getServiceTypeId());
        service.setTitle(req.getTitle());
        service.setDescription(req.getDescription() == null ? "" : req.getDescription());
        service.setTagIdsJson(toJson(req.getTagIds() == null ? List.of() : req.getTagIds()));
        service.setPriceCents(req.getPriceCents());
        service.setMinDurationMinutes(req.getMinDurationMinutes());
        service.setUpdatedAt(LocalDateTime.now());
    }

    private ServiceView toView(CompanionService s) {
        Game game = gameMapper.selectById(s.getGameId());
        ServiceType type = serviceTypeMapper.selectById(s.getServiceTypeId());
        List<Long> tagIds = fromJson(s.getTagIdsJson(), new TypeReference<List<Long>>() {});
        // 标签分两组：仍启用的正常展示，已停用/已删除的单独返回，供前端加"已停用"标注（FR-M12 收口）
        List<String> tagNames = new ArrayList<>();
        List<String> disabledTagNames = new ArrayList<>();
        for (Long tagId : tagIds) {
            Tag tag = tagMapper.selectById(tagId);
            if (tag != null && Integer.valueOf(1).equals(tag.getEnabled())) {
                tagNames.add(tag.getTagName());
            } else {
                disabledTagNames.add(tag == null ? "标签#" + tagId : tag.getTagName());
            }
        }
        return ServiceView.builder()
                .id(s.getId())
                .companionUserId(s.getCompanionUserId())
                .gameId(s.getGameId())
                .gameName(game == null ? "" : game.getGameName())
                .serviceTypeId(s.getServiceTypeId())
                .serviceTypeName(type == null ? "" : type.getTypeName())
                .serviceTypeEnabled(type == null ? 0 : type.getEnabled())
                .title(s.getTitle())
                .description(s.getDescription())
                .tagIds(tagIds)
                .tagNames(tagNames)
                .disabledTagNames(disabledTagNames)
                .priceCents(s.getPriceCents())
                .minDurationMinutes(s.getMinDurationMinutes())
                .auditStatus(s.getAuditStatus())
                .serviceStatus(s.getServiceStatus())
                .auditReason(s.getAuditReason())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据序列化失败");
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return objectMapper.convertValue(new ArrayList<>(), type);
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据解析失败");
        }
    }
}