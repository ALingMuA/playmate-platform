package com.gameplay.customer_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.customer_service.domain.CustomerServiceAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客服账号 Mapper。
 */
public interface CustomerServiceAccountMapper extends BaseMapper<CustomerServiceAccount> {

    @Select("SELECT * FROM customer_service_account WHERE id = #{id} FOR UPDATE")
    CustomerServiceAccount selectForUpdateById(@Param("id") Long id);

    @Select("SELECT * FROM customer_service_account WHERE user_id = #{userId} FOR UPDATE")
    CustomerServiceAccount selectForUpdateByUserId(@Param("userId") Long userId);
}
