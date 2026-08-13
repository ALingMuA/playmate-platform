package com.gameplay.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 账号认证状态校验服务。
 *
 * <p>REST 过滤器与 WebSocket 握手（详细设计 7.3）共用：
 * 校验账号存在、启用状态与令牌版本，实现"账号禁用、改密或注销后旧令牌立即失效"。</p>
 */
@Service
@RequiredArgsConstructor
public class AccountAuthService {

    private final UserMapper userMapper;

    /**
     * 校验账号启用状态与令牌版本，返回有效用户。
     *
     * @throws BusinessException AUTH_TOKEN_INVALID（账号不存在或已注销）
     * @throws BusinessException AUTH_ACCOUNT_DISABLED（账号已禁用）
     * @throws BusinessException AUTH_TOKEN_VERSION_MISMATCH（令牌版本落后，登录状态已失效）
     */
    public User assertEnabledAndTokenVersion(Long userId, int tokenVersion) {
        // MyBatis-Plus 逻辑删除自动过滤 deleted=1，注销用户查询不到
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        if (!"ENABLED".equals(user.getAccountStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        if (user.getTokenVersion() == null || user.getTokenVersion() != tokenVersion) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_VERSION_MISMATCH);
        }
        return user;
    }

    /** 按登录账号（用户名/手机号/邮箱）查找用户 */
    public User findByAccount(String account) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or().eq(User::getMobile, account)
                .or().eq(User::getEmail, account)
                .last("LIMIT 1"));
    }
}
