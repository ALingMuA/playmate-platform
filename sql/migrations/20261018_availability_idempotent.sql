-- 20261018_availability_idempotent.sql
-- 目标：为模板生成实例建立幂等唯一键，防止并发/重复生成；先清理历史重复数据（幂等可重入）。
-- 执行前请备份（deploy 脚本会自动备份 /opt/backup-<时间戳>）。

-- 1) 去重：同一陪玩师、同一模板、同一开始时间只保留 id 最小的一条
DELETE a FROM companion_availability a
JOIN companion_availability b
  ON a.companion_user_id = b.companion_user_id
 AND a.template_id = b.template_id
 AND a.start_at = b.start_at
 AND a.template_id > 0
 AND a.id > b.id;

-- 2) 生成列 + 唯一键（按需创建）：仅约束模板生成实例（template_id=0 时键为 NULL，MySQL 允许多个 NULL）
SET @dbname = DATABASE();
SET @tablename = 'companion_availability';

SET @columnname = 'recurrence_key';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE companion_availability ADD COLUMN recurrence_key VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN template_id > 0 THEN CONCAT(template_id, ''@'', DATE_FORMAT(start_at, ''%Y-%m-%d %H:%i:%s'')) END) STORED COMMENT ''模板生成实例幂等键（template_id>0 时生效）'''
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @indexname = 'uk_ca_recurrence';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @indexname) > 0,
  'SELECT 1',
  'ALTER TABLE companion_availability ADD UNIQUE KEY uk_ca_recurrence (recurrence_key)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
