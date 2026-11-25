package com.gameplay.wallet;

import com.gameplay.audit.service.OperationLogService;
import com.gameplay.auth.mapper.UserMapper;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import com.gameplay.order.domain.PlayOrder;
import com.gameplay.wallet.config.DemoWalletProperties;
import com.gameplay.wallet.domain.WalletAccount;
import com.gameplay.wallet.domain.WalletLedger;
import com.gameplay.wallet.mapper.WalletAccountMapper;
import com.gameplay.wallet.mapper.WalletLedgerMapper;
import com.gameplay.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletAccountMapper walletAccountMapper;
    @Mock
    private WalletLedgerMapper walletLedgerMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private DemoWalletProperties demoWalletProperties;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("非正数金额调用资金方法应抛出 VALIDATION_FAILED")
    void nonPositiveAmountRejected() {
        PlayOrder zeroOrder = new PlayOrder();
        zeroOrder.setId(1L);
        zeroOrder.setOrderNo("PO_ZERO");
        zeroOrder.setUserId(10L);
        zeroOrder.setCompanionUserId(20L);
        zeroOrder.setTotalAmountCents(0L);
        zeroOrder.setSettlementAmountCents(0L);

        assertThatThrownBy(() -> walletService.pay(zeroOrder, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        PlayOrder negativeOrder = new PlayOrder();
        negativeOrder.setId(2L);
        negativeOrder.setOrderNo("PO_NEG");
        negativeOrder.setUserId(10L);
        negativeOrder.setCompanionUserId(20L);
        negativeOrder.setTotalAmountCents(-100L);
        negativeOrder.setSettlementAmountCents(-100L);

        assertThatThrownBy(() -> walletService.refund(negativeOrder, "test", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> walletService.settle(negativeOrder, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> walletService.refundAmount(zeroOrder, -50L, 1L, "partial", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> walletService.deductSettledIncome(negativeOrder, "clawback", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("insertLedger 遇到 DuplicateKeyException 必须抛出 WALLET_LEDGER_DUPLICATE 以回滚事务")
    void duplicateLedgerThrowsToRollbackTransaction() {
        PlayOrder order = new PlayOrder();
        order.setId(100L);
        order.setOrderNo("PO202611250001");
        order.setUserId(10L);
        order.setCompanionUserId(20L);
        order.setServiceTitleSnapshot("测试服务");
        order.setTotalAmountCents(5000L);

        WalletAccount wallet = new WalletAccount();
        wallet.setId(1L);
        wallet.setUserId(10L);
        wallet.setBalanceCents(10000L);
        wallet.setFrozenCents(0L);
        wallet.setTotalIncomeCents(0L);
        wallet.setVersion(0);

        when(walletLedgerMapper.selectByBusinessNo("PAY:PO202611250001")).thenReturn(null);
        when(walletAccountMapper.selectByUserIdForUpdate(10L)).thenReturn(wallet);
        when(walletAccountMapper.decreaseBalanceWithVersion(1L, 0, 5000L)).thenReturn(1);
        when(walletLedgerMapper.insert(any(WalletLedger.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> walletService.pay(order, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_LEDGER_DUPLICATE));

        verify(walletLedgerMapper).insert(any(WalletLedger.class));
    }
}
