package com.gameplay.wallet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplay.audit.service.OperationLogService;
import com.gameplay.auth.entity.User;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.wallet.config.DemoWalletProperties;
import com.gameplay.wallet.domain.WalletAccount;
import com.gameplay.wallet.domain.WalletLedger;
import com.gameplay.wallet.dto.DemoWalletConfigView;
import com.gameplay.wallet.dto.DemoWalletGrantView;
import com.gameplay.wallet.dto.LedgerView;
import com.gameplay.wallet.dto.WalletView;
import com.gameplay.wallet.enums.LedgerDirection;
import com.gameplay.wallet.enums.LedgerType;
import com.gameplay.wallet.mapper.WalletAccountMapper;
import com.gameplay.wallet.mapper.WalletLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 虚拟钱包应用服务：支付扣款、退款、收益结算与流水分页查询（详细设计 3.2）。
 *
 * <p>所有资金变动均以业务流水号（uk_wl_business_no）保证幂等，
 * 钱包余额使用乐观锁条件更新，禁止直接修改 wallet_account。</p>
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletAccountMapper walletAccountMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final DemoWalletProperties demoWalletProperties;
    private final TransactionTemplate transactionTemplate;

    /** 行锁读取钱包；不存在时创建（资金操作事务入口） */
    private WalletAccount ensureWalletForUpdate(Long userId) {
        WalletAccount wallet = walletAccountMapper.selectByUserIdForUpdate(userId);
        if (wallet != null) {
            return wallet;
        }
        wallet = new WalletAccount();
        wallet.setUserId(userId);
        wallet.setBalanceCents(0L);
        wallet.setFrozenCents(0L);
        wallet.setTotalIncomeCents(0L);
        wallet.setVersion(0);
        try {
            walletAccountMapper.insert(wallet);
        } catch (DuplicateKeyException e) {
            // 并发创建：另一事务已插入，重新锁定读取
            return walletAccountMapper.selectByUserIdForUpdate(userId);
        }
        return wallet;
    }

    /** 获取（必要时创建）用户钱包账户 */
    @Transactional
    public WalletAccount getOrCreate(Long userId) {
        WalletAccount account = walletAccountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, userId));
        if (account != null) {
            return account;
        }
        account = new WalletAccount();
        account.setUserId(userId);
        account.setBalanceCents(0L);
        account.setFrozenCents(0L);
        account.setTotalIncomeCents(0L);
        account.setVersion(0);
        try {
            walletAccountMapper.insert(account); // uk_wa_user 兜底并发
        } catch (DuplicateKeyException e) {
            return walletAccountMapper.selectOne(
                    new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, userId));
        }
        return account;
    }

    /**
     * 模拟支付扣款（FR-U09）：校验余额并扣减，写入 PAYMENT 流水。
     * <p>businessNo = PAY:订单号 保证同订单不重复扣款（返回既有流水结果）。</p>
     */
    @Transactional
    public void pay(PlayOrder order, Long operatorId) {
        long amountCents = requirePositiveAmount(order == null ? null : order.getTotalAmountCents());
        String businessNo = "PAY:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 已支付过，幂等返回
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getUserId());
        if (wallet.getBalanceCents() < amountCents) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_INSUFFICIENT);
        }
        int rows = walletAccountMapper.decreaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), amountCents);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getUserId(), order.getId(), LedgerType.PAYMENT, LedgerDirection.OUT,
                amountCents, wallet, "订单支付：" + safeStr(order.getServiceTitleSnapshot()),
                operatorId);
    }

    /** 退款入账（FR-U12/P14）：退还用户余额，写入 REFUND 流水 */
    @Transactional
    public void refund(PlayOrder order, String reason, Long operatorId) {
        long amountCents = requirePositiveAmount(order == null ? null : order.getTotalAmountCents());
        String businessNo = "REFUND:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getUserId());
        int rows = walletAccountMapper.increaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), amountCents);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getUserId(), order.getId(), LedgerType.REFUND, LedgerDirection.IN,
                amountCents, wallet, "订单退款：" + safeStr(reason), operatorId);
    }

    /**
     * 订单完成结算（FR-U13/P19）：陪玩师收益入账（可用余额 + 累计收益）。
     * <p>演示环境不提供真实提现，收益直接计入可用余额。</p>
     */
    @Transactional
    public void settle(PlayOrder order, Long operatorId) {
        long settleAmount = (order != null && order.getSettlementAmountCents() != null && order.getSettlementAmountCents() > 0)
                ? order.getSettlementAmountCents()
                : (order == null ? 0L : safeLong(order.getTotalAmountCents()));
        settleAmount(order, settleAmount, operatorId);
    }

    /**
     * 按指定金额结算陪玩师收益（用于正常完成结算与部分退款后的净额结算）。
     * <p>businessNo = SETTLE:订单号 保证同订单仅结算一次。</p>
     */
    @Transactional
    public void settleAmount(PlayOrder order, long amountCents, Long operatorId) {
        requirePositiveAmount(amountCents);
        String businessNo = "SETTLE:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getCompanionUserId());
        int rows = walletAccountMapper.settleIncomeWithVersion(
                wallet.getId(), wallet.getVersion(), amountCents);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getCompanionUserId(), order.getId(), LedgerType.SETTLEMENT, LedgerDirection.IN,
                amountCents, wallet, "订单完成收益结算：" + safeStr(order.getServiceTitleSnapshot()),
                operatorId);
    }

    /**
     * 部分退款（FR-M18 PARTIAL_REFUND）：退还指定金额给用户，业务号按投诉ID幂等。
     */
    @Transactional
    public void refundAmount(PlayOrder order, long amountCents, Long complaintId, String reason, Long operatorId) {
        requirePositiveAmount(amountCents);
        if (order.getTotalAmountCents() == null || amountCents > order.getTotalAmountCents()) {
            throw new BusinessException(ErrorCode.COMPLAINT_REFUND_AMOUNT_INVALID);
        }
        String suffix = complaintId == null ? "" : (":" + complaintId);
        String businessNo = "REFUND:" + order.getOrderNo() + ":PARTIAL" + suffix;
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getUserId());
        int rows = walletAccountMapper.increaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), amountCents);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getUserId(), order.getId(), LedgerType.REFUND, LedgerDirection.IN,
                amountCents, wallet, "投诉部分退款：" + safeStr(reason), operatorId);
    }

    /**
     * 扣回已结算收益（FR-M18 FULL_REFUND，订单已结算时）：从陪玩师钱包扣回订单结算金额。
     * <p>业务号 SETTLE_BACK:订单号 幂等；同时按实际扣回额扣减累计收益并始终记录流水（含缺口说明）。</p>
     */
    @Transactional
    public void deductSettledIncome(PlayOrder order, String reason, Long operatorId) {
        long settleAmount = (order != null && order.getSettlementAmountCents() != null && order.getSettlementAmountCents() > 0)
                ? order.getSettlementAmountCents()
                : (order == null ? 0L : safeLong(order.getTotalAmountCents()));
        deductSettledIncomeInternal(order, settleAmount, "SETTLE_BACK:" + order.getOrderNo(),
                "投诉全额退款扣回收益", reason, operatorId);
    }

    /**
     * 扣回部分已结算收益（FR-M18 PARTIAL_REFUND，订单已结算时）。
     */
    @Transactional
    public void deductSettledIncomeAmount(PlayOrder order, long amountCents, Long complaintId, String reason, Long operatorId) {
        String suffix = complaintId == null ? "" : (":" + complaintId);
        deductSettledIncomeInternal(order, amountCents,
                "SETTLE_BACK:" + order.getOrderNo() + ":PARTIAL" + suffix,
                "投诉部分退款扣回收益", reason, operatorId);
    }

    private void deductSettledIncomeInternal(PlayOrder order, long clawbackAmount, String businessNo,
                                             String prefix, String reason, Long operatorId) {
        requirePositiveAmount(clawbackAmount);
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getCompanionUserId());
        long actual = Math.min(clawbackAmount, Math.max(0L, wallet.getBalanceCents()));
        long shortfall = clawbackAmount - actual;
        int rows = walletAccountMapper.clawbackSettledIncomeWithVersion(
                wallet.getId(), wallet.getVersion(), actual, actual);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        String safeReason = safeStr(reason);
        String remark = shortfall > 0
                ? String.format("%s（应扣%d分，实扣%d分，缺口%d分）：%s", prefix, clawbackAmount, actual, shortfall, safeReason)
                : prefix + "：" + safeReason;
        insertLedger(businessNo, order.getCompanionUserId(), order.getId(), LedgerType.SETTLEMENT, LedgerDirection.OUT,
                actual, wallet, remark, operatorId);
    }

    /** 订单是否已完成收益结算（存在 SETTLE 流水） */
    public boolean isSettled(PlayOrder order) {
        return walletLedgerMapper.selectByBusinessNo("SETTLE:" + order.getOrderNo()) != null;
    }

    /** 我的钱包概览 */
    @Transactional
    public WalletView view(Long userId) {
        WalletAccount wallet = getOrCreate(userId);
        return WalletView.builder()
                .balanceCents(wallet.getBalanceCents())
                .frozenCents(wallet.getFrozenCents())
                .totalIncomeCents(wallet.getTotalIncomeCents())
                .build();
    }

    /** 我的资金流水分页（FR-P19 收益明细） */
    @Transactional
    public Page<LedgerView> ledgers(Long userId, long page, long size) {
        getOrCreate(userId);
        Page<WalletLedger> p = walletLedgerMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<WalletLedger>()
                        .eq(WalletLedger::getUserId, userId)
                        .orderByDesc(WalletLedger::getId));
        Page<LedgerView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /** 获取演示钱包配置 */
    public DemoWalletConfigView getDemoConfig() {
        return DemoWalletConfigView.builder()
                .enabled(demoWalletProperties.isEnabled())
                .allowedAmountsCents(demoWalletProperties.getAllowedAmountsCents())
                .build();
    }

    /** 管理端只读演示余额查询；没有钱包记录时按零余额返回且不创建账户。 */
    public WalletView demoBalanceView(Long userId) {
        WalletAccount wallet = walletAccountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, userId));
        return WalletView.builder()
                .balanceCents(wallet == null ? 0L : wallet.getBalanceCents())
                .frozenCents(wallet == null ? 0L : wallet.getFrozenCents())
                .totalIncomeCents(wallet == null ? 0L : wallet.getTotalIncomeCents())
                .build();
    }

    /**
     * 发放演示余额（仅演示环境开启时由管理员调用）。
     * <p>businessNo = DEMO_CREDIT:{requestId} 保证幂等，同 requestId 返回既有记录；
     * 并发重复提交触发流水唯一键回滚后重试读取既有记录。</p>
     */
    public DemoWalletGrantView grantDemoCredit(Long targetUserId, Long amountCents, String reason,
                                               String requestId, Long operatorAdminId) {
        if (!demoWalletProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.DEMO_WALLET_DISABLED);
        }
        if (amountCents == null || !demoWalletProperties.getAllowedAmountsCents().contains(amountCents)) {
            throw new BusinessException(ErrorCode.DEMO_WALLET_AMOUNT_INVALID);
        }
        if (requestId == null || requestId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "requestId不能为空");
        }
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null || (targetUser.getDeleted() != null && targetUser.getDeleted() == 1)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if ("DISABLED".equalsIgnoreCase(targetUser.getAccountStatus())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        String businessNo = "DEMO_CREDIT:" + requestId.trim();
        WalletLedger existing = walletLedgerMapper.selectByBusinessNo(businessNo);
        if (existing != null) {
            return verifyAndToGrantView(existing, targetUserId, amountCents);
        }

        try {
            return transactionTemplate.execute(status -> {
                WalletLedger insideExisting = walletLedgerMapper.selectByBusinessNo(businessNo);
                if (insideExisting != null) {
                    return verifyAndToGrantView(insideExisting, targetUserId, amountCents);
                }

                WalletAccount wallet = ensureWalletForUpdate(targetUserId);
                int rows = walletAccountMapper.increaseBalanceWithVersion(
                        wallet.getId(), wallet.getVersion(), amountCents);
                if (rows != 1) {
                    throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
                }

                String cleanReason = (reason == null ? "" : reason.trim());
                insertLedger(businessNo, targetUserId, 0L, LedgerType.DEMO_CREDIT, LedgerDirection.IN,
                        amountCents, wallet, "演示余额发放：" + cleanReason, operatorAdminId);

                operationLogService.record(
                        operatorAdminId,
                        "ADMIN",
                        "DEMO_WALLET_GRANT",
                        "USER",
                        targetUserId,
                        "balance=" + wallet.getBalanceCents(),
                        "balance=" + (wallet.getBalanceCents() + amountCents),
                        "发放演示余额 " + (amountCents / 100) + " 元，原因：" + cleanReason
                );

                return DemoWalletGrantView.builder()
                        .userId(targetUserId)
                        .amountCents(amountCents)
                        .balanceAfterCents(wallet.getBalanceCents() + amountCents)
                        .businessNo(businessNo)
                        .createdAt(LocalDateTime.now())
                        .build();
            });
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.WALLET_LEDGER_DUPLICATE) {
                WalletLedger winner = walletLedgerMapper.selectByBusinessNo(businessNo);
                if (winner != null) {
                    return verifyAndToGrantView(winner, targetUserId, amountCents);
                }
            }
            throw ex;
        }
    }

    private DemoWalletGrantView verifyAndToGrantView(WalletLedger existing, Long targetUserId, Long amountCents) {
        if (!existing.getUserId().equals(targetUserId) || !existing.getAmountCents().equals(amountCents)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return DemoWalletGrantView.builder()
                .userId(existing.getUserId())
                .amountCents(existing.getAmountCents())
                .balanceAfterCents(existing.getBalanceAfterCents())
                .businessNo(existing.getBusinessNo())
                .createdAt(existing.getCreatedAt())
                .build();
    }

    private long requirePositiveAmount(Long amountCents) {
        if (amountCents == null || amountCents <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "资金变动金额必须大于0");
        }
        return amountCents;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String safeStr(String value) {
        return value == null ? "" : value;
    }

    private void insertLedger(String businessNo, Long userId, Long orderId, LedgerType type,
                              LedgerDirection direction, Long amountCents, WalletAccount wallet,
                              String remark, Long operatorId) {
        WalletLedger ledger = new WalletLedger();
        ledger.setBusinessNo(businessNo);
        ledger.setUserId(userId);
        ledger.setOrderId(orderId);
        ledger.setLedgerType(type.name());
        ledger.setDirection(direction.name());
        ledger.setAmountCents(amountCents);
        ledger.setBalanceBeforeCents(wallet.getBalanceCents());
        ledger.setBalanceAfterCents(direction == LedgerDirection.IN
                ? wallet.getBalanceCents() + amountCents
                : wallet.getBalanceCents() - amountCents);
        ledger.setFrozenBeforeCents(wallet.getFrozenCents());
        ledger.setFrozenAfterCents(wallet.getFrozenCents());
        String safeRemark = remark == null ? "" : (remark.length() > 500 ? remark.substring(0, 500) : remark);
        ledger.setRemark(safeRemark);
        ledger.setCreatedBy(operatorId == null ? 0L : operatorId);
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            walletLedgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            // 禁止吞掉唯一键异常：抛出业务异常触发 @Transactional 整体回滚，防止余额重复变动
            throw new BusinessException(ErrorCode.WALLET_LEDGER_DUPLICATE);
        }
    }

    private LedgerView toView(WalletLedger l) {
        return LedgerView.builder()
                .businessNo(l.getBusinessNo())
                .orderId(l.getOrderId())
                .ledgerType(l.getLedgerType())
                .direction(l.getDirection())
                .amountCents(l.getAmountCents())
                .balanceAfterCents(l.getBalanceAfterCents())
                .frozenAfterCents(l.getFrozenAfterCents())
                .remark(l.getRemark())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
