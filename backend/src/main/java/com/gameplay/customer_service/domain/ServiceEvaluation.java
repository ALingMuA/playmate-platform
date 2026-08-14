package com.gameplay.customer_service.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服满意度评价表实体（对应 `service_evaluation`，FR-C15）。
 * <p>每个已关闭会话最多一条评价（uk_se_conversation）。</p>
 */
@Data
@TableName("service_evaluation")
public class ServiceEvaluation {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 已关闭客服会话ID */
    private Long conversationId;

    /** 评价用户ID */
    private Long evaluatorUserId;

    /** 满意度评分：1至5 */
    private Integer score;

    /** 评价内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
