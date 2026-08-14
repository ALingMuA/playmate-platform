package com.gameplay.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.review.domain.Complaint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单投诉 Mapper。
 */
public interface ComplaintMapper extends BaseMapper<Complaint> {

    /** 行锁读取投诉（仲裁处理入口） */
    @Select("SELECT * FROM complaint WHERE id = #{id} FOR UPDATE")
    Complaint selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

    /** 某订单是否存在进行中（未处理完）的投诉，供发起投诉时校验（FR-U17） */
    @Select("""
            SELECT COUNT(*) FROM complaint
            WHERE order_id = #{orderId}
              AND complaint_status IN ('PENDING', 'PROCESSING')
            """)
    long countActiveByOrder(@Param("orderId") Long orderId);
}
