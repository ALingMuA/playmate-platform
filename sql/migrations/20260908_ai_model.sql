-- 已有数据库升级：先备份，再在 game_companion_app 中执行一次。
-- 新数据库直接使用 schema.sql，无需重复执行本脚本。
CREATE TABLE IF NOT EXISTS ai_reply_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  conversation_id BIGINT NOT NULL COMMENT '所属会话',
  user_message_id BIGINT NOT NULL COMMENT '触发任务的用户消息',
  related_order_id BIGINT NOT NULL DEFAULT 0 COMMENT '已校验归属的关联订单',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/RUNNING/COMPLETED/FALLBACK/FAILED/CANCELLED',
  error_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '脱敏错误分类',
  started_at DATETIME NULL COMMENT '实际开始生成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_art_user_message (user_message_id),
  KEY idx_art_status_id (status, id),
  KEY idx_art_conversation_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服回复任务';

ALTER TABLE ai_call_log
  ADD COLUMN model_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '实际调用模型',
  ADD COLUMN input_tokens INT NULL COMMENT '输入用量，服务商未返回时为空',
  ADD COLUMN output_tokens INT NULL COMMENT '输出用量，服务商未返回时为空',
  ADD COLUMN error_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '脱敏错误分类';
