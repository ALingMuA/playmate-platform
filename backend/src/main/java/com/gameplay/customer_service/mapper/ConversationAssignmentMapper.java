package com.gameplay.customer_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.customer_service.domain.ConversationAssignment;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话分配轨迹 Mapper。
 */
public interface ConversationAssignmentMapper extends BaseMapper<ConversationAssignment> {

    @Data
    class ConversationQueuedAt {
        private Long conversationId;
        private LocalDateTime queuedAt;
    }

    @Select("""
            <script>
            SELECT conversation_id AS conversationId, MAX(created_at) AS queuedAt
            FROM conversation_assignment
            WHERE to_cs_account_id = 0
              AND assignment_type IN ('AI_TRANSFER', 'TRANSFER')
              AND conversation_id IN
              <foreach collection="conversationIds" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            GROUP BY conversation_id
            </script>
            """)
    List<ConversationQueuedAt> selectLatestQueueTimes(@Param("conversationIds") List<Long> conversationIds);
}
