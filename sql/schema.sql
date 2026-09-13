-- ============================================================
-- 游戏陪玩系统 数据库初始化脚本
-- 数据库: game_companion_app (utf8mb4 / InnoDB)
-- 来源: 详细设计说明书.md 第1章 数据库物理设计(DDL)
-- 说明: 金额使用 BIGINT 分值; 状态使用 VARCHAR 枚举名
-- ============================================================

CREATE DATABASE IF NOT EXISTS game_companion_app
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE game_companion_app;

CREATE TABLE `user` (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  username VARCHAR(32) NOT NULL DEFAULT '' COMMENT '登录账号',
  password_hash VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'BCrypt密码哈希',
  nickname VARCHAR(32) NOT NULL DEFAULT '' COMMENT '展示昵称',
  mobile VARCHAR(20) NULL DEFAULT NULL COMMENT '手机号（可选，唯一索引允许多个NULL）',
  email VARCHAR(100) NULL DEFAULT NULL COMMENT '邮箱（可选，唯一索引允许多个NULL）',
  avatar_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '头像文件地址',
  gender TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0未知，1男，2女',
  introduction VARCHAR(500) NOT NULL DEFAULT '' COMMENT '个人简介',
  account_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED、DISABLED',
  token_version INT NOT NULL DEFAULT 1 COMMENT 'JWT令牌版本，变更后旧令牌失效',
  last_login_at DATETIME NULL DEFAULT NULL COMMENT '最后登录时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_mobile (mobile),
  UNIQUE KEY uk_user_email (email),
  KEY idx_user_status_created (account_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号表';

CREATE TABLE `role` (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  role_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '角色编码：USER、COMPANION、CUSTOMER_SERVICE、ADMIN',
  role_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '角色名称',
  description VARCHAR(200) NOT NULL DEFAULT '' COMMENT '角色说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE user_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  role_id BIGINT NOT NULL DEFAULT 0 COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_user_role_role (role_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关系表';

CREATE TABLE companion_application (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  applicant_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '申请用户ID',
  real_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '真实姓名',
  contact_mobile VARCHAR(20) NOT NULL DEFAULT '' COMMENT '联系手机号',
  introduction VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '申请自我介绍',
  game_capability_json JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '游戏能力JSON数组',
  proof_urls_json JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '能力证明图片地址JSON数组',
  audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING、APPROVED、REJECTED',
  audit_by BIGINT NOT NULL DEFAULT 0 COMMENT '审核管理员ID，未审核为0',
  audit_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '审核意见或驳回原因',
  audited_at DATETIME NULL DEFAULT NULL COMMENT '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ca_user_status (applicant_user_id, audit_status),
  KEY idx_ca_status_created (audit_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师入驻申请表';

CREATE TABLE companion_profile (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联用户ID',
  display_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '陪玩师展示名',
  profile_intro VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '主页简介',
  capability_json JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '认证游戏能力JSON',
  rating_avg DECIMAL(3,1) NOT NULL DEFAULT 5.0 COMMENT '有效评价平均分',
  rating_count INT NOT NULL DEFAULT 0 COMMENT '有效评价数量',
  completed_order_count INT NOT NULL DEFAULT 0 COMMENT '已完成订单数',
  service_status VARCHAR(20) NOT NULL DEFAULT 'RESTING' COMMENT '接单状态：AVAILABLE、BUSY、RESTING、SUSPENDED',
  certification_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '认证状态：APPROVED、SUSPENDED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cp_user (user_id),
  KEY idx_cp_status_rating (service_status, certification_status, rating_avg)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师资料表';

CREATE TABLE game (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  game_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '游戏名称',
  game_icon_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '游戏图标地址',
  game_intro VARCHAR(500) NOT NULL DEFAULT '' COMMENT '游戏简介',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_game_name (game_name),
  KEY idx_game_enabled_sort (enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏基础表';

CREATE TABLE service_type (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  type_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '服务类型名称',
  type_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '服务类型编码',
  description VARCHAR(200) NOT NULL DEFAULT '' COMMENT '类型说明',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_st_code (type_code),
  UNIQUE KEY uk_st_name (type_name),
  KEY idx_st_enabled_sort (enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩服务类型表';

CREATE TABLE tag (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  tag_name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '标签名称',
  tag_category VARCHAR(32) NOT NULL DEFAULT '' COMMENT '标签分类：POSITION、STYLE、HERO等',
  game_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属游戏ID，0表示通用',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_game_category_name (game_id, tag_category, tag_name),
  KEY idx_tag_enabled_game (enabled, game_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务标签表';

CREATE TABLE companion_service (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师用户ID',
  game_id BIGINT NOT NULL DEFAULT 0 COMMENT '游戏ID',
  service_type_id BIGINT NOT NULL DEFAULT 0 COMMENT '服务类型ID',
  title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '服务标题',
  description VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '服务说明',
  tag_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '标签ID数组JSON',
  price_cents BIGINT NOT NULL DEFAULT 0 COMMENT '每小时价格，单位分',
  min_duration_minutes INT NOT NULL DEFAULT 60 COMMENT '最短服务时长，单位分钟',
  audit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING、APPROVED、REJECTED',
  service_status VARCHAR(20) NOT NULL DEFAULT 'OFF_SHELF' COMMENT '服务状态：ON_SHELF、OFF_SHELF',
  audit_by BIGINT NOT NULL DEFAULT 0 COMMENT '审核管理员ID',
  audit_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '审核意见',
  audit_at DATETIME NULL DEFAULT NULL COMMENT '审核时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_cs_list (game_id, service_type_id, audit_status, service_status, price_cents),
  KEY idx_cs_companion_status (companion_user_id, service_status, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩服务项目表';

CREATE TABLE companion_availability (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师用户ID',
  start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '可约开始时间',
  end_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '可约结束时间',
  availability_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE、UNAVAILABLE',
  source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL、RECURRENCE',
  remark VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ca_companion_time (companion_user_id, start_at, end_at, availability_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师可约档期表';

CREATE TABLE play_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '业务订单号',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '下单用户ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师用户ID',
  companion_service_id BIGINT NOT NULL DEFAULT 0 COMMENT '服务项目ID',
  game_id BIGINT NOT NULL DEFAULT 0 COMMENT '游戏ID快照',
  service_title_snapshot VARCHAR(100) NOT NULL DEFAULT '' COMMENT '服务标题快照',
  service_type_name_snapshot VARCHAR(50) NOT NULL DEFAULT '' COMMENT '服务类型快照',
  companion_name_snapshot VARCHAR(32) NOT NULL DEFAULT '' COMMENT '陪玩师昵称快照',
  unit_price_cents BIGINT NOT NULL DEFAULT 0 COMMENT '小时单价快照，单位分',
  duration_minutes INT NOT NULL DEFAULT 60 COMMENT '预约时长，单位分钟',
  total_amount_cents BIGINT NOT NULL DEFAULT 0 COMMENT '订单总金额，单位分',
  game_server VARCHAR(50) NOT NULL DEFAULT '' COMMENT '游戏区服',
  game_nickname VARCHAR(50) NOT NULL DEFAULT '' COMMENT '游戏昵称',
  user_remark VARCHAR(500) NOT NULL DEFAULT '' COMMENT '用户需求备注',
  appointment_start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约开始时间',
  appointment_end_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约结束时间',
  order_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态枚举',
  pay_expire_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '支付截止时间',
  accept_expire_at DATETIME NULL DEFAULT NULL COMMENT '接单截止时间',
  started_at DATETIME NULL DEFAULT NULL COMMENT '实际开始时间',
  ended_at DATETIME NULL DEFAULT NULL COMMENT '实际结束时间',
  confirmed_at DATETIME NULL DEFAULT NULL COMMENT '确认完成时间',
  closed_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '关闭或取消原因',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_po_order_no (order_no),
  KEY idx_po_user_status_created (user_id, order_status, created_at),
  KEY idx_po_companion_status_time (companion_user_id, order_status, appointment_start_at),
  KEY idx_po_timeout (order_status, pay_expire_at, accept_expire_at, confirmed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩订单主表';

CREATE TABLE order_time_slot (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_id BIGINT NOT NULL DEFAULT 0 COMMENT '订单ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师用户ID',
  start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '占用开始时间',
  end_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '占用结束时间',
  slot_status VARCHAR(20) NOT NULL DEFAULT 'TEMPORARY' COMMENT '占用状态：TEMPORARY、EFFECTIVE、RELEASED',
  expire_at DATETIME NULL DEFAULT NULL COMMENT '临时占用过期时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ots_order (order_id),
  KEY idx_ots_conflict (companion_user_id, slot_status, start_at, end_at),
  KEY idx_ots_expire (slot_status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单档期占用表';

CREATE TABLE order_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_id BIGINT NOT NULL DEFAULT 0 COMMENT '订单ID',
  from_status VARCHAR(20) NOT NULL DEFAULT '' COMMENT '迁移前状态，创建时为空字符串',
  to_status VARCHAR(20) NOT NULL DEFAULT '' COMMENT '迁移后状态',
  operator_id BIGINT NOT NULL DEFAULT 0 COMMENT '操作者ID，系统任务为0',
  operator_role VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '操作者角色',
  action_code VARCHAR(50) NOT NULL DEFAULT '' COMMENT '动作编码',
  reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原因说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_osh_order_created (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态历史表';

CREATE TABLE wallet_account (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  balance_cents BIGINT NOT NULL DEFAULT 0 COMMENT '可用虚拟余额，单位分',
  frozen_cents BIGINT NOT NULL DEFAULT 0 COMMENT '冻结余额或收益，单位分',
  total_income_cents BIGINT NOT NULL DEFAULT 0 COMMENT '陪玩师累计已结算收益，单位分',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wa_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟钱包账户表';

CREATE TABLE wallet_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  business_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '幂等业务流水号',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '资金所属用户ID',
  order_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联订单ID',
  ledger_type VARCHAR(30) NOT NULL DEFAULT '' COMMENT '流水类型：PAYMENT、REFUND、SETTLEMENT、ADJUSTMENT',
  direction VARCHAR(10) NOT NULL DEFAULT '' COMMENT '方向：IN、OUT',
  amount_cents BIGINT NOT NULL DEFAULT 0 COMMENT '变动金额，单位分且为正数',
  balance_before_cents BIGINT NOT NULL DEFAULT 0 COMMENT '变动前可用余额',
  balance_after_cents BIGINT NOT NULL DEFAULT 0 COMMENT '变动后可用余额',
  frozen_before_cents BIGINT NOT NULL DEFAULT 0 COMMENT '变动前冻结金额',
  frozen_after_cents BIGINT NOT NULL DEFAULT 0 COMMENT '变动后冻结金额',
  remark VARCHAR(500) NOT NULL DEFAULT '' COMMENT '流水说明',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建者ID，系统为0',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wl_business_no (business_no),
  KEY idx_wl_user_created (user_id, created_at),
  KEY idx_wl_order_type (order_id, ledger_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟资金流水表';

CREATE TABLE review (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_id BIGINT NOT NULL DEFAULT 0 COMMENT '订单ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '评价用户ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '被评价陪玩师ID',
  score TINYINT NOT NULL DEFAULT 5 COMMENT '评分：1至5',
  tag_json JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '评价标签JSON数组',
  content VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '评价内容',
  display_status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' COMMENT '展示状态：VISIBLE、HIDDEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_order (order_id),
  KEY idx_review_companion_display (companion_user_id, display_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

CREATE TABLE complaint (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  order_id BIGINT NOT NULL DEFAULT 0 COMMENT '订单ID',
  complainant_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '投诉发起人ID',
  complaint_type VARCHAR(30) NOT NULL DEFAULT '' COMMENT '投诉类型',
  description VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '投诉说明',
  complaint_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、PROCESSING、RESOLVED',
  resolution_type VARCHAR(30) NOT NULL DEFAULT '' COMMENT '处理类型：KEEP、FULL_REFUND、PARTIAL_REFUND',
  refund_amount_cents BIGINT NOT NULL DEFAULT 0 COMMENT '退款金额，单位分',
  handled_by BIGINT NOT NULL DEFAULT 0 COMMENT '处理管理员ID',
  handling_opinion VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '处理意见',
  handled_at DATETIME NULL DEFAULT NULL COMMENT '处理时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_complaint_order_status (order_id, complaint_status),
  KEY idx_complaint_status_created (complaint_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单投诉表';

CREATE TABLE complaint_evidence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  complaint_id BIGINT NOT NULL DEFAULT 0 COMMENT '投诉ID',
  file_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '证据文件地址',
  file_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '原始文件名',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_ce_complaint_sort (complaint_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉证据表';

CREATE TABLE favorite (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID',
  companion_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '被收藏陪玩师ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_user_companion (user_id, companion_user_id),
  KEY idx_favorite_companion (companion_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪玩师收藏表';

CREATE TABLE notification (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '接收用户ID',
  notification_type VARCHAR(32) NOT NULL DEFAULT '' COMMENT '通知类型',
  title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '通知标题',
  content VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '通知内容',
  related_type VARCHAR(32) NOT NULL DEFAULT '' COMMENT '关联业务类型',
  related_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联业务ID',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态：0否，1是',
  read_at DATETIME NULL DEFAULT NULL COMMENT '读取时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_n_user_read_created (user_id, read_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

CREATE TABLE announcement (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '公告标题',
  content VARCHAR(5000) NOT NULL DEFAULT '' COMMENT '公告内容',
  publish_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、REVOKED',
  published_by BIGINT NOT NULL DEFAULT 0 COMMENT '发布管理员ID',
  published_at DATETIME NULL DEFAULT NULL COMMENT '发布时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_an_publish_time (publish_status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告表';

CREATE TABLE customer_service_account (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联内部用户账号ID',
  cs_account VARCHAR(32) NOT NULL DEFAULT '' COMMENT '客服账号名',
  cs_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '客服姓名',
  contact_mobile VARCHAR(20) NOT NULL DEFAULT '' COMMENT '客服联系方式',
  account_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED、DISABLED',
  work_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '工作状态：ONLINE、BUSY、OFFLINE',
  force_change_password TINYINT NOT NULL DEFAULT 1 COMMENT '首次或重置后强制改密：0否，1是',
  max_active_conversations INT NOT NULL DEFAULT 5 COMMENT '最大并行处理会话数',
  disabled_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '禁用原因',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建管理员ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_csa_user (user_id),
  UNIQUE KEY uk_csa_account (cs_account),
  KEY idx_csa_work_status (account_status, work_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工客服账号表';

CREATE TABLE customer_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '会话编号',
  initiator_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '发起用户或陪玩师ID',
  source_type VARCHAR(20) NOT NULL DEFAULT 'HELP_CENTER' COMMENT '来源：HELP_CENTER、PROFILE、ORDER',
  related_order_id BIGINT NOT NULL DEFAULT 0 COMMENT '关联订单ID，无关联为0',
  conversation_status VARCHAR(20) NOT NULL DEFAULT 'AI_PROCESSING' COMMENT '会话状态枚举',
  reception_mode VARCHAR(20) NOT NULL DEFAULT 'AI' COMMENT '接待模式：AI、HUMAN、ADMIN',
  current_cs_account_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前客服账号ID，无客服为0',
  transfer_reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '转人工或转管理员原因',
  unresolved_count INT NOT NULL DEFAULT 0 COMMENT '连续未解决次数',
  closed_category VARCHAR(50) NOT NULL DEFAULT '' COMMENT '关闭问题分类',
  closed_result VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '关闭处理结果',
  closed_at DATETIME NULL DEFAULT NULL COMMENT '关闭时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cc_conversation_no (conversation_no),
  KEY idx_cc_queue (conversation_status, current_cs_account_id, created_at),
  KEY idx_cc_initiator_created (initiator_user_id, created_at),
  KEY idx_cc_order (related_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话主表';

CREATE TABLE customer_service_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_id BIGINT NOT NULL DEFAULT 0 COMMENT '会话ID',
  client_msg_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '客户端消息幂等ID',
  sender_type VARCHAR(20) NOT NULL DEFAULT '' COMMENT '发送者：USER、AI、CS、SYSTEM、ADMIN',
  sender_id BIGINT NOT NULL DEFAULT 0 COMMENT '发送者ID，AI或系统为0',
  content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '内容类型，首期仅TEXT',
  content VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '文本消息内容',
  ai_mark TINYINT NOT NULL DEFAULT 0 COMMENT 'AI标识：0否，1是',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态：0否，1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_csm_client_msg_id (client_msg_id),
  KEY idx_csm_conversation_id (conversation_id, id),
  KEY idx_csm_sender_created (sender_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服文本消息表';

CREATE TABLE conversation_assignment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_id BIGINT NOT NULL DEFAULT 0 COMMENT '会话ID',
  assignment_type VARCHAR(30) NOT NULL DEFAULT '' COMMENT '类型：AI_TRANSFER、CLAIM、ASSIGN、TRANSFER、ESCALATE_ADMIN',
  from_cs_account_id BIGINT NOT NULL DEFAULT 0 COMMENT '原客服账号ID',
  to_cs_account_id BIGINT NOT NULL DEFAULT 0 COMMENT '目标客服账号ID',
  operator_id BIGINT NOT NULL DEFAULT 0 COMMENT '操作人ID，系统为0',
  reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '分配或转交原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_cas_conversation_created (conversation_id, created_at),
  KEY idx_cas_to_cs_created (to_cs_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话分配轨迹表';

CREATE TABLE internal_note (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_id BIGINT NOT NULL DEFAULT 0 COMMENT '会话ID',
  author_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '客服或管理员用户ID',
  author_role VARCHAR(32) NOT NULL DEFAULT '' COMMENT '作者角色',
  content VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '内部备注内容，用户不可见',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_in_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服内部备注表';

CREATE TABLE ai_knowledge_base (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  category VARCHAR(50) NOT NULL DEFAULT '' COMMENT '知识分类',
  title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '问题标题',
  keywords VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '逗号分隔关键词',
  standard_answer VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '标准答案模板',
  priority INT NOT NULL DEFAULT 0 COMMENT '匹配优先级，越大越优先',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：0否，1是',
  maintained_by BIGINT NOT NULL DEFAULT 0 COMMENT '维护管理员ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_akb_category_title (category, title),
  KEY idx_akb_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服知识库表';

CREATE TABLE ai_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_id BIGINT NOT NULL DEFAULT 0 COMMENT '会话ID',
  knowledge_base_id BIGINT NOT NULL DEFAULT 0 COMMENT '命中知识库ID，未命中为0',
  provider VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_BASE' COMMENT '提供方：KNOWLEDGE_BASE、OLLAMA、EXTERNAL',
  request_summary VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '脱敏请求摘要',
  response_summary VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '脱敏响应摘要',
  confidence DECIMAL(4,3) NOT NULL DEFAULT 0.000 COMMENT '置信度，0至1',
  decision VARCHAR(30) NOT NULL DEFAULT '' COMMENT '决策：CONTINUE_AI、TRANSFER_HUMAN、FALLBACK_FAQ',
  error_message VARCHAR(500) NOT NULL DEFAULT '' COMMENT '异常摘要',
  elapsed_ms INT NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
  model_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '实际调用模型',
  input_tokens INT NULL COMMENT '输入用量，服务商未返回时为空',
  output_tokens INT NULL COMMENT '输出用量，服务商未返回时为空',
  error_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '脱敏错误分类',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_acl_conversation_created (conversation_id, created_at),
  KEY idx_acl_decision_created (decision, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服调用日志表';

CREATE TABLE ai_reply_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  conversation_id BIGINT NOT NULL COMMENT '所属会话',
  user_message_id BIGINT NOT NULL COMMENT '触发任务的用户消息',
  related_order_id BIGINT NOT NULL DEFAULT 0 COMMENT '已校验归属的关联订单',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING/RUNNING/COMPLETED/FALLBACK/FAILED/CANCELLED',
  error_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '脱敏错误分类',
  started_at DATETIME NULL COMMENT '实际开始生成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_art_user_message (user_message_id),
  KEY idx_art_status_id (status, id),
  KEY idx_art_conversation_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服回复任务';

CREATE TABLE service_evaluation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  conversation_id BIGINT NOT NULL DEFAULT 0 COMMENT '已关闭客服会话ID',
  evaluator_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '评价用户ID',
  score TINYINT NOT NULL DEFAULT 5 COMMENT '满意度评分：1至5',
  content VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '评价内容',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_se_conversation (conversation_id),
  KEY idx_se_evaluator_created (evaluator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服满意度评价表';

CREATE TABLE operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  operator_id BIGINT NOT NULL DEFAULT 0 COMMENT '操作者用户ID',
  operator_role VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作者角色',
  operation_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '操作类型',
  target_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '目标对象类型',
  target_id BIGINT NOT NULL DEFAULT 0 COMMENT '目标对象ID',
  before_data VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '变更前脱敏数据JSON',
  after_data VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '变更后脱敏数据JSON',
  reason VARCHAR(500) NOT NULL DEFAULT '' COMMENT '操作原因',
  request_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '请求追踪ID',
  ip_address VARCHAR(64) NOT NULL DEFAULT '' COMMENT '操作IP地址',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_ol_operator_created (operator_id, created_at),
  KEY idx_ol_target (target_type, target_id, created_at),
  KEY idx_ol_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台与客服操作审计日志表';

CREATE TABLE IF NOT EXISTS ai_model_settings (
  id BIGINT NOT NULL PRIMARY KEY,
  encrypted_config MEDIUMTEXT NULL COMMENT 'AES-GCM 加密后的完整配置；NULL 表示使用本机配置',
  version BIGINT NOT NULL DEFAULT 1,
  updated_by BIGINT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员 AI 接口配置';
