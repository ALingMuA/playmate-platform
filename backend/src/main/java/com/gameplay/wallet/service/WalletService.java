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

import java.time.LocalDateTime;
import java.util.List;

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
        String businessNo = "PAY:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 已支付过，幂等返回
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getUserId());
        if (wallet.getBalanceCents() < order.getTotalAmountCents()) {
            throw new BusinessException(ErrorCode.WALLET_BALANCE_INSUFFICIENT);
        }
        int rows = walletAccountMapper.decreaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), order.getTotalAmountCents());
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getUserId(), order.getId(), LedgerType.PAYMENT, LedgerDirection.OUT,
                order.getTotalAmountCents(), wallet, "订单支付：" + order.getServiceTitleSnapshot(),
                operatorId);
    }

    /** 退款入账（FR-U12/P14）：退还用户余额，写入 REFUND 流水 */
    @Transactional
    public void refund(PlayOrder order, String reason, Long operatorId) {
        String businessNo = "REFUND:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getUserId());
        int rows = walletAccountMapper.increaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), order.getTotalAmountCents());
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getUserId(), order.getId(), LedgerType.REFUND, LedgerDirection.IN,
                order.getTotalAmountCents(), wallet, "订单退款：" + reason, operatorId);
    }

    /**
     * 订单完成结算（FR-U13/P19）：陪玩师收益入账（可用余额 + 累计收益）。
     * <p>演示环境不提供真实提现，收益直接计入可用余额。</p>
     */
    @Transactional
    public void settle(PlayOrder order, Long operatorId) {
        String businessNo = "SETTLE:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getCompanionUserId());
        long settleAmount = (order.getSettlementAmountCents() != null && order.getSettlementAmountCents() > 0)
                ? order.getSettlementAmountCents() : order.getTotalAmountCents();
        int rows = walletAccountMapper.settleIncomeWithVersion(
                wallet.getId(), wallet.getVersion(), settleAmount);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getCompanionUserId(), order.getId(), LedgerType.SETTLEMENT, LedgerDirection.IN,
                settleAmount, wallet, "订单完成收益结算：" + order.getServiceTitleSnapshot(),
                operatorId);
    }

    /**
     * 部分退款（FR-M18 PARTIAL_REFUND）：退还指定金额给用户，业务号 REFUND:订单号:PARTIAL 幂等。
     */
    @Transactional
    public void refundAmount(PlayOrder order, long amountCents, String reason, Long operatorId) {
        if (amountCents <= 0 || amountCents > order.getTotalAmountCents()) {
            throw new BusinessException(ErrorCode.COMPLAINT_REFUND_AMOUNT_INVALID);
        }
        String businessNo = "REFUND:" + order.getOrderNo() + ":PARTIAL";
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
                amountCents, wallet, "投诉部分退款：" + reason, operatorId);
    }

    /**
     * 扣回已结算收益（FR-M18 FULL_REFUND，订单已结算时）：从陪玩师钱包扣回订单金额。
     * <p>业务号 SETTLE_BACK:订单号 幂等；余额不足时按可用余额扣回并如实记录流水备注。</p>
     */
    @Transactional
    public void deductSettledIncome(PlayOrder order, String reason, Long operatorId) {
        String businessNo = "SETTLE_BACK:" + order.getOrderNo();
        if (walletLedgerMapper.selectByBusinessNo(businessNo) != null) {
            return; // 幂等
        }
        WalletAccount wallet = ensureWalletForUpdate(order.getCompanionUserId());
        long settleAmount = (order.getSettlementAmountCents() != null && order.getSettlementAmountCents() > 0)
                ? order.getSettlementAmountCents() : order.getTotalAmountCents();
        long actual = Math.min(settleAmount, wallet.getBalanceCents());
        if (actual <= 0) {
            return; // 陪玩师无可扣余额，演示环境允许
        }
        int rows = walletAccountMapper.decreaseBalanceWithVersion(
                wallet.getId(), wallet.getVersion(), actual);
        if (rows != 1) {
            throw new BusinessException(ErrorCode.WALLET_CONCURRENT_MODIFICATION);
        }
        insertLedger(businessNo, order.getCompanionUserId(), order.getId(), LedgerType.SETTLEMENT, LedgerDirection.OUT,
                actual, wallet, "投诉全额退款扣回收益：" + reason, operatorId);
    }

    /** 订单是否已完成收益结算（存在 SETTLE 流水） */
    public boolean isSettled(PlayOrder order) {
        return walletLedgerMapper.selectByBusinessNo("SETTLE:" + order.getOrderNo()) != null;
    }

    /** 我的钱包概览 */
    public WalletView view(Long userId) {
        WalletAccount wallet = getOrCreate(userId);
        return WalletView.builder()
                .balanceCents(wallet.getBalanceCents())
                .frozenCents(wallet.getFrozenCents())
                .totalIncomeCents(wallet.getTotalIncomeCents())
                .build();
    }

    /** 我的资金流水分页（FR-P19 收益明细） */
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

    /**
     * 发放演示余额（仅演示环境开启时由管理员调用）。
     * <p>businessNo = DEMO_CREDIT:{requestId} 保证幂等，同 requestId 返回既有记录。</p>
     */
    @Transactional
    public DemoWalletGrantView grantDemoCredit(Long targetUserId, Long amountCents, String reason,
                                              String requestId, Long operatorAdminId) {
        if (!demoWalletProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.DEMO_WALLET_DISABLED);
        }
        if (amountCents == null || !demoWalletProperties.getAllowedAmountsCents().contains(amountCents)) {
            throw new BusinessException(ErrorCode.DEMO_WALLET_AMOUNT_INVALID);
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
            return DemoWalletGrantView.builder()
                    .userId(existing.getUserId())
                    .amountCents(existing.getAmountCents())
                    .balanceAfterCents(existing.getBalanceAfterCents())
                    .businessNo(existing.getBusinessNo())
                    .createdAt(existing.getCreatedAt())
                    .build();
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
        ledger.setRemark(remark);
        ledger.setCreatedBy(operatorId == null ? 0L : operatorId);
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            walletLedgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            // uk_wl_business_no 最终幂等防线：重复流水直接忽略
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
