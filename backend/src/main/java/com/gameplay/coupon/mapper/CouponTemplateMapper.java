package com.gameplay.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.coupon.domain.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    @Select("SELECT * FROM coupon_template WHERE id = #{id} FOR UPDATE")
    CouponTemplate selectByIdForUpdate(@Param("id") Long id);

    /**
     * 原子递增已领取数量（防超发）。
     *
     * @param id 模板ID
     * @param totalQuantity 总发行量（0表示不限量）
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE coupon_template
            SET claimed_quantity = claimed_quantity + 1
            WHERE id = #{id}
            <if test="totalQuantity != null and totalQuantity &gt; 0">
              AND claimed_quantity &lt; #{totalQuantity}
            </if>
            </script>
            """)
    int increaseClaimedQuantity(@Param("id") Long id, @Param("totalQuantity") Integer totalQuantity);
}
