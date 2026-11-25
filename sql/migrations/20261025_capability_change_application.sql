-- 20261025_capability_change_application.sql
-- 已有数据库升级（幂等可重入）：先备份，再在 game_companion_app 中执行。
-- 新数据库直接使用 schema.sql，无需重复执行本脚本。
-- 背景：游戏能力认证改为平台审核制——陪玩师提交"能力变更申请"（复用入驻申请表），
--       管理员审核通过后覆盖陪玩主页能力；application_type 区分两类申请。

SET @dbname = DATABASE();
SET @tablename = 'companion_application';
SET @columnname = 'application_type';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE companion_application ADD COLUMN application_type VARCHAR(20) NOT NULL DEFAULT ''ENTRY'' COMMENT ''申请类型：ENTRY 入驻申请、CAPABILITY_UPDATE 能力变更申请'' AFTER applicant_user_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
