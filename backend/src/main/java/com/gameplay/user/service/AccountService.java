package com.gameplay.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.auth.dto.UserProfileView;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.user.domain.Gender;
import com.gameplay.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 账号资料应用服务（FR-A05 个人资料维护、FR-A06 主动注销会话）。
 *
 * <p>与 auth 模块同属"认证与账号"功能模块（概要设计 4.1），
 * 复用 auth 的 {@code user} 表实体与 Mapper，不重复定义持久层。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /** 查询当前用户个人资料（FR-A05、FR-A06 最近登录时间） */
    public UserProfileView getProfile(Long userId) {
        return toProfile(requireUser(userId));
    }

    /**
     * 更新个人资料（FR-A05）：头像、昵称、性别、简介。
     *
     * <p>为 null 的字段不更新；avatarUrl、introduction 允许清空；昵称不允许空白。
     * 全部字段为 null 时不做任何修改，直接返回当前资料。</p>
     */
    @Transactional
    public UserProfileView updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean changed = false;

        if (request.getNickname() != null) {
            if (!StringUtils.hasText(request.getNickname())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "昵称不能为空白");
            }
            wrapper.set(User::getNickname, request.getNickname().trim());
            user.setNickname(request.getNickname().trim());
            changed = true;
        }
        if (request.getAvatarUrl() != null) {
            wrapper.set(User::getAvatarUrl, request.getAvatarUrl());
            user.setAvatarUrl(request.getAvatarUrl());
            changed = true;
        }
        if (request.getGender() != null) {
            if (!Gender.isValid(request.getGender())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "性别取值不合法（0未知，1男，2女）");
            }
            wrapper.set(User::getGender, request.getGender());
            user.setGender(request.getGender());
            changed = true;
        }
        if (request.getIntroduction() != null) {
            wrapper.set(User::getIntroduction, request.getIntroduction());
            user.setIntroduction(request.getIntroduction());
            changed = true;
        }

        if (changed) {
            userMapper.update(null, wrapper);
        }
        return toProfile(user);
    }

    /**
     * 主动注销全部会话（FR-A06）：递增令牌版本，使所有已签发 JWT 立即失效。
     * <p>客户端应丢弃本地令牌；如需继续使用请重新登录。</p>
     */
    @Transactional
    public void logoutAll(Long userId) {
        User user = requireUser(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getTokenVersion, user.getTokenVersion() + 1)
                .eq(User::getId, userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return user;
    }

    private UserProfileView toProfile(User user) {
        List<String> roles = roleMapper.selectRolesByUserId(user.getId()).stream()
                .map(Role::getRoleCode)
                .toList();
        return UserProfileView.builder()
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
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
