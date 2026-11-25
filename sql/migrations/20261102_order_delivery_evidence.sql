-- 已有数据库升级（幂等可重入）：备份后在 game_companion_app 中执行。
-- 新数据库直接使用 sql/schema.sql，无需重复执行本脚本。
SET @dbname = DATABASE();
SET @tablename = 'user';
SET @columnname = 'disabled_reason';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE `user` ADD COLUMN disabled_reason VARCHAR(500) NOT NULL DEFAULT '''' COMMENT ''管理员禁用原因'' AFTER account_status'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

CREATE TABLE IF NOT EXISTS order_delivery_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  companion_user_id BIGINT NOT NULL COMMENT '上传凭证的陪玩师用户ID',
  file_url VARCHAR(500) NOT NULL COMMENT '履约凭证文件地址',
  file_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '原始文件名',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_ode_order_sort (order_id, sort_no),
  KEY idx_ode_companion_order (companion_user_id, order_id),
  KEY idx_ode_file_url (file_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师订单履约凭证';
