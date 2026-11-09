-- ======================================================================
-- GMP 培训系统 - V7 增量脚本：外部专项培训与资质证书登记
-- 适用数据库：gmp_training_system（执行前请先 USE gmp_training_system;）
-- 说明：本脚本仅新增表与菜单数据，不修改既有表结构，可重复执行
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 外部专项培训 / 资质证书登记表 tr_external_certificate
-- 对应 GMP 指南 3.2.4「人员培训」中关于委托培训、外部机构培训以及特殊岗位
-- 持证上岗的管理要求：企业需保留人员接受外部培训及取得资质的证明材料，
-- 并对证书有效期实施跟踪，避免超期上岗。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tr_external_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '持证人用户ID（关联 sys_user.id）',
  `cert_type` tinyint NOT NULL COMMENT '类型：1-外部培训 2-资质证书',
  `cert_name` varchar(200) NOT NULL COMMENT '证书/培训名称',
  `cert_no` varchar(100) DEFAULT NULL COMMENT '证书编号（外部培训可留空）',
  `issuer` varchar(200) DEFAULT NULL COMMENT '发证机构/培训机构',
  `issue_date` date DEFAULT NULL COMMENT '发证日期',
  `expire_date` date DEFAULT NULL COMMENT '有效期至（为空表示长期有效）',
  `file_url` varchar(500) DEFAULT NULL COMMENT '证明材料存储路径（扫描件/照片）',
  `status` tinyint DEFAULT 0 COMMENT '审核状态：0-待审核 1-已生效 2-已驳回',
  `audit_by` varchar(50) DEFAULT NULL COMMENT '审核人账号',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) DEFAULT NULL COMMENT '审核意见（驳回时必填）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expire_date` (`expire_date`),
  KEY `idx_status` (`status`),
  KEY `idx_cert_type` (`cert_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部专项培训与资质证书登记表';

-- ----------------------------------------------------------------------
-- 2. sys_menu 菜单数据：培训管理 → 资质证书
-- 说明：管理端侧边栏由前端路由动态渲染，此菜单数据用于「角色管理」页面的菜单授权树展示
-- ----------------------------------------------------------------------
INSERT INTO sys_menu(name, parent_id, path, component, icon, sort, type, permission, visible, status)
SELECT '资质证书', p.id, '/training/certificate', 'views/training/certificate/index', 'Medal', 7, 2, NULL, 1, 1
FROM (SELECT id FROM sys_menu WHERE path = '/training' AND parent_id = 0 AND type = 1 LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/training/certificate');

-- 为管理员类角色授予该菜单权限（系统管理员 / QA负责人 / 培训管理员）
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path = '/training/certificate'
WHERE r.code IN ('SUPER_ADMIN', 'QA_MANAGER', 'TRAINING_ADMIN')
  AND r.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ======================================================================
-- 脚本结束
-- ======================================================================
