-- ======================================================================
-- GMP 培训系统 - V6 增量脚本：线下培训登记 + 电子签到
-- 适用数据库：gmp_training_system（执行前请先 USE gmp_training_system;）
-- 说明：本脚本仅新增表与菜单数据，不修改既有表结构，可重复执行（使用 IF NOT EXISTS）
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 线下培训场次表 tr_offline_training
-- 对应 GMP 指南 3.2.4.8「培训执行和保证」中的线下集中授课、岗位实操等培训方式的登记
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tr_offline_training` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `plan_id` bigint DEFAULT NULL COMMENT '关联培训计划ID（可为空，表示独立组织的线下培训）',
  `course_id` bigint DEFAULT NULL COMMENT '关联培训课程ID（可为空）',
  `title` varchar(200) NOT NULL COMMENT '培训主题',
  `trainer` varchar(50) DEFAULT NULL COMMENT '培训师/主讲人',
  `location` varchar(200) DEFAULT NULL COMMENT '培训地点',
  `start_time` datetime NOT NULL COMMENT '培训开始时间',
  `end_time` datetime NOT NULL COMMENT '培训结束时间',
  `sign_start_time` datetime DEFAULT NULL COMMENT '签到开始时间（为空时取培训开始前30分钟）',
  `sign_end_time` datetime DEFAULT NULL COMMENT '签到结束时间（为空时取培训结束时间）',
  `sign_code` varchar(10) DEFAULT NULL COMMENT '签到码（6位数字，由系统生成）',
  `status` tinyint DEFAULT 0 COMMENT '状态：0-未开始 1-进行中 2-已结束 3-已取消',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_plan_id` (`plan_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线下培训场次表';

-- ----------------------------------------------------------------------
-- 2. 签到记录表 tr_attendance_record
-- 对应 GMP 指南 3.2.4.10「培训文件」中的培训记录留存要求，作为培训出勤的可追溯凭证
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tr_attendance_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `training_id` bigint NOT NULL COMMENT '线下培训场次ID',
  `user_id` bigint NOT NULL COMMENT '签到学员用户ID',
  `sign_time` datetime NOT NULL COMMENT '签到时间',
  `sign_type` tinyint DEFAULT 1 COMMENT '签到方式：1-学员自主签到 2-管理员补签',
  `sign_ip` varchar(64) DEFAULT NULL COMMENT '签到来源IP（自主签到时记录）',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注（补签原因等）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  -- 同一场次同一学员仅允许一条签到记录，数据库层兜底防重复签到
  UNIQUE KEY `uk_training_user` (`training_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_sign_time` (`sign_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线下培训签到记录表';

-- ----------------------------------------------------------------------
-- 3. sys_menu 菜单数据：培训管理 → 线下培训
-- 说明：管理端侧边栏由前端路由动态渲染，此菜单数据用于「角色管理」页面的菜单授权树展示
-- 若已存在同名菜单则不重复插入
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(name, parent_id, path, component, icon, sort, type, permission, visible, status)
SELECT '线下培训', p.id, '/training/offline', 'views/training/offline/index', 'Location', 6, 2, NULL, 1, 1
FROM (SELECT id FROM sys_menu WHERE path = '/training' AND parent_id = 0 AND type = 1 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/training/offline');

-- 为管理员类角色授予该菜单权限（系统管理员 / QA负责人 / 培训管理员）
-- 说明：sys_role 的角色编码列名为 code（见建表脚本），不是 role_code
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/training/offline'
WHERE r.code IN ('SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN')
  AND r.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ======================================================================
-- 脚本结束
-- ======================================================================
