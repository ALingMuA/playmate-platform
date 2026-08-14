package com.gameplay.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.admin.dto.UserAdminView;
import com.gameplay.admin.dto.UserStatusUpdateRequest;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.companion.domain.CompanionProfile;
import com.gameplay.companion.mapper.CompanionProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端用户管理服务（FR-M03 查询、FR-M04 启用/禁用）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final CompanionProfileMapper companionProfileMapper;

    /** 用户分页查询（FR-M03）：按账号/昵称/手机号关键词与状态过滤 */
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
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    /** 启用/禁用用户（FR-M04）：禁用递增令牌版本，旧令牌立即失效 */
    @Transactional
    public UserAdminView setStatus(Long userId, UserStatusUpdateRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!"ENABLED".equals(req.getAccountStatus()) && !"DISABLED".equals(req.getAccountStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账号状态不合法");
        }
        if ("DISABLED".equals(req.getAccountStatus()) && !StringUtils.hasText(req.getReason())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "禁用用户必须填写原因");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getAccountStatus, req.getAccountStatus())
                .eq(User::getId, userId));
        // 禁用：递增令牌版本
        if ("DISABLED".equals(req.getAccountStatus())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .setSql("token_version = token_version + 1")
                    .eq(User::getId, userId));
        }
        return toView(userMapper.selectById(userId));
    }

    private UserAdminView toView(User user) {
        List<String> roles = roleMapper.selectRolesByUserId(user.getId()).stream()
                .map(Role::getRoleCode)
                .toList();
        CompanionProfile profile = companionProfileMapper.selectOne(
                new LambdaQueryWrapper<CompanionProfile>()
                        .eq(CompanionProfile::getUserId, user.getId())
                        .last("LIMIT 1"));
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
                .roles(roles)
                .companionCertificationStatus(profile == null ? null : profile.getCertificationStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}