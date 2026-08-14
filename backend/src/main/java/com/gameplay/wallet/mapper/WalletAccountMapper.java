package com.gameplay.wallet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.wallet.domain.WalletAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 虚拟钱包账户 Mapper。
 */
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {

    /** 行锁读取钱包（资金操作事务入口） */
    @Select("SELECT * FROM wallet_account WHERE user_id = #{userId} FOR UPDATE")
    WalletAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    /** 乐观锁扣减可用余额（余额不足返回0） */
    @Update("""
            UPDATE wallet_account
            SET balance_cents = balance_cents - #{amountCents},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id} AND version = #{version} AND balance_cents >= #{amountCents}
            """)
    int decreaseBalanceWithVersion(@Param("id") Long id,
                                   @Param("version") Integer version,
                                   @Param("amountCents") Long amountCents);

    /** 乐观锁增加可用余额 */
    @Update("""
            UPDATE wallet_account
            SET balance_cents = balance_cents + #{amountCents},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id} AND version = #{version}
            """)
    int increaseBalanceWithVersion(@Param("id") Long id,
                                   @Param("version") Integer version,
                                   @Param("amountCents") Long amountCents);

    /** 结算入账：可用余额与累计收益同时增加 */
    @Update("""
            UPDATE wallet_account
            SET balance_cents = balance_cents + #{amountCents},
                total_income_cents = total_income_cents + #{amountCents},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{id} AND version = #{version}
            """)
    int settleIncomeWithVersion(@Param("id") Long id,
                                @Param("version") Integer version,
                                @Param("amountCents") Long amountCents);
}
