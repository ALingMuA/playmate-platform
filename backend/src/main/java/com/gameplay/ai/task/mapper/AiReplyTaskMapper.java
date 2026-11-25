package com.gameplay.ai.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gameplay.ai.task.domain.AiReplyTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AiReplyTaskMapper extends BaseMapper<AiReplyTask> {
    @Select("SELECT * FROM ai_reply_task WHERE id = #{id} FOR UPDATE")
    AiReplyTask selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM ai_reply_task WHERE status = 'PENDING' ORDER BY id ASC LIMIT #{limit}")
    List<AiReplyTask> selectPending(@Param("limit") int limit);

    @Select("SELECT * FROM ai_reply_task "
            + "WHERE (status = 'RUNNING' AND started_at IS NOT NULL AND started_at < #{runningBefore}) "
            + "   OR (status = 'PENDING' AND created_at < #{pendingBefore}) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<AiReplyTask> selectExpiredCandidates(@Param("runningBefore") LocalDateTime runningBefore,
                                              @Param("pendingBefore") LocalDateTime pendingBefore,
                                              @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ai_reply_task t JOIN customer_conversation c "
            + "ON c.id = t.conversation_id WHERE c.initiator_user_id = #{userId} "
            + "AND t.created_at >= #{since}")
    long countRecentForUser(@Param("userId") Long userId,
                            @Param("since") LocalDateTime since);
}
