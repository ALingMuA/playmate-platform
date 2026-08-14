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

-- 3. 目录基础数据（FR-U01/FR-M10~M12 演示数据，概要设计"首批游戏"：王者荣耀、英雄联盟、和平精英）
-- 3.1 游戏
INSERT IGNORE INTO game (game_name, game_icon_url, game_intro, sort_no, enabled) VALUES
  ('王者荣耀', '', '5v5 公平竞技 MOBA 手游，开黑上分首选', 1, 1),
  ('英雄联盟', '', '经典端游 MOBA，团队配合与操作细节并重', 2, 1),
  ('和平精英', '', '百人战术竞技射击手游，吃鸡必备', 3, 1);

-- 3.2 服务类型
INSERT IGNORE INTO service_type (type_name, type_code, description, sort_no, enabled) VALUES
  ('开黑陪伴', 'TEAM_UP', '一起组队开黑，上分路上不孤单', 1, 1),
  ('娱乐陪玩', 'FUN_PLAY', '轻松娱乐局，快乐游戏时光', 2, 1),
  ('游戏教学', 'COACHING', '一对一教学，快速提升技术水平', 3, 1);

-- 3.3 标签（游戏专属 + 通用，FR-P02 能力认证、FR-M12 标签管理）
INSERT IGNORE INTO tag (tag_name, tag_category, game_id, sort_no, enabled) VALUES
  -- 王者荣耀（game_id=1）
  ('打野', 'POSITION', 1, 1, 1), ('中单', 'POSITION', 1, 2, 1),
  ('对抗路', 'POSITION', 1, 3, 1), ('发育路', 'POSITION', 1, 4, 1),
  ('游走', 'POSITION', 1, 5, 1),
  ('李白', 'HERO', 1, 1, 1), ('韩信', 'HERO', 1, 2, 1), ('貂蝉', 'HERO', 1, 3, 1),
  -- 英雄联盟（game_id=2）
  ('上单', 'POSITION', 2, 1, 1), ('打野', 'POSITION', 2, 2, 1),
  ('中单', 'POSITION', 2, 3, 1), ('ADC', 'POSITION', 2, 4, 1),
  ('辅助', 'POSITION', 2, 5, 1),
  ('亚索', 'HERO', 2, 1, 1), ('盲僧', 'HERO', 2, 2, 1), ('锤石', 'HERO', 2, 3, 1),
  -- 和平精英（game_id=3）
  ('指挥', 'POSITION', 3, 1, 1), ('突击手', 'POSITION', 3, 2, 1),
  ('狙击手', 'POSITION', 3, 3, 1), ('自由人', 'POSITION', 3, 4, 1),
  -- 通用标签（game_id=0）
  ('幽默风趣', 'STYLE', 0, 1, 1), ('技术流', 'STYLE', 0, 2, 1),
  ('耐心教学', 'STYLE', 0, 3, 1), ('高冷话少', 'STYLE', 0, 4, 1),
  ('开黑上分', 'OTHER', 0, 1, 1), ('娱乐放松', 'OTHER', 0, 2, 1);
