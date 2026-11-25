package com.gameplay.customer_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.customer_service.domain.CustomerConversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客服会话 Mapper。
 */
public interface CustomerConversationMapper extends BaseMapper<CustomerConversation> {

    @Select("SELECT * FROM customer_conversation WHERE id = #{id} FOR UPDATE")
    CustomerConversation selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM customer_conversation "
            + "WHERE current_cs_account_id = #{csAccountId} "
            + "  AND conversation_status = 'HUMAN_PROCESSING' "
            + "ORDER BY id ASC FOR UPDATE")
    List<CustomerConversation> selectActiveHumanProcessingForUpdate(@Param("csAccountId") Long csAccountId);
}
