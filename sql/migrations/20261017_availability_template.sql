-- 20261017_availability_template.sql
-- 已有数据库升级：先备份，再在 game_companion_app 中执行一次。
-- 新数据库直接使用 schema.sql，无需重复执行本脚本。

-- 档期表增加模板来源列：区分手动创建与周期模板生成的实例，供模板更新/停用/删除时精确定位清理
ALTER TABLE companion_availability
  ADD COLUMN template_id BIGINT NOT NULL DEFAULT 0 COMMENT '生成来源模板ID（0=手动创建）' AFTER source_type,
  ADD KEY idx_ca_template (template_id);

-- 周期模板表：按周几 + 每日时段（或全天）+ 生效日期范围循环生成可约档期，
-- 通过 last_generated_until 水位线实现滚动续期（陪玩端访问时惰性补齐未来 28 天），设置一次长期生效
CREATE TABLE IF NOT EXISTS companion_availability_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师用户ID',
  template_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模板名称',
  day_of_week VARCHAR(20) NOT NULL DEFAULT '' COMMENT '生效周几集合，逗号分隔（1=周一..7=周日）',
  all_day TINYINT NOT NULL DEFAULT 0 COMMENT '是否全天可约：0否，1是',
  start_time TIME NULL DEFAULT NULL COMMENT '每日开始时间（all_day=0 时必填）',
  end_time TIME NULL DEFAULT NULL COMMENT '每日结束时间（all_day=0 时必填；早于开始时间视为跨天）',
  effective_from DATE NOT NULL COMMENT '生效开始日期',
  effective_until DATE NULL DEFAULT NULL COMMENT '生效结束日期（NULL 表示长期有效）',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0停用，1启用',
  last_generated_until DATE NOT NULL COMMENT '已生成到的日期水位线（滚动续期使用）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_cat_companion_enabled (companion_user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师档期周期模板表';
