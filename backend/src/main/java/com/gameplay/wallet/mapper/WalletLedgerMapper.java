package com.gameplay.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.wallet.domain.WalletLedger;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 虚拟资金流水 Mapper（uk_wl_business_no 为幂等防线）。
 */
public interface WalletLedgerMapper extends BaseMapper<WalletLedger> {

    @Select("SELECT * FROM wallet_ledger WHERE business_no = #{businessNo}")
    WalletLedger selectByBusinessNo(@Param("businessNo") String businessNo);
}
