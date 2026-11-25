package com.gameplay.customer_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.customer_service.domain.CustomerServiceMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客服消息 Mapper。
 */
public interface CustomerServiceMessageMapper extends BaseMapper<CustomerServiceMessage> {

    @Select("""
            <script>
            SELECT m.* FROM customer_service_message m
            INNER JOIN (
                SELECT conversation_id, MAX(id) AS max_id
                FROM customer_service_message
                WHERE sender_type = 'USER'
                  AND conversation_id IN
                  <foreach collection="conversationIds" item="id" open="(" separator="," close=")">
                      #{id}
                  </foreach>
                GROUP BY conversation_id
            ) latest ON m.id = latest.max_id
            </script>
            """)
    List<CustomerServiceMessage> selectLatestUserMessages(@Param("conversationIds") List<Long> conversationIds);
}
