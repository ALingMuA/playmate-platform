package com.gameplay.wallet.enums;

/**
 * 资金流水类型（wallet_ledger.ledger_type）。
 */
public enum LedgerType {
    /** 支付 */
    PAYMENT,
    /** 退款 */
    REFUND,
    /** 结算 */
    SETTLEMENT,
    /** 调整 */
    ADJUSTMENT
}
