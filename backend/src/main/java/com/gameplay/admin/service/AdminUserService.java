package com.gameplay.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.admin.dto.UserAdminView;
import com.gameplay.admin.dto.UserResetPasswordRequest;
import com.gameplay.admin.dto.UserStatusUpdateRequest;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.entity.UserRole;
import com.gameplay.auth.event.SessionInvalidatedEvent;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.auth.mapper.UserRoleMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import com.gameplay.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端用户管理服务（FR-M03 查询、FR-M04 启用/禁用、重置密码）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final CompanionProfileMapper companionProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /** 用户分页查询（FR-M03）：按账号/昵称/手机号关键词与状态过滤，批量加载角色与陪玩资料避免 N+1 */
    public Page<UserAdminView> page(String keyword, String accountStatus, long page, long size) {
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(User::getUsername, keyword)
                                .or().like(User::getNickname, keyword)
                                .or().like(User::getMobile, keyword))
                        .eq(StringUtils.hasText(accountStatus), User::getAccountStatus, accountStatus)
                        .orderByDesc(User::getId));
        Page<UserAdminView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        List<User> records = p.getRecords();
        if (records.isEmpty()) {
            vp.setRecords(List.of());
            return vp;
        }
        List<Long> userIds = records.stream().map(User::getId).toList();
        Map<Long, List<String>> rolesByUserId = loadRolesBatch(userIds);
        Map<Long, CompanionProfile> profileByUserId = companionProfileMapper.selectList(
                        new LambdaQueryWrapper<CompanionProfile>()
                                .in(CompanionProfile::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(CompanionProfile::getUserId, Function.identity(), (a, b) -> a));

        vp.setRecords(records.stream()
                .map(user -> toView(
                        user,
                        rolesByUserId.getOrDefault(user.getId(), List.of()),
                        profileByUserId.get(user.getId())))
                .toList());
        return vp;
    }

    /** 启用/禁用用户（FR-M04）：禁止自禁用及禁用管理员，禁用时递增令牌版本并通知长连接下线 */
    @Transactional
    public UserAdminView setStatus(Long adminId, Long userId, UserStatusUpdateRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!"ENABLED".equals(req.getAccountStatus()) && !"DISABLED".equals(req.getAccountStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账号状态不合法");
        }
        if ("DISABLED".equals(req.getAccountStatus())) {
            if (adminId != null && adminId.equals(userId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不能禁用当前登录的管理员账号");
            }
            List<String> targetRoles = roleMapper.selectRolesByUserId(userId).stream()
                    .map(Role::getRoleCode)
                    .toList();
            if (targetRoles.contains("ADMIN")) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "不能禁用管理员账号");
            }
            if (!StringUtils.hasText(req.getReason())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "禁用用户必须填写原因");
            }
        }
        String beforeStatus = user.getAccountStatus();
        String trimmedReason = req.getReason() == null ? "" : req.getReason().trim();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getAccountStatus, req.getAccountStatus())
                .set(User::getDisabledReason, "DISABLED".equals(req.getAccountStatus()) ? trimmedReason : "")
                .eq(User::getId, userId));
        if ("DISABLED".equals(req.getAccountStatus())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .setSql("token_version = token_version + 1")
                    .eq(User::getId, userId));
            eventPublisher.publishEvent(new SessionInvalidatedEvent(userId));
        }

        operationLogService.record(
                adminId,
                "ADMIN",
                "USER_STATUS_CHANGE",
                "USER",
                userId,
                "{\"accountStatus\":\"" + beforeStatus + "\"}",
                "{\"accountStatus\":\"" + req.getAccountStatus() + "\"}",
                trimmedReason
        );

        return toView(userMapper.selectById(userId));
    }

    /** 重置用户密码：更新哈希、递增令牌版本使旧会话失效、记录脱敏审计并发送站内通知 */
    @Transactional
    public UserAdminView resetPassword(Long adminId, Long userId, UserResetPasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (adminId != null && adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "重置自身密码请前往个人中心修改");
        }

        // 原子递增令牌版本，保证并发操作下版本号严格单调递增并触发旧会话失效
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getPasswordHash, passwordEncoder.encode(req.getNewPassword()))
                .setSql("token_version = token_version + 1")
                .eq(User::getId, userId));
        eventPublisher.publishEvent(new SessionInvalidatedEvent(userId));

        operationLogService.record(
                adminId,
                "ADMIN",
                "RESET_USER_PASSWORD",
                "USER",
                userId,
                "",
                "密码已重置，令牌版本已递增",
                req.getReason()
        );

        notificationService.record(
                userId,
                "SYSTEM",
                "安全提醒：您的登录密码已被管理员重置",
                "您的账号密码已由平台管理员重置，原有登录设备已全部下线。若非本人申请，请及时联系客服核实。",
                "USER",
                userId,
                "USER:" + userId + ":RESET_PASSWORD:" + System.currentTimeMillis()
        );

        return toView(userMapper.selectById(userId));
    }

    private Map<Long, List<String>> loadRolesBatch(List<Long> userIds) {
        Map<Long, String> roleCodeById = roleMapper.selectList(
                        new LambdaQueryWrapper<Role>().eq(Role::getEnabled, 1))
                .stream()
                .collect(Collectors.toMap(Role::getId, Role::getRoleCode, (a, b) -> a));
        List<UserRole> relations = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds));
        Map<Long, List<String>> rolesByUserId = new HashMap<>();
        for (UserRole rel : relations) {
            String code = roleCodeById.get(rel.getRoleId());
            if (code != null) {
                rolesByUserId.computeIfAbsent(rel.getUserId(), k -> new ArrayList<>()).add(code);
            }
        }
        return rolesByUserId;
    }

    private UserAdminView toView(User user) {
        List<String> roles = roleMapper.selectRolesByUserId(user.getId()).stream()
                .map(Role::getRoleCode)
                .toList();
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, user.getId())
                        .last("LIMIT 1"));
        return toView(user, roles, profile);
    }

    private UserAdminView toView(User user, List<String> roles, CompanionProfile profile) {
        return UserAdminView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .introduction(user.getIntroduction())
                .accountStatus(user.getAccountStatus())
                .disabledReason(user.getDisabledReason())
                .roles(roles)
                .companionCertificationStatus(profile == null ? null : profile.getCertificationStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
