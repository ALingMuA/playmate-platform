-- 20261010_activity_and_coupon.sql
-- 平台活动与优惠券模块数据库表结构及订单扩展

-- 1. 平台运营活动表
CREATE TABLE IF NOT EXISTS platform_activity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '活动标题',
  subtitle VARCHAR(100) NOT NULL DEFAULT '' COMMENT '副标题',
  description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '活动描述',
  banner_image VARCHAR(255) NOT NULL DEFAULT '' COMMENT '活动横幅图',
  bg_gradient VARCHAR(100) NOT NULL DEFAULT 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' COMMENT '背景渐变',
  icon VARCHAR(32) NOT NULL DEFAULT '🎁' COMMENT '活动图标',
  tag VARCHAR(50) NOT NULL DEFAULT '' COMMENT '活动标签',
  tag_color VARCHAR(32) NOT NULL DEFAULT '#ff6b6b' COMMENT '标签颜色',
  action_type VARCHAR(32) NOT NULL DEFAULT 'COUPON_CLAIM' COMMENT '动作类型：COUPON_CLAIM, COMPANION_LIST, PAGE_NAV',
  action_route VARCHAR(255) NOT NULL DEFAULT '' COMMENT '跳转路径或路由',
  coupon_template_id BIGINT NULL DEFAULT NULL COMMENT '关联优惠券模板ID',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT, PUBLISHED, OFFLINE',
  start_time DATETIME NULL DEFAULT NULL COMMENT '活动开始时间',
  end_time DATETIME NULL DEFAULT NULL COMMENT '活动结束时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_pa_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台运营活动表';

-- 2. 优惠券模板表
CREATE TABLE IF NOT EXISTS coupon_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  name VARCHAR(100) NOT NULL DEFAULT '' COMMENT '优惠券名称',
  coupon_type VARCHAR(20) NOT NULL DEFAULT 'FIXED' COMMENT '优惠券类型：FIXED(满减), DISCOUNT(折扣)',
  threshold_cents BIGINT NOT NULL DEFAULT 0 COMMENT '使用门槛(分)，0为无门槛',
  discount_cents BIGINT NOT NULL DEFAULT 0 COMMENT '满减金额(分)',
  discount_rate_bp INT NOT NULL DEFAULT 10000 COMMENT '折扣基点，8500表示8.5折',
  max_discount_cents BIGINT NOT NULL DEFAULT 0 COMMENT '折扣券最高优惠(分)，0为不限制',
  scope_type VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '适用范围：ALL, GAME, SERVICE, COMPANION',
  scope_id BIGINT NOT NULL DEFAULT 0 COMMENT '适用目标ID，ALL时为0',
  activity_id BIGINT NULL DEFAULT NULL COMMENT '关联活动ID',
  total_quantity INT NOT NULL DEFAULT 0 COMMENT '总发行量，0为不限量',
  claimed_quantity INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
  per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每人限领数量',
  valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '有效开始时间',
  valid_until DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '有效结束时间',
  publish_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT, PUBLISHED, OFFLINE',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人管理员ID',
  published_by BIGINT NOT NULL DEFAULT 0 COMMENT '发布人管理员ID',
  published_at DATETIME NULL DEFAULT NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ct_publish_status (publish_status, valid_from, valid_until),
  KEY idx_ct_activity_id (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- 3. 用户优惠券表
CREATE TABLE IF NOT EXISTS user_coupon (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  template_id BIGINT NOT NULL COMMENT '优惠券模板ID',
  user_id BIGINT NOT NULL COMMENT '持有用户ID',
  coupon_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '优惠券唯一编码',
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE, LOCKED, USED, EXPIRED',
  valid_from DATETIME NOT NULL COMMENT '有效开始时间',
  valid_until DATETIME NOT NULL COMMENT '有效结束时间',
  locked_order_id BIGINT NULL DEFAULT NULL COMMENT '当前锁定的订单ID',
  used_order_id BIGINT NULL DEFAULT NULL COMMENT '已核销使用的订单ID',
  claimed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  locked_at DATETIME NULL DEFAULT NULL COMMENT '锁定时间',
  used_at DATETIME NULL DEFAULT NULL COMMENT '核销使用时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_uc_code (coupon_code),
  KEY idx_uc_user_status (user_id, status, valid_until),
  KEY idx_uc_orders (locked_order_id, used_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 4. 订单表增加金额与优惠快照字段（按需增加）
SET @dbname = DATABASE();

SET @tablename = "play_order";
SET @columnname = "original_amount_cents";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " BIGINT NOT NULL DEFAULT 0 COMMENT '服务原价(分)' AFTER duration_minutes;")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = "discount_amount_cents";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " BIGINT NOT NULL DEFAULT 0 COMMENT '优惠抵扣金额(分)' AFTER original_amount_cents;")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = "coupon_id";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " BIGINT NULL DEFAULT NULL COMMENT '使用的用户优惠券ID' AFTER discount_amount_cents;")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = "coupon_name_snapshot";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " VARCHAR(100) NOT NULL DEFAULT '' COMMENT '优惠券名称快照' AFTER coupon_id;")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = "settlement_amount_cents";
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  "SELECT 1",
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师结算金额(分)' AFTER coupon_name_snapshot;")
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 初始化已有历史订单的快照兜底值（原价=实付，结算=实付）
UPDATE play_order SET original_amount_cents = total_amount_cents WHERE original_amount_cents = 0 AND total_amount_cents > 0;
UPDATE play_order SET settlement_amount_cents = total_amount_cents WHERE settlement_amount_cents = 0 AND total_amount_cents > 0;
