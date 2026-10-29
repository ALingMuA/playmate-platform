-- 站内通知幂等键支持
ALTER TABLE notification
  ADD COLUMN event_key VARCHAR(128) NULL DEFAULT NULL COMMENT '业务事件幂等键' AFTER related_id,
  ADD UNIQUE KEY uk_n_user_event_key (user_id, event_key);
