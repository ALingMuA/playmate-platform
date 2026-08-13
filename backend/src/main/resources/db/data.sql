-- 初始化种子数据：角色与预置管理员（幂等，可重复执行）
-- 说明：密码哈希由 BCrypt 生成；管理员初始密码见项目 README（首次登录后应修改）。
USE game_companion_app;

-- 1. 系统角色
INSERT IGNORE INTO `role` (role_code, role_name, description) VALUES
  ('USER', '普通用户', '注册默认角色，可浏览、预约、评价、投诉'),
  ('COMPANION', '陪玩师', '通过入驻审核后获得，可接单履约、管理档期'),
  ('CUSTOMER_SERVICE', '客服人员', '仅由管理员在后台创建，处理用户咨询'),
  ('ADMIN', '管理员', '后台运营管理，审核、账号、内容与统计');

-- 2. 预置管理员账号（admin / Admin@123456，仅演示环境）
INSERT IGNORE INTO `user` (username, password_hash, nickname, email, account_status, token_version)
VALUES ('admin', '$2b$10$/rgy/i/TnCCzPm.P5aCLTeQwXP7rAb3v5iSJbk.pF05CZgNMJ/8XW', '系统管理员', 'admin@gameplay.local', 'ENABLED', 1);

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM `user` u JOIN `role` r ON r.role_code = 'ADMIN' WHERE u.username = 'admin';
