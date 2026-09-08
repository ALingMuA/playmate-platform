package com.gameplay.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.ai.task.domain.AiReplyTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiReplyTaskMapper extends BaseMapper<AiReplyTask> {
    @Select("SELECT * FROM ai_reply_task WHERE id = #{id} FOR UPDATE")
    AiReplyTask selectForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM ai_reply_task t JOIN customer_conversation c "
            + "ON c.id = t.conversation_id WHERE c.initiator_user_id = #{userId} "
            + "AND t.created_at >= #{since}")
    long countRecentForUser(@Param("userId") Long userId,
                            @Param("since") java.time.LocalDateTime since);
}
