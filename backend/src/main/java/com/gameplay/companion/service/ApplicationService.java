package com.gameplay.companion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.entity.UserRole;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.auth.mapper.UserRoleMapper;
import com.gameplay.notification.service.NotificationService;
import com.gameplay.catalog.domain.Game;
import com.gameplay.catalog.mapper.GameMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionApplication;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.dto.ApplicationSubmitRequest;
import com.gameplay.companion.dto.ApplicationView;
import com.gameplay.companion.dto.CapabilityChangeSubmitRequest;
import com.gameplay.companion.dto.GameCapabilityDto;
import com.gameplay.companion.enums.ApplicationAuditStatus;
import com.gameplay.companion.enums.ApplicationType;
import com.gameplay.companion.mapper.CompanionApplicationMapper;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 陪玩师入驻申请服务（FR-P01~P04）。
 *
 * <p>申请记录只增不改：驳回后可再次提交（新记录），保留全部历史审核信息（FR-P04）。
 * 审核通过时创建陪玩师资料（companion_profile）并授予 COMPANION 角色。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final CompanionApplicationMapper applicationMapper;
    private final CompanionProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final GameMapper gameMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    /** 提交入驻申请（FR-P01/P02/P04） */
    @Transactional
    public ApplicationView submit(Long userId, ApplicationSubmitRequest req) {
        // 已是陪玩师则拒绝
        if (profileMapper.selectCount(new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, userId)) > 0) {
            throw new BusinessException(ErrorCode.COMPANION_ALREADY_APPROVED);
        }
        // 存在待审核的入驻申请则拒绝重复提交（能力变更申请不影响入驻判重）
        if (applicationMapper.selectCount(new LambdaQueryWrapper<CompanionApplication>()
                .eq(CompanionApplication::getApplicantUserId, userId)
                .eq(CompanionApplication::getApplicationType, ApplicationType.ENTRY.name())
                .eq(CompanionApplication::getAuditStatus, ApplicationAuditStatus.PENDING.name())) > 0) {
            throw new BusinessException(ErrorCode.COMPANION_APPLICATION_ALREADY_PENDING);
        }
        // 校验能力中的游戏存在且启用
        validateCapabilities(req.getCapabilities());

        CompanionApplication application = new CompanionApplication();
        application.setApplicantUserId(userId);
        application.setApplicationType(ApplicationType.ENTRY.name());
        application.setRealName(req.getRealName());
        application.setContactMobile(req.getContactMobile());
        application.setIntroduction(req.getIntroduction());
        application.setGameCapabilityJson(toJson(req.getCapabilities()));
        application.setProofUrlsJson(toJson(req.getProofUrls() == null ? List.of() : req.getProofUrls()));
        application.setAuditStatus(ApplicationAuditStatus.PENDING.name());
        application.setAuditBy(0L);
        application.setAuditReason("");
        applicationMapper.insert(application);
        return toView(application);
    }

    /**
     * 提交能力变更申请（陪玩师端）：游戏能力由平台审核，不可自行修改。
     *
     * <p>申请通过后用新能力整体覆盖陪玩主页；审核期间公开展示原能力不变。</p>
     */
    @Transactional
    public ApplicationView submitCapabilityChange(Long userId, CapabilityChangeSubmitRequest req) {
        // 必须已是陪玩师（主页存在）
        CompanionProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CompanionProfile>()
                .eq(CompanionProfile::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(ErrorCode.COMPANION_NOT_APPROVED);
        }
        // 存在待审核的能力变更申请则拒绝重复提交
        if (applicationMapper.selectCount(new LambdaQueryWrapper<CompanionApplication>()
                .eq(CompanionApplication::getApplicantUserId, userId)
                .eq(CompanionApplication::getApplicationType, ApplicationType.CAPABILITY_UPDATE.name())
                .eq(CompanionApplication::getAuditStatus, ApplicationAuditStatus.PENDING.name())) > 0) {
            throw new BusinessException(ErrorCode.COMPANION_CAPABILITY_CHANGE_PENDING);
        }
        validateCapabilities(req.getCapabilities());

        CompanionApplication application = new CompanionApplication();
        application.setApplicantUserId(userId);
        application.setApplicationType(ApplicationType.CAPABILITY_UPDATE.name());
        application.setRealName("");
        application.setContactMobile("");
        application.setIntroduction("");
        application.setGameCapabilityJson(toJson(req.getCapabilities()));
        application.setProofUrlsJson(toJson(req.getProofUrls() == null ? List.of() : req.getProofUrls()));
        application.setAuditStatus(ApplicationAuditStatus.PENDING.name());
        application.setAuditBy(0L);
        application.setAuditReason("");
        applicationMapper.insert(application);
        return toView(application);
    }

    /** 我的申请记录（最新在前，FR-P03） */
    public List<ApplicationView> myApplications(Long userId) {
        List<CompanionApplication> list = applicationMapper.selectList(
                new LambdaQueryWrapper<CompanionApplication>()
                        .eq(CompanionApplication::getApplicantUserId, userId)
                        .orderByDesc(CompanionApplication::getId));
        return list.stream().map(this::toView).toList();
    }

    /** 申请详情（本人） */
    public ApplicationView getDetail(Long userId, Long applicationId) {
        CompanionApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.COMPANION_APPLICATION_NOT_FOUND);
        }
        if (!application.getApplicantUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED);
        }
        return toView(application);
    }

    // ==================== 管理后台 ====================

    /** 申请分页（FR-M06），可按审核状态筛选 */
    public Page<ApplicationView> adminPage(String status, long page, long size) {
        LambdaQueryWrapper<CompanionApplication> wrapper = new LambdaQueryWrapper<CompanionApplication>()
                .orderByDesc(CompanionApplication::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(CompanionApplication::getAuditStatus, status);
        }
        Page<CompanionApplication> p = applicationMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ApplicationView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /** 审核通过/驳回（FR-M06）：通过后创建陪玩师资料并授予 COMPANION 角色 */
    @Transactional
    public void audit(Long applicationId, boolean approved, String reason, Long adminId) {
        CompanionApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.COMPANION_APPLICATION_NOT_FOUND);
        }
        if (!ApplicationAuditStatus.PENDING.name().equals(application.getAuditStatus())) {
            throw new BusinessException(ErrorCode.COMPANION_APPLICATION_NOT_PENDING);
        }
        application.setAuditStatus(approved
                ? ApplicationAuditStatus.APPROVED.name()
                : ApplicationAuditStatus.REJECTED.name());
        application.setAuditBy(adminId);
        application.setAuditReason(reason == null ? "" : reason);
        application.setAuditedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        notificationService.record(
                application.getApplicantUserId(),
                "AUDIT_RESULT",
                approved ? "陪玩师资质审核已通过" : "陪玩师资质审核未通过",
                approved ? "恭喜！您的陪玩师资质审核已通过。" : ("很遗憾，您的审核申请未通过。原因：" + (reason == null ? "" : reason)),
                "COMPANION",
                application.getId(),
                "AUDIT:COMPANION:" + application.getId() + ":" + (approved ? "APPROVED" : "REJECTED")
        );

        // 能力变更申请：通过后仅覆盖陪玩主页的游戏能力，不重复建主页/授角色；
        // 驳回则公开展示的能力保持不变
        if (approved && ApplicationType.CAPABILITY_UPDATE.name().equals(application.getApplicationType())) {
            CompanionProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CompanionProfile>()
                    .eq(CompanionProfile::getUserId, application.getApplicantUserId()));
            if (profile == null) {
                throw new BusinessException(ErrorCode.COMPANION_PROFILE_NOT_FOUND);
            }
            profile.setCapabilityJson(application.getGameCapabilityJson());
            profileMapper.updateById(profile);
            return;
        }

        if (approved) {
            User user = userMapper.selectById(application.getApplicantUserId());
            if (user == null) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
            }
            // 创建陪玩师资料（幂等：重复审核同一申请已被上面的状态校验拦截）
            CompanionProfile profile = new CompanionProfile();
            profile.setUserId(application.getApplicantUserId());
            profile.setDisplayName(user.getNickname());
            profile.setProfileIntro("");
            profile.setCapabilityJson(application.getGameCapabilityJson());
            profile.setRatingAvg(new java.math.BigDecimal("5.0"));
            profile.setRatingCount(0);
            profile.setCompletedOrderCount(0);
            profile.setServiceStatus("RESTING");
            profile.setCertificationStatus("APPROVED");
            profileMapper.insert(profile);
            // 授予 COMPANION 角色（幂等：uk_user_role 唯一键兜底）
            Role companionRole = roleMapper.selectByRoleCode("COMPANION");
            if (companionRole != null && userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, application.getApplicantUserId())
                    .eq(UserRole::getRoleId, companionRole.getId())) == 0) {
                UserRole relation = new UserRole();
                relation.setUserId(application.getApplicantUserId());
                relation.setRoleId(companionRole.getId());
                try {
                    userRoleMapper.insert(relation);
                } catch (Exception e) {
                    log.debug("COMPANION 角色已存在，忽略重复授权", e);
                }
            }
        }
    }

    // ==================== 工具 ====================

    /** 校验能力中的游戏存在且启用 */
    private void validateCapabilities(List<GameCapabilityDto> capabilities) {
        for (GameCapabilityDto cap : capabilities) {
            Game game = gameMapper.selectById(cap.getGameId());
            if (game == null || !Integer.valueOf(1).equals(game.getEnabled())) {
                throw new BusinessException(ErrorCode.GAME_NOT_FOUND, "游戏不存在或已停用");
            }
        }
    }

    private ApplicationView toView(CompanionApplication a) {
        return ApplicationView.builder()
                .id(a.getId())
                .applicationType(a.getApplicationType() == null ? ApplicationType.ENTRY.name() : a.getApplicationType())
                .realName(a.getRealName())
                .contactMobile(a.getContactMobile())
                .introduction(a.getIntroduction())
                .capabilities(fromJson(a.getGameCapabilityJson(), new TypeReference<List<GameCapabilityDto>>() {}))
                .proofUrls(fromJson(a.getProofUrlsJson(), new TypeReference<List<String>>() {}))
                .auditStatus(a.getAuditStatus())
                .auditReason(a.getAuditReason())
                .auditedAt(a.getAuditedAt())
                .createdAt(a.getCreatedAt())
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
