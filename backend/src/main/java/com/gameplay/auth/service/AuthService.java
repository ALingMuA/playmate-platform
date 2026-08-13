package com.gameplay.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gameplay.auth.dto.AuthResponse;
import com.gameplay.auth.dto.ChangePasswordRequest;
import com.gameplay.auth.dto.LoginRequest;
import com.gameplay.auth.dto.RegisterRequest;
import com.gameplay.auth.dto.UserProfileView;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.entity.UserRole;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.auth.mapper.UserRoleMapper;
import com.gameplay.auth.security.JwtTokenService;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号认证应用服务（FR-A01 注册、FR-A02 登录/退出、FR-A04 修改密码、FR-A05 个人资料）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AccountAuthService accountAuthService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 用户注册：创建账号并绑定默认 USER 角色。
     * <p>用户名、手机号、邮箱唯一性先查后插，最终由数据库唯一索引兜底。</p>
     */
    @Transactional
    public UserProfileView register(RegisterRequest req) {
        // 联系方式至少一项（需求 5.1：可使用用户名、手机号或邮箱注册）
        if (!StringUtils.hasText(req.getMobile()) && !StringUtils.hasText(req.getEmail())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "手机号与邮箱至少填写一项");
        }
        // 唯一性校验
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_USERNAME_EXISTS);
        }
        if (StringUtils.hasText(req.getMobile()) && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getMobile, req.getMobile())) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_MOBILE_EXISTS);
        }
        if (StringUtils.hasText(req.getEmail()) && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_EMAIL_EXISTS);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setMobile(req.getMobile() == null ? "" : req.getMobile());
        user.setEmail(req.getEmail() == null ? "" : req.getEmail());
        user.setAvatarUrl("");
        user.setGender(0);
        user.setIntroduction("");
        user.setAccountStatus("ENABLED");
        user.setTokenVersion(1);
        userMapper.insert(user);

        // 默认普通用户角色
        Role userRole = roleMapper.selectByRoleCode("USER");
        if (userRole == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统角色未初始化");
        }
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(userRole.getId());
        userRoleMapper.insert(relation);

        return toProfile(user, List.of("USER"));
    }

    /**
     * 登录：账号支持用户名/手机号/邮箱，校验通过后签发 JWT 并更新最后登录时间。
     */
    public AuthResponse login(LoginRequest req) {
        User user = accountAuthService.findByAccount(req.getAccount());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            // 不区分账号不存在与密码错误，避免账号枚举
            throw new BusinessException(ErrorCode.AUTH_CREDENTIAL_INVALID);
        }
        if (!"ENABLED".equals(user.getAccountStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getLastLoginAt, now)
                .eq(User::getId, user.getId()));
        user.setLastLoginAt(now);

        List<String> roles = loadRoles(user.getId());
        String token = jwtTokenService.createToken(user.getId(), user.getUsername(), roles, user.getTokenVersion());
        return new AuthResponse(token, "Bearer", jwtTokenService.getExpireSeconds(), toProfile(user, roles));
    }

    /**
     * 修改密码（登录后）：校验原密码，更新哈希并递增令牌版本使所有旧令牌失效。
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INCORRECT);
        }
        if (req.getOldPassword().equals(req.getNewPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新密码不能与原密码相同");
        }

        int newVersion = user.getTokenVersion() + 1;
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getPasswordHash, passwordEncoder.encode(req.getNewPassword()))
                .set(User::getTokenVersion, newVersion)
                .eq(User::getId, userId));

        List<String> roles = loadRoles(userId);
        String token = jwtTokenService.createToken(userId, user.getUsername(), roles, newVersion);
        return new AuthResponse(token, "Bearer", jwtTokenService.getExpireSeconds(), toProfile(user, roles));
    }

    /** 当前登录用户信息（FR-A05） */
    public UserProfileView getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return toProfile(user, loadRoles(userId));
    }

    private List<String> loadRoles(Long userId) {
        return roleMapper.selectRolesByUserId(userId).stream()
                .map(Role::getRoleCode)
                .toList();
    }

    private UserProfileView toProfile(User user, List<String> roles) {
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
