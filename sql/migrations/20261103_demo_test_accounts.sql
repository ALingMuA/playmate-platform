-- 预置各角色标准测试账号（答辩演示与开发测试使用，幂等可重入）
-- 密码规则：
--   admin: Admin@123456
--   companion_test / cs_test / user_test: Test123456


-- 1. 插入或更新基础用户表
INSERT INTO `user` (username, password_hash, nickname, email, mobile, account_status, token_version)
VALUES
  ('admin', '$2b$10$/rgy/i/TnCCzPm.P5aCLTeQwXP7rAb3v5iSJbk.pF05CZgNMJ/8XW', '系统管理员', 'admin@gameplay.local', '13800000000', 'ENABLED', 1),
  ('companion_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试陪玩师', 'companion@gameplay.local', '13800000002', 'ENABLED', 1),
  ('cs_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试客服', 'cs@gameplay.local', '13800000003', 'ENABLED', 1),
  ('user_test', '$2a$10$xIjEwYQtrLwJEUGIy5KTPeTlohemM93laCpJXecm3CjXatnAYOSNm', '测试用户', 'user@gameplay.local', '13800000004', 'ENABLED', 1)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  account_status = 'ENABLED';

-- 2. 绑定对应系统角色
-- 管理员角色
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

-- 陪玩师角色（同时拥有 USER 与 COMPANION 角色）
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'companion_test' AND r.role_code IN ('USER', 'COMPANION');

-- 客服角色
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'cs_test' AND r.role_code = 'CUSTOMER_SERVICE';

-- 普通用户角色
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u CROSS JOIN `role` r
WHERE u.username = 'user_test' AND r.role_code = 'USER';

-- 3. 初始化陪玩师主页资料表（companion_profile）
INSERT INTO companion_profile (
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
WHERE u.username = 'companion_test'
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name),
  service_status = 'AVAILABLE',
  certification_status = 'APPROVED';

-- 4. 初始化人工客服账号表（customer_service_account）
INSERT INTO customer_service_account (
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
WHERE u.username = 'cs_test'
ON DUPLICATE KEY UPDATE
  cs_name = VALUES(cs_name),
  account_status = 'ENABLED',
  work_status = 'ONLINE',
  force_change_password = 0;
