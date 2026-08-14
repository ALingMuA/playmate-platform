package com.gameplay.customer_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.entity.UserRole;
import com.gameplay.auth.event.PasswordChangedEvent;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.auth.mapper.UserRoleMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.customer_service.domain.CustomerServiceAccount;
import com.gameplay.customer_service.dto.CsAccountCreateRequest;
import com.gameplay.customer_service.dto.CsAccountResetPasswordRequest;
import com.gameplay.customer_service.dto.CsAccountStatusUpdateRequest;
import com.gameplay.customer_service.dto.CsAccountView;
import com.gameplay.customer_service.dto.CsWorkStatusUpdateRequest;
import com.gameplay.customer_service.enums.CsAccountStatus;
import com.gameplay.customer_service.enums.CsWorkStatus;
import com.gameplay.customer_service.mapper.CustomerServiceAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 客服账号应用服务（FR-C01~C06）。
 *
 * <p>客服账号由管理员在后台创建，关联内部用户账号并绑定 CUSTOMER_SERVICE 角色；
 * 登录口令、令牌版本与禁用机制复用用户体系，禁用/重置密码即递增 token_version 使旧令牌失效。</p>
 */
@Service
@RequiredArgsConstructor
public class CsAccountService {

    /** 默认最大并行处理会话数 */
    public static final int DEFAULT_MAX_ACTIVE_CONVERSATIONS = 5;

    private final CustomerServiceAccountMapper csAccountMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /** 创建客服账号（FR-C01）：创建内部账号 + 绑定角色 + 客服档案，强制首次改密 */
    @Transactional
    public CsAccountView create(Long adminId, CsAccountCreateRequest req) {
        // 客服账号名唯一
        if (csAccountMapper.selectCount(new LambdaQueryWrapper<CustomerServiceAccount>()
                .eq(CustomerServiceAccount::getCsAccount, req.getCsAccount())) > 0) {
            throw new BusinessException(ErrorCode.CS_ACCOUNT_EXISTS);
        }
        // 关联内部用户账号（复用登录体系）
        User user = new User();
        user.setUsername(req.getCsAccount());
        user.setPasswordHash(passwordEncoder.encode(req.getInitialPassword()));
        user.setNickname(req.getCsName());
        user.setAvatarUrl("");
        user.setGender(0);
        user.setIntroduction("客服人员");
        user.setAccountStatus(CsAccountStatus.ENABLED.name());
        user.setTokenVersion(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.ACCOUNT_USERNAME_EXISTS);
        }
        Role csRole = roleMapper.selectByRoleCode("CUSTOMER_SERVICE");
        if (csRole == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统角色未初始化");
        }
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(csRole.getId());
        userRoleMapper.insert(relation);

        CustomerServiceAccount account = new CustomerServiceAccount();
        account.setUserId(user.getId());
        account.setCsAccount(req.getCsAccount());
        account.setCsName(req.getCsName());
        account.setContactMobile(req.getContactMobile());
        account.setAccountStatus(CsAccountStatus.ENABLED.name());
        account.setWorkStatus(CsWorkStatus.OFFLINE.name());
        account.setForceChangePassword(1);
        account.setMaxActiveConversations(req.getMaxActiveConversations() == null
                ? DEFAULT_MAX_ACTIVE_CONVERSATIONS : req.getMaxActiveConversations());
        account.setDisabledReason("");
        account.setCreatedBy(adminId);
        csAccountMapper.insert(account);
        return toView(account);
    }

    /** 客服账号分页查询（FR-C02） */
    public Page<CsAccountView> list(String csAccount, String csName, String accountStatus,
                                    long page, long size) {
        Page<CustomerServiceAccount> p = csAccountMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CustomerServiceAccount>()
                        .like(StringUtils.hasText(csAccount), CustomerServiceAccount::getCsAccount, csAccount)
                        .like(StringUtils.hasText(csName), CustomerServiceAccount::getCsName, csName)
                        .eq(StringUtils.hasText(accountStatus), CustomerServiceAccount::getAccountStatus, accountStatus)
                        .orderByDesc(CustomerServiceAccount::getId));
        Page<CsAccountView> vp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vp.setRecords(p.getRecords().stream().map(this::toView).toList());
        return vp;
    }

    /** 启用/禁用客服账号（FR-C03）：禁用必须填写原因，旧令牌立即失效 */
    @Transactional
    public CsAccountView setStatus(Long adminId, Long accountId, CsAccountStatusUpdateRequest req) {
        CustomerServiceAccount account = requireAccount(accountId);
        CsAccountStatus target;
        try {
            target = CsAccountStatus.valueOf(req.getAccountStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "账号状态不合法");
        }
        if (target == CsAccountStatus.DISABLED && !StringUtils.hasText(req.getReason())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "禁用客服账号必须填写原因");
        }
        if (account.getAccountStatus().equals(target.name())) {
            throw new BusinessException(ErrorCode.CS_ACCOUNT_STATUS_INVALID, "账号已是目标状态");
        }
        account.setAccountStatus(target.name());
        account.setDisabledReason(target == CsAccountStatus.DISABLED ? req.getReason() : "");
        csAccountMapper.updateById(account);
        // 禁用：递增令牌版本，立即终止所有登录会话
        if (target == CsAccountStatus.DISABLED) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .setSql("token_version = token_version + 1")
                    .eq(User::getId, account.getUserId()));
        }
        return toView(account);
    }

    /** 重置客服密码（FR-C04）：原会话失效，下次登录必须修改密码 */
    @Transactional
    public CsAccountView resetPassword(Long adminId, Long accountId, CsAccountResetPasswordRequest req) {
        CustomerServiceAccount account = requireAccount(accountId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getPasswordHash, passwordEncoder.encode(req.getNewPassword()))
                .setSql("token_version = token_version + 1")
                .eq(User::getId, account.getUserId()));
        account.setForceChangePassword(1);
        csAccountMapper.updateById(account);
        return toView(account);
    }

    /**
     * 监听密码变更事件（FR-C04）：客服完成改密后清除"强制改密"标志。
     * <p>auth 模块不依赖客服模块，通过事件解耦；非客服用户无对应账号记录，静默忽略。</p>
     */
    @EventListener
    @Transactional
    public void onPasswordChanged(PasswordChangedEvent event) {
        CustomerServiceAccount account = getByUserId(event.userId());
        if (account != null) {
            account.setForceChangePassword(0);
            csAccountMapper.updateById(account);
        }
    }

    /** 客服设置工作状态（FR-C06）：仅启用且非强制改密的客服可上线 */
    @Transactional
    public CsAccountView setWorkStatus(Long csUserId, CsWorkStatusUpdateRequest req) {
        CustomerServiceAccount account = requireAccountByUserId(csUserId);
        CsWorkStatus target;
        try {
            target = CsWorkStatus.valueOf(req.getWorkStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工作状态不合法");
        }
        if (target == CsWorkStatus.ONLINE) {
            assertCsEnabled(account, "客服未启用或未完成首次改密，不能上线");
        }
        account.setWorkStatus(target.name());
        csAccountMapper.updateById(account);
        return toView(account);
    }

    /** 当前客服账号视图（按内部用户ID，工作台"我的"） */
    public CsAccountView getViewByUserId(Long csUserId) {
        return toView(requireAccountByUserId(csUserId));
    }

    /** 当前客服账号（按内部用户ID） */
    public CustomerServiceAccount getByUserId(Long csUserId) {
        return csAccountMapper.selectOne(new LambdaQueryWrapper<CustomerServiceAccount>()
                .eq(CustomerServiceAccount::getUserId, csUserId));
    }

    /** 校验客服可接待：账号启用、已改密、在线（FR-C06/CS_AGENT_NOT_ONLINE） */
    public CustomerServiceAccount assertAvailable(Long csUserId) {
        CustomerServiceAccount account = requireAccountByUserId(csUserId);
        assertCsEnabled(account, null);
        if (!CsWorkStatus.ONLINE.name().equals(account.getWorkStatus())) {
            throw new BusinessException(ErrorCode.CS_AGENT_NOT_ONLINE, "客服当前不在线");
        }
        return account;
    }

    /** 校验账号可用（不含在线状态）：启用且已完成强制改密 */
    private void assertCsEnabled(CustomerServiceAccount account, String message) {
        if (!CsAccountStatus.ENABLED.name().equals(account.getAccountStatus())) {
            throw new BusinessException(ErrorCode.CS_AGENT_NOT_ONLINE,
                    message != null ? message : "客服账号已被禁用");
        }
        if (account.getForceChangePassword() != null && account.getForceChangePassword() == 1) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
                    message != null ? message : "首次登录或重置后必须修改密码");
        }
    }

    /** 校验客服是会话当前处理人 */
    public CustomerServiceAccount assertCurrentHandler(Long csUserId, Long currentCsAccountId) {
        CustomerServiceAccount account = requireAccountByUserId(csUserId);
        if (currentCsAccountId == null || !currentCsAccountId.equals(account.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DATA_SCOPE_DENIED, "会话未分配给当前客服");
        }
        return account;
    }



    /** 当前处理中会话数（容量校验用） */
    public long countActiveConversations(Long csAccountId) {
        return 0L; // 由会话服务统计，此处保留扩展点
    }

    private CustomerServiceAccount requireAccount(Long accountId) {
        CustomerServiceAccount account = csAccountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.CS_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private CustomerServiceAccount requireAccountByUserId(Long csUserId) {
        CustomerServiceAccount account = getByUserId(csUserId);
        if (account == null) {
            throw new BusinessException(ErrorCode.CS_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private CsAccountView toView(CustomerServiceAccount account) {
        return CsAccountView.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .csAccount(account.getCsAccount())
                .csName(account.getCsName())
                .contactMobile(account.getContactMobile())
                .accountStatus(account.getAccountStatus())
                .workStatus(account.getWorkStatus())
                .forceChangePassword(account.getForceChangePassword())
                .maxActiveConversations(account.getMaxActiveConversations())
                .disabledReason(account.getDisabledReason())
                .createdBy(account.getCreatedBy())
                .createdAt(account.getCreatedAt())
                .build();
    }
}