package com.gameplay.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.coupon.domain.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Select("SELECT * FROM user_coupon WHERE id = #{id} FOR UPDATE")
    UserCoupon selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM user_coupon WHERE locked_order_id = #{orderId} FOR UPDATE")
    UserCoupon selectByLockedOrderIdForUpdate(@Param("orderId") Long orderId);

    @org.apache.ibatis.annotations.Update("UPDATE user_coupon SET status = #{status}, locked_order_id = NULL, locked_at = NULL WHERE id = #{id}")
    int releaseLock(@Param("id") Long id, @Param("status") String status);
}
