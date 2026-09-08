package com.gameplay.customer_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.customer_service.domain.CustomerConversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 客服会话 Mapper。
 */
public interface CustomerConversationMapper extends BaseMapper<CustomerConversation> {
    @Select("SELECT * FROM customer_conversation WHERE id = #{id} FOR UPDATE")
    CustomerConversation selectForUpdate(@Param("id") Long id);
}
