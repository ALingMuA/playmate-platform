-- 演示/答辩测试账号种子数据（仅限本地开发或演示环境手动导入，严禁用于生产部署）
-- 密码统一为 Test123456；幂等插入，绝不覆盖已有密码或账号状态。
USE game_companion_app;

INSERT IGNORE INTO `user` (username, password_hash, nickname, email, mobile, account_status, token_version)
VALUES
  ('companion_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试陪玩师', 'companion@gameplay.local', '13800000002', 'ENABLED', 1),
  ('cs_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试客服', 'cs@gameplay.local', '13800000003', 'ENABLED', 1),
  ('user_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试用户', 'user@gameplay.local', '13800000004', 'ENABLED', 1);

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'companion_test' AND r.role_code IN ('USER', 'COMPANION');

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'cs_test' AND r.role_code = 'CUSTOMER_SERVICE';

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'user_test' AND r.role_code = 'USER';

INSERT IGNORE INTO companion_profile (
  user_id, display_name, profile_intro, capability_json, rating_avg, rating_count, completed_order_count, service_status, certification_status
)
SELECT
  u.id,
  '测试陪玩师',
  '精通王者荣耀、英雄联盟等热门竞技游戏，技术过硬，耐心教学，随时可约！',
  JSON_ARRAY(),
  5.0,
  12,
  36,
  'AVAILABLE',
  'APPROVED'
FROM `user` u
WHERE u.username = 'companion_test';

INSERT IGNORE INTO customer_service_account (
  user_id, cs_account, cs_name, contact_mobile, account_status, work_status, force_change_password, max_active_conversations, created_by
)
SELECT
  u.id,
  'cs_test',
  '测试客服01',
  '13800000003',
  'ENABLED',
  'ONLINE',
  0,
  5,
  1
FROM `user` u
WHERE u.username = 'cs_test';
