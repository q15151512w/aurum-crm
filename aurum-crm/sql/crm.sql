-- =============================================================================
--  AurumCRM 数据库初始化脚本（全量）
--
--  本脚本包含完整的数据库初始化，一条命令建好可直接运行的环境：
--    第一部分  表结构       —— 49 张表的建表语句
--    第二部分  基础数据     —— 部门、角色、单位、编号规则、审批流、字典
--    第三部分  菜单与权限   —— 菜单树 + 角色授权
--    第四部分  演示数据     —— 演示账号 + 业务数据（客户/线索/商机/合同/回款）
--
--  执行方式：
--    mysql -u root -p < crm.sql
--
--  数据库要求：MySQL 8.0+（依赖生成列、ngram 全文索引解析器）
--
--  ⚠️ 脚本会先 DROP 再 CREATE 全部表，【请勿在生产环境执行】。
--     生产环境请删除「第四部分 演示数据」，或改用你自己的初始化数据。
--
--  演示账号（密码统一为 123456）：
--    admin    / 123456   超级管理员   数据范围：全部
--    director / 123456   销售主管     数据范围：本部门及以下
--    sales01  / 123456   销售专员     数据范围：仅本人
--    sales02  / 123456   销售专员     数据范围：仅本人
--    finance  / 123456   财务         数据范围：全部
--
--  详细说明见 ../docs/02-建表文档.md 与 ../docs/04-权限设计文档.md
--  版本：V1.0    编写日期：2026-09-12
--
--  ⚠️ 本文件是「全量」脚本，只给**全新的空库**用。它已经是**结果**：
--     历史上所有增量改动（补列 / 补表 / 补菜单）都已经折叠进下面的建表语句与
--     种子数据里了。所以 upgrade/ 里的脚本**不是「还没合并的碎片」**，
--     别再往这里追加它们的内容 —— 那会让新库跑出 Duplicate column / 主键重复。
--
--  ⚠️ 已有数据的库**不要执行本文件**（49 条 DROP TABLE，等于清库）。
--     存量库请走 ../sql/upgrade/，清单与执行状态见 ../docs/09-生产部署.md 7.1。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `crm`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `crm`;


-- #############################################################################
--  第一部分  表结构（共 49 张表，见文末清单）
-- #############################################################################

-- =============================================================================
--  一、系统基础与权限模块（sys_）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sys_dept  组织架构表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `dept_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '部门ID',
  `parent_id`      BIGINT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '父部门ID(0为顶级)',
  `ancestors`      VARCHAR(500)      NOT NULL DEFAULT ''     COMMENT '祖级列表(逗号分隔,如 0,1,3)',
  `dept_name`      VARCHAR(64)       NOT NULL                COMMENT '部门名称',
  -- 三级组织：集团 → 部门 → 组。前端组织架构页按它给节点上不同的徽标底色
  -- （前端 value 是英文字符串 group/dept/team，后端转成这里的码值）
  `type`           TINYINT UNSIGNED  NOT NULL DEFAULT 2      COMMENT '类型(1集团 2部门 3组)',
  `dept_code`      VARCHAR(64)                DEFAULT NULL   COMMENT '部门编码',
  `leader_user_id` BIGINT UNSIGNED            DEFAULT NULL   COMMENT '部门负责人用户ID',
  `phone`          VARCHAR(32)                DEFAULT NULL   COMMENT '联系电话',
  `email`          VARCHAR(64)                DEFAULT NULL   COMMENT '邮箱',
  `sort`           SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '显示排序',
  `status`         TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态(0停用 1正常)',
  `create_by`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`dept_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_ancestors` (`ancestors`(191)),
  KEY `idx_status` (`status`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-组织架构表';


-- -----------------------------------------------------------------------------
-- 2. sys_user  用户表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '用户ID',
  `username`        VARCHAR(64)      NOT NULL                COMMENT '登录账号',
  `password`        VARCHAR(128)     NOT NULL                COMMENT '密码(BCrypt)',
  `real_name`       VARCHAR(64)      NOT NULL                COMMENT '真实姓名',
  `nickname`        VARCHAR(64)               DEFAULT NULL   COMMENT '昵称',
  `dept_id`         BIGINT UNSIGNED           DEFAULT NULL   COMMENT '所属部门ID',
  `superior_id`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '直属上级用户ID',
  `phone`           VARCHAR(255)              DEFAULT NULL   COMMENT '手机号(加密存储)',
  `email`           VARCHAR(128)              DEFAULT NULL   COMMENT '邮箱',
  `avatar`          VARCHAR(255)              DEFAULT NULL   COMMENT '头像地址',
  `gender`          TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '性别(0未知 1男 2女)',
  `status`          TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态(1正常 2禁用 3离职)',
  `last_login_time` DATETIME                  DEFAULT NULL   COMMENT '最后登录时间',
  `last_login_ip`   VARCHAR(64)               DEFAULT NULL   COMMENT '最后登录IP',
  `remark`          VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`       TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`, `is_active`),
  -- 邮箱自 2026-09-14 起是登录 / 找回密码的身份凭证，必须唯一。
  -- 与 uk_username 同款：带 is_active 生成列，逻辑删除后邮箱可被重新注册。
  -- ⚠️ MySQL 唯一索引允许多个 NULL，所以「没填邮箱」的用户不会互相冲突。
  UNIQUE KEY `uk_email` (`email`, `is_active`),
  KEY `idx_dept_id` (`dept_id`, `del_flag`),
  KEY `idx_superior_id` (`superior_id`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-用户表';


-- -----------------------------------------------------------------------------
-- 3. sys_role  角色表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `role_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '角色ID',
  `role_name`   VARCHAR(64)      NOT NULL                COMMENT '角色名称',
  `role_key`    VARCHAR(64)      NOT NULL                COMMENT '角色权限字符串',
  `data_scope`  TINYINT UNSIGNED NOT NULL DEFAULT 5      COMMENT '数据范围(1全部 2自定义 3本部门 4本部门及以下 5仅本人)',
  -- 所属角色组。NULL = 未分组。角色组只是个分组标签，不影响权限判定
  -- （权限看 role_key / data_scope / sys_role_menu），删组时要把成员的 group_id 置回 NULL
  `group_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '所属角色组ID(NULL=未分组)',
  `sort`        SMALLINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '显示排序',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态(0停用 1正常)',
  `remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`   TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_key` (`role_key`, `is_active`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_group_id` (`group_id`, `del_flag`) COMMENT '角色组列表页统计组内角色数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-角色表';


-- -----------------------------------------------------------------------------
-- 3.1 sys_role_group  角色组表
--
--  ⚠️ 这张表补得比较晚：原型里有「角色组」这个界面（新增/删除角色组），
--     菜单按钮权限 system:roleGroup:create / :delete 也早就埋在 sys_menu 种子里，
--     但建表时漏了这张表，导致「角色组」三个接口一直无表可依。
--
--  定位：**纯分组标签**。角色组不参与任何权限判定 ——
--        权限只看 sys_role.role_key + data_scope + sys_role_menu。
--        它的唯一作用是让角色列表能按「组」归类展示。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_group`;
CREATE TABLE `sys_role_group` (
  `group_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '角色组ID',
  `group_name`  VARCHAR(64)      NOT NULL                COMMENT '角色组名称',
  `sort`        SMALLINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '显示排序',
  `remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`   TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`group_id`),
  -- 组名在存活行内唯一（同 sys_role 的 uk_role_key 套路）
  UNIQUE KEY `uk_group_name` (`group_name`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-角色组表';


-- -----------------------------------------------------------------------------
-- 4. sys_user_role  用户角色关联表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-用户角色关联表';


-- -----------------------------------------------------------------------------
-- 5. sys_role_menu  角色菜单关联表  【RBAC 核心】
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-角色菜单关联表';


-- -----------------------------------------------------------------------------
-- 6. sys_role_dept  角色部门关联表（data_scope=2 自定义时生效）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept` (
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`, `dept_id`),
  KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-角色部门关联表';


-- -----------------------------------------------------------------------------
-- 7. sys_menu  菜单权限表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `menu_id`     BIGINT UNSIGNED   NOT NULL                COMMENT '菜单ID',
  `parent_id`   BIGINT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '父菜单ID(0为顶级)',
  `menu_name`   VARCHAR(64)       NOT NULL                COMMENT '菜单名称',
  `menu_type`   TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '类型(1目录 2菜单 3按钮)',
  `path`        VARCHAR(200)               DEFAULT NULL   COMMENT '路由地址',
  `component`   VARCHAR(255)               DEFAULT NULL   COMMENT '前端组件路径',
  `perms`       VARCHAR(128)               DEFAULT NULL   COMMENT '权限标识(如 crm:customer:list)',
  `icon`        VARCHAR(100)               DEFAULT NULL   COMMENT '菜单图标',
  `sort`        SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '显示排序',
  `visible`     TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '是否显示(0隐藏 1显示)',
  `status`      TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态(0停用 1正常)',
  `create_by`   BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`menu_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_perms` (`perms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-菜单权限表';


-- -----------------------------------------------------------------------------
-- 8. sys_dict_type  字典类型表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `dict_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '字典主键',
  `dict_name`   VARCHAR(100)     NOT NULL                COMMENT '字典名称',
  `dict_type`   VARCHAR(100)     NOT NULL                COMMENT '字典类型编码',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态(0停用 1正常)',
  `remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`   TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-字典类型表';


-- -----------------------------------------------------------------------------
-- 9. sys_dict_data  字典数据表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
  `dict_code`   BIGINT UNSIGNED   NOT NULL                COMMENT '字典编码',
  `dict_sort`   SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '字典排序',
  `dict_label`  VARCHAR(100)      NOT NULL                COMMENT '字典标签',
  `dict_value`  VARCHAR(100)      NOT NULL                COMMENT '字典键值',
  `dict_type`   VARCHAR(100)      NOT NULL                COMMENT '字典类型编码',
  `css_class`   VARCHAR(100)               DEFAULT NULL   COMMENT '样式属性',
  `list_class`  VARCHAR(100)               DEFAULT NULL   COMMENT '表格回显样式',
  `is_default`  TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '是否默认(0否 1是)',
  `status`      TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态(0停用 1正常)',
  `remark`      VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`dict_code`),
  KEY `idx_dict_type` (`dict_type`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-字典数据表';


-- -----------------------------------------------------------------------------
-- 10. sys_numbering_rule  编号规则表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_numbering_counter`;
DROP TABLE IF EXISTS `sys_numbering_rule_item`;
DROP TABLE IF EXISTS `sys_numbering_rule`;
CREATE TABLE `sys_numbering_rule` (
  `rule_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '规则ID',
  `rule_code`      VARCHAR(32)      NOT NULL                COMMENT '规则编码(LEAD/CUSTOMER/OPP/QUOTE/CONTRACT/PAYMENT/INVOICE)',
  `rule_name`      VARCHAR(64)      NOT NULL                COMMENT '规则名称',
  `prefix`         VARCHAR(32)      NOT NULL DEFAULT ''     COMMENT '编号前缀',
  `date_format`    VARCHAR(32)      NOT NULL DEFAULT 'yyyyMMdd' COMMENT '日期格式段(留空则不含日期)',
  `seq_length`     TINYINT UNSIGNED NOT NULL DEFAULT 4      COMMENT '流水号位数(不足左补0)',
  `reset_cycle`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '重置周期(0不重置 1按日 2按月 3按年)',
  `current_seq`    BIGINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '当前流水值',
  `current_period` VARCHAR(16)               DEFAULT NULL   COMMENT '当前周期标识(如 20260912)',
  `enabled`        TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '是否启用(0禁用 1启用)',
  `remark`         VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-编号规则表';

-- -----------------------------------------------------------------------------
-- 10.1 sys_numbering_rule_item  编号规则项表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_numbering_rule_item` (
  `item_id` BIGINT UNSIGNED NOT NULL COMMENT '编号项ID',
  `rule_id` BIGINT UNSIGNED NOT NULL COMMENT '编号规则ID',
  `item_type` VARCHAR(32) NOT NULL COMMENT '编号项类型',
  `item_value` VARCHAR(255) DEFAULT NULL COMMENT '固定值或DATE节点格式',
  `item_length` TINYINT UNSIGNED DEFAULT NULL COMMENT '流水号长度',
  `sort` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '节点顺序',
  `sequence_basis` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否参与流水分组依据',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '更新人ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标志',
  PRIMARY KEY (`item_id`), KEY `idx_rule_sort` (`rule_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-编号规则项';

-- -----------------------------------------------------------------------------
-- 10.2 sys_numbering_counter  编号分组流水计数器
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_numbering_counter` (
  `counter_id` BIGINT UNSIGNED NOT NULL COMMENT '计数器ID',
  `rule_id` BIGINT UNSIGNED NOT NULL COMMENT '编号规则ID',
  `basis_key` VARCHAR(255) NOT NULL COMMENT '流水依据分组键',
  `current_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前流水值',
  `create_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '更新人ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '删除标志',
  `is_active` TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`counter_id`), UNIQUE KEY `uk_rule_basis` (`rule_id`, `basis_key`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-编号分组流水计数器';

-- -----------------------------------------------------------------------------
-- 11. sys_unit  计量单位表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_unit`;
CREATE TABLE `sys_unit` (
  `unit_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '单位ID',
  `unit_name`      VARCHAR(32)       NOT NULL                COMMENT '单位名称',
  `unit_code`      VARCHAR(32)       NOT NULL                COMMENT '单位编码',
  `decimal_places` TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '数量小数位',
  `sort`           SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  `status`         TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态(0停用 1启用)',
  `create_by`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`unit_id`),
  UNIQUE KEY `uk_unit_code` (`unit_code`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-计量单位表';


-- -----------------------------------------------------------------------------
-- 12. sys_attachment  附件表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_attachment`;
CREATE TABLE `sys_attachment` (
  `attachment_id` BIGINT UNSIGNED  NOT NULL                COMMENT '附件ID',
  `biz_type`      VARCHAR(32)      NOT NULL                COMMENT '业务类型(contract/invoice/payment/customer...)',
  `biz_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '业务ID',
  `file_name`     VARCHAR(255)     NOT NULL                COMMENT '原始文件名',
  `file_path`     VARCHAR(500)     NOT NULL                COMMENT '存储路径',
  `file_url`      VARCHAR(500)              DEFAULT NULL   COMMENT '访问URL',
  `file_size`     BIGINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '文件大小(字节)',
  `file_type`     VARCHAR(64)               DEFAULT NULL   COMMENT '文件类型(扩展名/MIME)',
  `attachment_type` TINYINT UNSIGNED NOT NULL DEFAULT 4    COMMENT '附件分类(1合同扫描件 2发票PDF 3营业执照 4其他)',
  `upload_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '上传人ID',
  `create_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`attachment_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-附件表';


-- -----------------------------------------------------------------------------
-- 13. sys_notice  消息通知表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `notice_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '通知ID',
  `title`        VARCHAR(255)     NOT NULL                COMMENT '标题',
  `content`      TEXT                      DEFAULT NULL   COMMENT '内容',
  `notice_type`  TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '通知类型(1审批提醒 2公海回收提醒 3系统公告 4任务提醒)',
  `biz_type`     VARCHAR(32)               DEFAULT NULL   COMMENT '关联业务类型',
  `biz_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联业务ID',
  `receiver_id`  BIGINT UNSIGNED           DEFAULT NULL COMMENT '接收人ID(NULL=全员公告)',
  `is_read`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '是否已读(0未读 1已读)',
  `read_time`    DATETIME                  DEFAULT NULL   COMMENT '阅读时间',
  `handle_status` TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '是否已处理(0未处理 1已处理)',
  `handle_time`  DATETIME                  DEFAULT NULL   COMMENT '处理时间',
  `send_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `create_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`     TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`notice_id`),
  KEY `idx_receiver` (`receiver_id`, `is_read`, `del_flag`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-消息通知表';


-- -----------------------------------------------------------------------------
-- 14. sys_operation_log  操作日志表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `log_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '日志ID',
  `title`         VARCHAR(64)               DEFAULT NULL   COMMENT '模块标题',
  `business_type` TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '业务类型(0其他 1新增 2修改 3删除 4查询 5导出 6导入)',
  `method`        VARCHAR(255)              DEFAULT NULL   COMMENT '方法名称',
  `request_method` VARCHAR(16)              DEFAULT NULL   COMMENT '请求方式(GET/POST/PUT/DELETE)',
  `operator_type` TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '操作类别(0其他 1后台用户 2手机端用户)',
  `operator_id`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '操作人ID',
  `oper_name`     VARCHAR(64)               DEFAULT NULL   COMMENT '操作人名称',
  `dept_name`     VARCHAR(64)               DEFAULT NULL   COMMENT '操作人部门',
  `oper_url`      VARCHAR(255)              DEFAULT NULL   COMMENT '请求URL',
  `oper_ip`       VARCHAR(64)               DEFAULT NULL   COMMENT '操作IP',
  `oper_param`    TEXT                      DEFAULT NULL   COMMENT '请求参数',
  `json_result`   TEXT                      DEFAULT NULL   COMMENT '返回结果',
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '操作状态(0失败 1成功)',
  `error_msg`     TEXT                      DEFAULT NULL   COMMENT '错误消息',
  `oper_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `cost_time`     BIGINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '耗时(毫秒)',
  PRIMARY KEY (`log_id`),
  KEY `idx_oper_time` (`oper_time`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_business_type` (`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-操作日志表';


-- -----------------------------------------------------------------------------
-- 15. sys_login_log  登录日志表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log` (
  `log_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '日志ID',
  `username`   VARCHAR(64)               DEFAULT NULL   COMMENT '登录账号',
  `user_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '用户ID',
  `ip`         VARCHAR(64)               DEFAULT NULL   COMMENT '登录IP',
  `location`   VARCHAR(255)              DEFAULT NULL   COMMENT '登录地点',
  `browser`    VARCHAR(64)               DEFAULT NULL   COMMENT '浏览器',
  `os`         VARCHAR(64)               DEFAULT NULL   COMMENT '操作系统',
  `login_type` TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '登录方式(1密码 2短信验证码)',
  `status`     TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '登录状态(0失败 1成功)',
  `msg`        VARCHAR(255)              DEFAULT NULL   COMMENT '提示消息',
  `login_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  -- 登出时间：前端「登陆时长（小时）」= (logout_time - login_time)。
  -- ⚠️ 允许 NULL 且会**大片为 NULL**：用户直接关浏览器不触发登出，
  --    forgotPassword / changePassword 走的是 kickout 而不是 logout()，这些都不会回填。
  `logout_time` DATETIME                 DEFAULT NULL   COMMENT '登出时间（未登出为 NULL，登陆时长算不出来）',
  PRIMARY KEY (`log_id`),
  KEY `idx_login_time` (`login_time`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-登录日志表';


-- -----------------------------------------------------------------------------
-- 16. sys_data_change_log  数据变更审计表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_data_change_log`;
CREATE TABLE `sys_data_change_log` (
  `log_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '日志ID',
  `biz_type`      VARCHAR(32)      NOT NULL                COMMENT '业务类型(contract/invoice/payment)',
  `biz_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '业务ID',
  `field_name`    VARCHAR(64)      NOT NULL                COMMENT '字段名',
  `field_label`   VARCHAR(64)               DEFAULT NULL   COMMENT '字段中文名',
  `old_value`     TEXT                      DEFAULT NULL   COMMENT '变更前值',
  `new_value`     TEXT                      DEFAULT NULL   COMMENT '变更后值',
  `changed_by`    BIGINT UNSIGNED  NOT NULL                COMMENT '变更人ID',
  `changed_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  `change_source` TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '来源(1人工 2AI 3系统)',
  PRIMARY KEY (`log_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `changed_time`),
  KEY `idx_changed_by` (`changed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-数据变更审计表';


-- =============================================================================
--  二、通用审批流引擎（sys_）
--  结构：模板(flow) → 模板节点(node) → 实例(instance) → 任务(task)
--  收益：新增任何审批业务无需建表，只需配置模板
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 17. sys_approval_flow  审批流模板表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_approval_flow`;
CREATE TABLE `sys_approval_flow` (
  `flow_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '审批流ID',
  `flow_code`   VARCHAR(64)      NOT NULL                COMMENT '流程编码(CONTRACT_AUDIT/INVOICE_ISSUE/PAYMENT_AUDIT)',
  `flow_name`   VARCHAR(64)      NOT NULL                COMMENT '流程名称',
  `biz_type`    VARCHAR(32)      NOT NULL                COMMENT '业务类型(contract/invoice/payment)',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态(0停用 1启用)',
  `remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`   TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_flow_code` (`flow_code`, `is_active`),
  KEY `idx_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-审批流模板表';


-- -----------------------------------------------------------------------------
-- 18. sys_approval_node  审批流节点表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_approval_node`;
CREATE TABLE `sys_approval_node` (
  `node_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '节点ID',
  `flow_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '审批流ID',
  `node_order`     SMALLINT UNSIGNED NOT NULL                COMMENT '节点顺序(从1开始)',
  `node_name`      VARCHAR(64)       NOT NULL                COMMENT '节点名称',
  `approve_type`   TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '审批方式(1或签 2会签 3依次)',
  `approver_type`  TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '审批人类型(1指定用户 2指定角色 3部门主管 4发起人直属上级 5发起人本人)',
  `approver_value` JSON                                   COMMENT '审批人取值(用户ID/角色ID数组,如 [1,2,3];approver_type 为 3/4/5 时留空)',
  `allow_reject`   TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '是否允许驳回(0否 1是)',
  `remark`         VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `del_flag`       TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`node_id`),
  KEY `idx_flow_id` (`flow_id`, `node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-审批流节点表';


-- -----------------------------------------------------------------------------
-- 19. sys_approval_instance  审批实例表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_approval_instance`;
CREATE TABLE `sys_approval_instance` (
  `instance_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '实例ID',
  `flow_id`            BIGINT UNSIGNED   NOT NULL                COMMENT '审批流模板ID',
  `biz_type`           VARCHAR(32)       NOT NULL                COMMENT '业务类型(contract/invoice/payment)',
  `biz_id`             BIGINT UNSIGNED   NOT NULL                COMMENT '业务单据ID',
  `biz_no`             VARCHAR(64)                DEFAULT NULL   COMMENT '单据编号快照',
  `title`              VARCHAR(255)               DEFAULT NULL   COMMENT '审批标题',
  `apply_user_id`      BIGINT UNSIGNED   NOT NULL                COMMENT '发起人ID',
  `apply_dept_id`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '发起人部门ID',
  `current_node_order` SMALLINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '当前节点顺序',
  `status`             TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '状态(0审批中 1已通过 2已驳回 3已撤销)',
  `start_time`         DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `end_time`           DATETIME                   DEFAULT NULL   COMMENT '结束时间',
  `create_by`          BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`           TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`instance_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `status`),
  KEY `idx_apply_user` (`apply_user_id`, `status`),
  KEY `idx_status` (`status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-审批实例表';


-- -----------------------------------------------------------------------------
-- 20. sys_approval_task  审批任务表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_approval_task`;
CREATE TABLE `sys_approval_task` (
  `task_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '任务ID',
  `instance_id`    BIGINT UNSIGNED   NOT NULL                COMMENT '审批实例ID',
  `node_id`        BIGINT UNSIGNED            DEFAULT NULL   COMMENT '来源模板节点ID',
  `node_order`     SMALLINT UNSIGNED NOT NULL                COMMENT '节点顺序',
  `node_name`      VARCHAR(64)                DEFAULT NULL   COMMENT '节点名称快照',
  `approve_type`   TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '审批方式(1或签 2会签 3依次)',
  `approver_id`    BIGINT UNSIGNED   NOT NULL                COMMENT '实际审批人ID(解析后落库)',
  `task_status`    TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '任务状态(0待办 1已办 2已跳过)',
  `approve_result` TINYINT UNSIGNED           DEFAULT NULL   COMMENT '审批结果(1通过 2驳回 3撤销)',
  `comment`        VARCHAR(500)               DEFAULT NULL   COMMENT '审批意见',
  `handle_time`    DATETIME                   DEFAULT NULL   COMMENT '处理时间',
  `create_time`    DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`task_id`),
  KEY `idx_todo` (`approver_id`, `task_status`, `create_time`),
  KEY `idx_instance` (`instance_id`, `node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-审批任务表';


-- =============================================================================
--  三、AI 能力（sys_）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 21. sys_ai_call_log  AI调用日志表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_ai_call_log`;
CREATE TABLE `sys_ai_call_log` (
  `log_id`            BIGINT UNSIGNED  NOT NULL                COMMENT '日志ID',
  `scene`             VARCHAR(64)      NOT NULL                COMMENT '应用场景(FOLLOW_SUMMARY/STAGE_PREDICT/LEAD_SCORE/QA/OCR)',
  `model_name`        VARCHAR(64)               DEFAULT NULL   COMMENT '模型名称',
  `biz_type`          VARCHAR(32)               DEFAULT NULL   COMMENT '关联业务类型',
  `biz_id`            BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联业务ID',
  `prompt_tokens`     INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '输入Token数',
  `completion_tokens` INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '输出Token数',
  `total_tokens`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '总Token数',
  `cost`              DECIMAL(12,6)    NOT NULL DEFAULT 0.000000 COMMENT '费用',
  `latency_ms`        INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '耗时(毫秒)',
  `status`            TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态(0失败 1成功)',
  `error_msg`         VARCHAR(500)              DEFAULT NULL   COMMENT '错误信息',
  `input_digest`      VARCHAR(500)              DEFAULT NULL   COMMENT '输入摘要(不存全文,避免敏感数据落库)',
  `output_digest`     VARCHAR(500)              DEFAULT NULL   COMMENT '输出摘要',
  `operator_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '操作人ID',
  `create_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_scene` (`scene`, `create_time`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统-AI调用日志表';


-- =============================================================================
--  四、线索与客户模块（crm_）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 22. crm_pool_rule  公海规则配置表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_pool_rule`;
CREATE TABLE `crm_pool_rule` (
  `rule_id`       BIGINT UNSIGNED   NOT NULL                COMMENT '规则ID',
  `pool_type`     TINYINT UNSIGNED  NOT NULL                COMMENT '池类型(1线索池 2客户池)',
  `rule_name`     VARCHAR(64)       NOT NULL                COMMENT '规则名称',
  `recycle_days`  SMALLINT UNSIGNED NOT NULL DEFAULT 30     COMMENT '未跟进回收天数(0表示不回收)',
  `claim_limit`   SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '领取上限数量(0表示不限)',
  `claim_cycle`   TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '领取上限周期(1按日 2按周 3按月)',
  `notify_days`   SMALLINT UNSIGNED NOT NULL DEFAULT 3      COMMENT '回收前提醒天数',
  `enabled`       TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '是否启用(0禁用 1启用)',
  `remark`        VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`     BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`rule_id`),
  KEY `idx_pool_type` (`pool_type`, `enabled`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-公海规则配置表';


-- -----------------------------------------------------------------------------
-- 23. crm_lead  线索表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_lead`;
CREATE TABLE `crm_lead` (
  `lead_id`                BIGINT UNSIGNED  NOT NULL                COMMENT '线索ID',
  `lead_no`                VARCHAR(64)      NOT NULL                COMMENT '线索编号',
  `lead_name`              VARCHAR(128)     NOT NULL                COMMENT '线索名称(公司/客户名)',
  `contact_name`           VARCHAR(64)               DEFAULT NULL   COMMENT '联系人姓名',
  `position`               VARCHAR(64)               DEFAULT NULL   COMMENT '职位',
  `phone`                  VARCHAR(255)              DEFAULT NULL   COMMENT '联系电话(加密)',
  `email`                  VARCHAR(128)              DEFAULT NULL   COMMENT '邮箱',
  `wechat`                 VARCHAR(64)               DEFAULT NULL   COMMENT '微信',
  `industry`               TINYINT UNSIGNED          DEFAULT NULL   COMMENT '所属行业(字典 crm_industry)',
  `source`                 TINYINT UNSIGNED          DEFAULT NULL   COMMENT '线索来源(字典 crm_source)',
  `level`                  TINYINT UNSIGNED          DEFAULT NULL   COMMENT '客户级别(字典 crm_customer_level)',
  `province`               VARCHAR(32)               DEFAULT NULL   COMMENT '省',
  `city`                   VARCHAR(32)               DEFAULT NULL   COMMENT '市',
  `district`               VARCHAR(32)               DEFAULT NULL   COMMENT '区县',
  `address`                VARCHAR(255)              DEFAULT NULL   COMMENT '详细地址',
  `owner_user_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属人ID(NULL=在公海)',
  `dept_id`                BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `enter_pool_time`        DATETIME                  DEFAULT NULL   COMMENT '进入线索池时间',
  `enter_pool_reason_code` TINYINT UNSIGNED          DEFAULT NULL   COMMENT '进入池原因编码(字典 crm_pool_reason)',
  `enter_pool_remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '进入池原因备注',
  `prev_owner_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '前归属人ID',
  `next_contact_time`      DATE                      DEFAULT NULL   COMMENT '下次联系时间',
  `last_follow_time`       DATETIME                  DEFAULT NULL   COMMENT '最后跟进时间',
  `convert_time`           DATETIME                  DEFAULT NULL   COMMENT '转化时间',
  `converted_customer_id`  BIGINT UNSIGNED           DEFAULT NULL   COMMENT '转化生成的客户ID',
  `remark`                 VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`               TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`              TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`lead_id`),
  UNIQUE KEY `uk_lead_no` (`lead_no`, `is_active`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_pool_recycle` (`owner_user_id`, `last_follow_time`),
  KEY `idx_converted_customer` (`converted_customer_id`),
  KEY `idx_create_time` (`create_time`),
  FULLTEXT KEY `ft_lead_name` (`lead_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-线索表';


-- -----------------------------------------------------------------------------
-- 24. crm_customer  客户表（核心资产）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_customer`;
CREATE TABLE `crm_customer` (
  `customer_id`            BIGINT UNSIGNED  NOT NULL                COMMENT '客户ID',
  `customer_no`            VARCHAR(64)      NOT NULL                COMMENT '客户编号',
  `customer_name`          VARCHAR(128)     NOT NULL                COMMENT '客户名称',
  `lead_id`                BIGINT UNSIGNED           DEFAULT NULL   COMMENT '来源线索ID(可空,支持直接新增客户)',
  `industry`               TINYINT UNSIGNED          DEFAULT NULL   COMMENT '所属行业(字典 crm_industry)',
  `level`                  TINYINT UNSIGNED NOT NULL DEFAULT 2      COMMENT '客户级别(1重要 2普通 3非优先)',
  `deal_status`            TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '成交状态(1未成交 2已成交)',
  `source`                 TINYINT UNSIGNED          DEFAULT NULL   COMMENT '客户来源(字典 crm_source)',
  `phone`                  VARCHAR(255)              DEFAULT NULL   COMMENT '联系电话(加密)',
  `email`                  VARCHAR(128)              DEFAULT NULL   COMMENT '邮箱',
  `website`                VARCHAR(255)              DEFAULT NULL   COMMENT '官网',
  `province`               VARCHAR(32)               DEFAULT NULL   COMMENT '省',
  `city`                   VARCHAR(32)               DEFAULT NULL   COMMENT '市',
  `district`               VARCHAR(32)               DEFAULT NULL   COMMENT '区县',
  `region_code`            VARCHAR(12)               DEFAULT NULL   COMMENT '行政区划代码',
  `address`                VARCHAR(255)              DEFAULT NULL   COMMENT '详细地址',
  `owner_user_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属人ID(NULL=在公海)',
  `dept_id`                BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `enter_pool_time`        DATETIME                  DEFAULT NULL   COMMENT '进入公海时间',
  `enter_pool_reason_code` TINYINT UNSIGNED          DEFAULT NULL   COMMENT '进入公海原因编码(字典 crm_pool_reason)',
  `enter_pool_remark`      VARCHAR(255)              DEFAULT NULL   COMMENT '进入公海原因备注',
  `prev_owner_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '前归属人ID',
  `next_contact_time`      DATE                      DEFAULT NULL   COMMENT '下次联系时间(首页看板待回访客户判定:已过期且未成交)',
  `last_follow_time`       DATETIME                  DEFAULT NULL   COMMENT '最后跟进时间(公海回收判定依据)',
  `remark`                 VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`               TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`              TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`, `is_active`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_pool_recycle` (`owner_user_id`, `last_follow_time`),
  KEY `idx_lead_id` (`lead_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_phone` (`phone`),
  FULLTEXT KEY `ft_customer_name` (`customer_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-客户表';


-- -----------------------------------------------------------------------------
-- 25. crm_contact  联系人表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_contact`;
CREATE TABLE `crm_contact` (
  `contact_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '联系人ID',
  `customer_id`  BIGINT UNSIGNED  NOT NULL                COMMENT '客户ID',
  `contact_name` VARCHAR(64)      NOT NULL                COMMENT '姓名',
  `position`     VARCHAR(64)               DEFAULT NULL   COMMENT '职务',
  `phone`        VARCHAR(255)              DEFAULT NULL   COMMENT '手机号码(加密)',
  `telephone`    VARCHAR(32)               DEFAULT NULL   COMMENT '座机',
  `email`        VARCHAR(128)              DEFAULT NULL   COMMENT '邮箱',
  `wechat`       VARCHAR(64)               DEFAULT NULL   COMMENT '微信',
  `gender`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '性别(0未知 1男 2女)',
  `is_primary`   TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '是否主联系人(0否 1是)',
  `remark`       VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`     TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`contact_id`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-联系人表';


-- -----------------------------------------------------------------------------
-- 26. crm_customer_share  客户共享表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_customer_share`;
CREATE TABLE `crm_customer_share` (
  `share_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '共享ID',
  `customer_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '客户ID',
  `shared_user_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '被共享用户ID',
  `shared_dept_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '被共享部门ID',
  `permission`     TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '权限(1只读 2可编辑)',
  `expire_time`    DATETIME                  DEFAULT NULL   COMMENT '到期时间(NULL=永久)',
  `reason`         VARCHAR(255)              DEFAULT NULL   COMMENT '共享原因',
  `create_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  CONSTRAINT `ck_share_target` CHECK ((`shared_user_id` IS NOT NULL) <> (`shared_dept_id` IS NOT NULL)),
  PRIMARY KEY (`share_id`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_shared_user` (`shared_user_id`, `expire_time`, `del_flag`),
  KEY `idx_shared_dept` (`shared_dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-客户共享表';


-- -----------------------------------------------------------------------------
-- 27. crm_customer_invoice_info  客户开票信息表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_customer_invoice_info`;
CREATE TABLE `crm_customer_invoice_info` (
  `info_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '主键',
  `customer_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '客户ID',
  `invoice_title` VARCHAR(128)     NOT NULL                COMMENT '开票名称(抬头)',
  `tax_no`        VARCHAR(64)               DEFAULT NULL   COMMENT '纳税人识别号',
  `bank_name`     VARCHAR(128)              DEFAULT NULL   COMMENT '开户行',
  `bank_account`  VARCHAR(64)               DEFAULT NULL   COMMENT '银行账号',
  `invoice_addr`  VARCHAR(255)              DEFAULT NULL   COMMENT '开票地址',
  `invoice_phone` VARCHAR(32)               DEFAULT NULL   COMMENT '开票电话',
  `is_default`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '是否默认(0否 1是)',
  `create_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`info_id`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-客户开票信息表';


-- -----------------------------------------------------------------------------
-- 28. crm_owner_change_log  归属流转记录表（只追加，不更新不删除）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_owner_change_log`;
CREATE TABLE `crm_owner_change_log` (
  `log_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '主键',
  `biz_type`      TINYINT UNSIGNED NOT NULL                COMMENT '业务类型(1线索 2客户)',
  `biz_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '业务ID',
  `biz_name`      VARCHAR(128)              DEFAULT NULL   COMMENT '业务名称快照',
  `action`        TINYINT UNSIGNED NOT NULL                COMMENT '动作(1分配 2领取 3放入公海 4规则自动回收 5管理员改派 6线索转化)',
  `from_owner_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '原归属人(NULL=原在公海)',
  `to_owner_id`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '新归属人(NULL=进入公海)',
  `reason_code`   TINYINT UNSIGNED          DEFAULT NULL   COMMENT '原因编码(字典 crm_pool_reason)',
  `reason_remark` VARCHAR(255)              DEFAULT NULL   COMMENT '原因备注',
  `operator_id`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '操作人ID(自动回收时为NULL)',
  `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `create_time`),
  KEY `idx_to_owner` (`to_owner_id`, `create_time`),
  KEY `idx_action` (`action`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-归属流转记录表';


-- -----------------------------------------------------------------------------
-- 29. crm_region  行政区划表（可选）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_region`;
CREATE TABLE `crm_region` (
  `region_id`    BIGINT UNSIGNED   NOT NULL                COMMENT '主键',
  `parent_id`    BIGINT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '父级ID(0为顶级)',
  `region_name`  VARCHAR(64)       NOT NULL                COMMENT '区划名称',
  `region_level` TINYINT UNSIGNED  NOT NULL                COMMENT '层级(1省 2市 3区县)',
  `region_code`  VARCHAR(12)       NOT NULL                COMMENT '行政区划代码',
  `sort`         SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  PRIMARY KEY (`region_id`),
  UNIQUE KEY `uk_region_code` (`region_code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-行政区划表';


-- =============================================================================
--  五、产品 / 商机 / 报价模块（crm_）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 30. crm_product_category  产品分类表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_product_category`;
CREATE TABLE `crm_product_category` (
  `category_id`   BIGINT UNSIGNED   NOT NULL                COMMENT '分类ID',
  `parent_id`     BIGINT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '父分类ID(0为顶级)',
  `ancestors`     VARCHAR(500)      NOT NULL DEFAULT ''     COMMENT '祖级列表(逗号分隔)',
  `category_name` VARCHAR(64)       NOT NULL                COMMENT '分类名称',
  `sort`          SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  `status`        TINYINT UNSIGNED  NOT NULL DEFAULT 1      COMMENT '状态(0停用 1启用)',
  `remark`        VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`     BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`category_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-产品分类表';


-- -----------------------------------------------------------------------------
-- 31. crm_product  产品表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_product`;
CREATE TABLE `crm_product` (
  `product_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '产品ID',
  `product_no`   VARCHAR(64)      NOT NULL                COMMENT '产品编号',
  `product_name` VARCHAR(128)     NOT NULL                COMMENT '产品名称',
  `category_id`  BIGINT UNSIGNED           DEFAULT NULL   COMMENT '产品分类ID',
  `unit_id`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '计量单位ID',
  `price`        DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '标准售价',
  `cost_price`   DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '成本价',
  `tax_rate`     DECIMAL(5,2)     NOT NULL DEFAULT 0.00   COMMENT '税率(%)',
  `image`        VARCHAR(255)              DEFAULT NULL   COMMENT '产品图片',
  `description`  TEXT                      DEFAULT NULL   COMMENT '产品描述',
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '上下架状态(0下架 1上架)',
  `remark`       VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`     TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`    TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_no` (`product_no`, `is_active`),
  KEY `idx_category_id` (`category_id`, `del_flag`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_create_time` (`create_time`),
  FULLTEXT KEY `ft_product_name` (`product_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-产品表';


-- -----------------------------------------------------------------------------
-- 32. crm_opportunity  商机表（销售漏斗核心）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_opportunity`;
CREATE TABLE `crm_opportunity` (
  `opportunity_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '商机ID',
  `opportunity_no`      VARCHAR(64)      NOT NULL                COMMENT '商机编号',
  `opportunity_name`    VARCHAR(128)     NOT NULL                COMMENT '商机名称',
  `customer_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '关联客户ID',
  `contact_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联联系人ID',
  `amount`              DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '预计金额',
  `stage`               TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '销售阶段(1初步接洽 2需求确认 3方案报价 4商务谈判 5赢单 6输单)',
  `discount_rate`            DECIMAL(5,2)     NOT NULL DEFAULT 0.00   COMMENT '赢单概率(%)',
  `source`              TINYINT UNSIGNED          DEFAULT NULL   COMMENT '商机来源(字典 crm_source)',
  `owner_user_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '归属人ID',
  `dept_id`             BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `expected_close_date` DATE                      DEFAULT NULL   COMMENT '预计成交日期',
  `actual_close_date`   DATE                      DEFAULT NULL   COMMENT '实际成交日期',
  `next_contact_time`   DATE                      DEFAULT NULL   COMMENT '下次联系时间',
  `remark`              VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`           BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`            TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`           TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`opportunity_id`),
  UNIQUE KEY `uk_opportunity_no` (`opportunity_no`, `is_active`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_stage` (`stage`, `del_flag`),
  KEY `idx_expected_close` (`expected_close_date`),
  KEY `idx_create_time` (`create_time`),
  FULLTEXT KEY `ft_opportunity_name` (`opportunity_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-商机表';


-- -----------------------------------------------------------------------------
-- 33. crm_opportunity_stage_log  商机阶段变更记录表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_opportunity_stage_log`;
CREATE TABLE `crm_opportunity_stage_log` (
  `log_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '日志ID',
  `opportunity_id` BIGINT UNSIGNED  NOT NULL                COMMENT '商机ID',
  `from_stage`     TINYINT UNSIGNED          DEFAULT NULL   COMMENT '原阶段',
  `to_stage`       TINYINT UNSIGNED NOT NULL                COMMENT '新阶段',
  `win_rate`       DECIMAL(5,2)     NOT NULL DEFAULT 0.00   COMMENT '变更后赢单概率(%)',
  `change_reason`  VARCHAR(500)              DEFAULT NULL   COMMENT '变更原因',
  `source`         TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '来源(1人工 2AI)',
  `ai_confidence`  DECIMAL(5,4)              DEFAULT NULL   COMMENT 'AI置信度',
  `operator_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '操作人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_opportunity_id` (`opportunity_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-商机阶段变更记录表';


-- -----------------------------------------------------------------------------
-- 34. crm_opportunity_item  商机产品明细表
--     与 crm_quotation_item / crm_contract_item 结构一致（只换外键列）。
--     金额口径见《02-建表文档》4.x：unit_price 为含税单价，amount 为含税行金额。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_opportunity_item`;
CREATE TABLE `crm_opportunity_item` (
  `item_id`         BIGINT UNSIGNED   NOT NULL                COMMENT '明细ID',
  `opportunity_id`  BIGINT UNSIGNED   NOT NULL                COMMENT '商机ID',
  `product_id`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品ID',
  `product_name`    VARCHAR(128)      NOT NULL                COMMENT '产品名称快照',
  `product_type_id` BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品分类ID',
  `unit_id`         BIGINT UNSIGNED            DEFAULT NULL   COMMENT '计量单位ID',
  `quantity`        DECIMAL(15,4)     NOT NULL DEFAULT 1      COMMENT '数量',
  `unit_price`      DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '单价(含税)',
  `discount_rate`   DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '折扣率(%)',
  `tax_rate`        DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '税率(%)',
  `amount`          DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '行金额(含税)',
  `sort`            SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  `remark`          VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`item_id`),
  KEY `idx_opportunity_id` (`opportunity_id`, `del_flag`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-商机产品明细表';


-- -----------------------------------------------------------------------------
-- 35. crm_quotation  报价单主表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_quotation`;
CREATE TABLE `crm_quotation` (
  `quotation_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '报价单ID',
  `quotation_no`   VARCHAR(64)      NOT NULL                COMMENT '报价单编号',
  `quotation_name` VARCHAR(128)     NOT NULL                COMMENT '报价单名称',
  `opportunity_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联商机ID',
  `customer_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '关联客户ID',
  `version`        SMALLINT UNSIGNED NOT NULL DEFAULT 1     COMMENT '版本号',
  `total_amount`   DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '金额合计(不含税)',
  `tax_amount`     DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '税额合计',
  `total_with_tax` DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '价税合计',
  `valid_date`     DATE                      DEFAULT NULL   COMMENT '有效期至',
  `owner_user_id`  BIGINT UNSIGNED  NOT NULL                COMMENT '归属人ID',
  `dept_id`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态(0草稿 1已发出 2已接受 3已拒绝 4已作废)',
  `remark`         VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`quotation_id`),
  UNIQUE KEY `uk_quotation_no` (`quotation_no`, `is_active`),
  KEY `idx_opportunity_id` (`opportunity_id`, `del_flag`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-报价单主表';


-- -----------------------------------------------------------------------------
-- 36. crm_quotation_item  报价单明细表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_quotation_item`;
CREATE TABLE `crm_quotation_item` (
  `item_id`         BIGINT UNSIGNED   NOT NULL                COMMENT '明细ID',
  `quotation_id`    BIGINT UNSIGNED   NOT NULL                COMMENT '报价单ID',
  `product_id`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品ID',
  `product_name`    VARCHAR(128)      NOT NULL                COMMENT '产品名称快照',
  `product_type_id` BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品分类ID',
  `unit_id`         BIGINT UNSIGNED            DEFAULT NULL   COMMENT '计量单位ID',
  `quantity`        DECIMAL(15,4)     NOT NULL DEFAULT 1      COMMENT '数量',
  `unit_price`      DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '单价(含税)',
  `discount_rate`   DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '折扣率(%)',
  `tax_rate`        DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '税率(%)',
  `amount`          DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '行金额(含税)',
  `sort`            SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  `remark`          VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`item_id`),
  KEY `idx_quotation_id` (`quotation_id`, `del_flag`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-报价单明细表';


-- =============================================================================
--  六、合同与财务模块（crm_ / fin_）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 37. crm_contract  合同表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_contract`;
CREATE TABLE `crm_contract` (
  `contract_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '合同ID',
  `contract_no`     VARCHAR(64)      NOT NULL                COMMENT '合同编号(编号规则生成)',
  `contract_name`   VARCHAR(128)     NOT NULL                COMMENT '合同名称',
  `customer_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '关联客户ID',
  `opportunity_id`  BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联商机ID(1:N,可空)',
  `quotation_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联报价单ID',
  `contract_type`   TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '合同类型(1销售合同 2框架合同 3补充协议)',
  `amount`          DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '合同金额(含税,须等于明细汇总)',
  `owner_user_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '归属人ID',
  `dept_id`         BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `sign_date`       DATE                      DEFAULT NULL   COMMENT '签订日期',
  `start_date`      DATE                      DEFAULT NULL   COMMENT '生效日期',
  `end_date`        DATE                      DEFAULT NULL   COMMENT '到期日期',
  `customer_signer` VARCHAR(64)               DEFAULT NULL   COMMENT '客户签约人',
  `company_signer`  VARCHAR(64)               DEFAULT NULL   COMMENT '公司签约人',
  `audit_status`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '审核状态(0待提审 1审批中 2已通过 3已驳回)',
  `status`          TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '合同状态(0草稿 1已生效 2已作废 3已到期)',
  `remark`          VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`       TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`, `is_active`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_opportunity_id` (`opportunity_id`),
  KEY `idx_quotation_id` (`quotation_id`),
  KEY `idx_audit_status` (`audit_status`, `del_flag`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_sign_date` (`sign_date`),
  KEY `idx_end_date` (`end_date`),
  KEY `idx_create_time` (`create_time`),
  FULLTEXT KEY `ft_contract_name` (`contract_name`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-合同表';


-- -----------------------------------------------------------------------------
-- 38. crm_contract_item  合同产品明细表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_contract_item`;
CREATE TABLE `crm_contract_item` (
  `item_id`         BIGINT UNSIGNED   NOT NULL                COMMENT '明细ID',
  `contract_id`     BIGINT UNSIGNED   NOT NULL                COMMENT '合同ID',
  `product_id`      BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品ID',
  `product_name`    VARCHAR(128)      NOT NULL                COMMENT '产品名称快照',
  `product_type_id` BIGINT UNSIGNED            DEFAULT NULL   COMMENT '产品分类ID',
  `unit_id`         BIGINT UNSIGNED            DEFAULT NULL   COMMENT '计量单位ID',
  `quantity`        DECIMAL(15,4)     NOT NULL DEFAULT 1      COMMENT '数量',
  `unit_price`      DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '单价(含税)',
  `discount_rate`   DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '折扣率(%)',
  `tax_rate`        DECIMAL(5,2)      NOT NULL DEFAULT 0.00   COMMENT '税率(%)',
  `amount`          DECIMAL(15,2)     NOT NULL DEFAULT 0.00   COMMENT '行金额(含税)',
  `sort`            SMALLINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '排序',
  `remark`          VARCHAR(255)               DEFAULT NULL   COMMENT '备注',
  `create_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '创建人ID',
  `create_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       BIGINT UNSIGNED            DEFAULT NULL   COMMENT '更新人ID',
  `update_time`     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`        TINYINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`item_id`),
  KEY `idx_contract_id` (`contract_id`, `del_flag`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-合同产品明细表';


-- -----------------------------------------------------------------------------
-- 39. fin_payment  回款表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `fin_payment`;
CREATE TABLE `fin_payment` (
  `payment_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '回款ID',
  `payment_no`     VARCHAR(64)      NOT NULL                COMMENT '回款编号',
  `contract_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '关联合同ID',
  `customer_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '关联客户ID',
  `owner_user_id`  BIGINT UNSIGNED  NOT NULL                COMMENT '归属人ID',
  `dept_id`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `amount`         DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '回款金额',
  `payment_date`   DATE             NOT NULL                COMMENT '回款日期',
  `payment_method` TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '回款方式(1银行转账 2现金 3支票 4在线支付 5其他)',
  `account_name`   VARCHAR(128)              DEFAULT NULL   COMMENT '收款账户名',
  `account_no`     VARCHAR(64)               DEFAULT NULL   COMMENT '收款账号',
  `serial_no`      VARCHAR(128)              DEFAULT NULL   COMMENT '银行流水号',
  `audit_status`   TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '审核状态(0待提审 1审批中 2已通过 3已驳回)',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '单据状态(0待确认 1已确认 2已作废)',
  `remark`         VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`payment_id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`, `is_active`),
  KEY `idx_contract_id` (`contract_id`, `del_flag`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_audit_status` (`audit_status`, `del_flag`),
  KEY `idx_payment_date` (`payment_date`),
  KEY `idx_serial_no` (`serial_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='财务-回款表';


-- -----------------------------------------------------------------------------
-- 40. fin_invoice  发票表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `fin_invoice`;
CREATE TABLE `fin_invoice` (
  `invoice_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '发票ID',
  `apply_no`       VARCHAR(64)      NOT NULL                COMMENT '发票申请编号(系统生成)',
  `invoice_no`     VARCHAR(64)               DEFAULT NULL   COMMENT '实际发票号(开票后回填)',
  `payment_id`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '主回款ID(去规范化,取首条明细的payment_id;fin_invoice_payment为准)',
  `contract_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联合同ID(仅当全部明细同属一份合同时才有值,否则NULL)',
  `customer_id`    BIGINT UNSIGNED  NOT NULL                COMMENT '关联客户ID',
  `owner_user_id`  BIGINT UNSIGNED  NOT NULL                COMMENT '归属人ID',
  `dept_id`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `invoice_type`   TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '发票类型(1增值税专票 2增值税普票 3电子专票 4电子普票)',
  `invoice_title`  VARCHAR(128)              DEFAULT NULL   COMMENT '开票名称(抬头,来自客户开票信息快照)',
  `tax_no`         VARCHAR(64)               DEFAULT NULL   COMMENT '纳税人识别号(快照)',
  `bank_name`      VARCHAR(128)              DEFAULT NULL   COMMENT '开户行(快照)',
  `bank_account`   VARCHAR(64)               DEFAULT NULL   COMMENT '银行账号(快照)',
  `invoice_addr`   VARCHAR(255)              DEFAULT NULL   COMMENT '开票地址(快照)',
  `invoice_phone`  VARCHAR(32)               DEFAULT NULL   COMMENT '开票电话(快照)',
  `amount`         DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '不含税金额',
  `tax_rate`       DECIMAL(5,2)     NOT NULL DEFAULT 0.00   COMMENT '税率(%)',
  `tax_amount`     DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '税额',
  `total_amount`   DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '价税合计',
  `apply_date`     DATE             NOT NULL                COMMENT '申请日期',
  `issue_date`     DATE                      DEFAULT NULL   COMMENT '开票日期',
  `issue_user_id`  BIGINT UNSIGNED           DEFAULT NULL   COMMENT '开票人(财务)',
  `audit_status`   TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '审核状态(0待提审 1审批中 2已通过 3已驳回)',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '单据状态(0待开票 1已开票 2已作废)',
  `remark`         VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `attachment_ids` TEXT                      DEFAULT NULL   COMMENT '开票资料附件ID(JSON数组)',
  `create_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`      TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0 AND `status` <> 2, 1, NULL)) VIRTUAL COMMENT '未作废唯一性守卫',
  PRIMARY KEY (`invoice_id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`, `is_active`),
  KEY `idx_payment_id` (`payment_id`),
  KEY `idx_invoice_no` (`invoice_no`),
  KEY `idx_contract_id` (`contract_id`, `del_flag`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_audit_status` (`audit_status`, `del_flag`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_apply_date` (`apply_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='财务-发票表';


-- -----------------------------------------------------------------------------
-- 41. fin_invoice_payment  发票回款明细表（一票多回款，唯一事实来源）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `fin_invoice_payment`;
CREATE TABLE `fin_invoice_payment` (
  `id`               BIGINT UNSIGNED NOT NULL                COMMENT '主键',
  `invoice_id`       BIGINT UNSIGNED NOT NULL                COMMENT '发票ID',
  `payment_id`       BIGINT UNSIGNED NOT NULL                COMMENT '回款ID',
  `write_off_amount` DECIMAL(15,2)   NOT NULL DEFAULT 0.00   COMMENT '本次开票金额(含税)',
  `create_by`        BIGINT UNSIGNED          DEFAULT NULL   COMMENT '创建人ID',
  `create_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inv_pay` (`invoice_id`, `payment_id`),
  KEY `idx_payment_id` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='财务-发票回款明细表';


-- =============================================================================
--  七、销售过程 / 营销 / 售后模块（crm_）
--  注意：本模块 4 张表在原型 107 页中无对应功能，属主动扩展
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 42. crm_follow_up  跟进记录表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_follow_up`;
CREATE TABLE `crm_follow_up` (
  `followup_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '跟进记录ID',
  `biz_type`         TINYINT UNSIGNED NOT NULL                COMMENT '业务类型(1线索 2客户 3商机)',
  `biz_id`           BIGINT UNSIGNED  NOT NULL                COMMENT '业务ID',
  `customer_id`      BIGINT UNSIGNED           DEFAULT NULL   COMMENT '客户ID',
  `contact_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '联系人ID',
  `followup_type`    TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '跟进方式(1电话 2拜访 3邮件 4微信 5其他)',
  `content`          TEXT                      DEFAULT NULL   COMMENT '跟进内容',
  `result`           VARCHAR(500)              DEFAULT NULL   COMMENT '跟进结果',
  `next_follow_time` DATETIME                  DEFAULT NULL   COMMENT '下次跟进时间',
  `source`           TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '来源(1人工 2AI生成 3AI建议人工确认)',
  `ai_confidence`    DECIMAL(5,4)              DEFAULT NULL   COMMENT 'AI置信度',
  `ai_call_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联AI调用日志ID',
  `owner_user_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属人ID',
  `dept_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `create_by`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`followup_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`, `create_time`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_next_follow` (`next_follow_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-跟进记录表';


-- -----------------------------------------------------------------------------
-- 43. crm_sales_target  销售目标表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_sales_target`;
CREATE TABLE `crm_sales_target` (
  `target_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '目标ID',
  `target_year`      SMALLINT UNSIGNED NOT NULL               COMMENT '目标年份',
  `target_month`     TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '目标月份(0表示年度目标)',
  `target_type`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '目标类型(1个人 2部门)',
  `owner_user_id`    BIGINT UNSIGNED           DEFAULT NULL   COMMENT '目标归属人ID(个人目标时)',
  `dept_id`          BIGINT UNSIGNED           DEFAULT NULL   COMMENT '目标归属部门ID(部门目标时)',
  `target_amount`    DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '目标金额',
  `actual_amount`    DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '实际完成金额',
  `achievement_rate` DECIMAL(5,2)     NOT NULL DEFAULT 0.00   COMMENT '完成率(%)',
  `remark`           VARCHAR(255)              DEFAULT NULL   COMMENT '备注',
  `create_by`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`target_id`),
  KEY `idx_user_period` (`owner_user_id`, `target_year`, `target_month`, `del_flag`),
  KEY `idx_dept_period` (`dept_id`, `target_year`, `target_month`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-销售目标表';


-- -----------------------------------------------------------------------------
-- 44. crm_marketing_campaign  营销活动表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_marketing_campaign`;
CREATE TABLE `crm_marketing_campaign` (
  `campaign_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '活动ID',
  `campaign_no`   VARCHAR(64)      NOT NULL                COMMENT '活动编号',
  `campaign_name` VARCHAR(128)     NOT NULL                COMMENT '活动名称',
  `campaign_type` TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '活动类型(1展会 2广告 3线上推广 4地推 5其他)',
  `channel`       VARCHAR(64)               DEFAULT NULL   COMMENT '投放渠道',
  `start_date`    DATE                      DEFAULT NULL   COMMENT '开始日期',
  `end_date`      DATE                      DEFAULT NULL   COMMENT '结束日期',
  `budget`        DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '预算金额',
  `actual_cost`   DECIMAL(15,2)    NOT NULL DEFAULT 0.00   COMMENT '实际花费',
  `lead_count`    INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '获取线索数',
  `convert_count` INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '转化客户数',
  `roi`           DECIMAL(10,4)    NOT NULL DEFAULT 0.0000 COMMENT '投入产出比',
  `owner_user_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '负责人ID',
  `dept_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态(0未开始 1进行中 2已结束 3已取消)',
  `remark`        VARCHAR(500)              DEFAULT NULL   COMMENT '备注',
  `create_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`     TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`campaign_id`),
  UNIQUE KEY `uk_campaign_no` (`campaign_no`, `is_active`),
  KEY `idx_status` (`status`, `del_flag`),
  KEY `idx_date` (`start_date`, `end_date`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-营销活动表';


-- -----------------------------------------------------------------------------
-- 45. crm_ticket  客户工单表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_ticket`;
CREATE TABLE `crm_ticket` (
  `ticket_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '工单ID',
  `ticket_no`     VARCHAR(64)      NOT NULL                COMMENT '工单编号',
  `customer_id`   BIGINT UNSIGNED  NOT NULL                COMMENT '客户ID',
  `contract_id`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '关联合同ID',
  `title`         VARCHAR(255)     NOT NULL                COMMENT '工单标题',
  `content`       TEXT                      DEFAULT NULL   COMMENT '问题描述',
  `ticket_type`   TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '工单类型(1咨询 2故障 3投诉 4建议 5其他)',
  `priority`      TINYINT UNSIGNED NOT NULL DEFAULT 2      COMMENT '优先级(1紧急 2高 3中 4低)',
  `owner_user_id` BIGINT UNSIGNED           DEFAULT NULL   COMMENT '处理人ID',
  `dept_id`       BIGINT UNSIGNED           DEFAULT NULL   COMMENT '归属部门ID',
  `status`        TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '状态(0待处理 1处理中 2已解决 3已关闭)',
  `expect_time`   DATETIME                  DEFAULT NULL   COMMENT '期望解决时间',
  `finish_time`   DATETIME                  DEFAULT NULL   COMMENT '实际解决时间',
  `solution`      TEXT                      DEFAULT NULL   COMMENT '解决方案',
  `satisfaction`  TINYINT UNSIGNED          DEFAULT NULL   COMMENT '满意度评分(1-5)',
  `create_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`      TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`     TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`ticket_id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`, `is_active`),
  KEY `idx_customer_id` (`customer_id`, `del_flag`),
  KEY `idx_owner` (`owner_user_id`, `dept_id`, `del_flag`),
  KEY `idx_dept` (`dept_id`, `del_flag`) COMMENT '数据权限:本部门/本部门及以下查询',
  KEY `idx_status` (`status`, `priority`, `del_flag`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-客户工单表';


-- -----------------------------------------------------------------------------
-- 46. crm_star  关注收藏表
--     通用表：靠 biz_type + biz_id 指向任意业务对象，线索/客户/商机/报价单/合同/回款/发票共用。
--     唯一键带 is_active（生成列方案，见《02-建表文档》1.5）—— 取消关注是逻辑删除，
--     is_active 随之变 NULL，MySQL 唯一索引不约束 NULL，所以重新关注能插入新行
--     而不会撞 Duplicate entry。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_star`;
CREATE TABLE `crm_star` (
  `star_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '关注ID',
  `biz_type`    TINYINT UNSIGNED NOT NULL                COMMENT '业务类型(1线索 2客户 3商机 4报价单 5合同 6回款 7发票)',
  `biz_id`      BIGINT UNSIGNED  NOT NULL                COMMENT '业务ID(线索ID/客户ID/商机ID/…)',
  `user_id`     BIGINT UNSIGNED  NOT NULL                COMMENT '关注人ID',
  `create_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '创建人ID',
  `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   BIGINT UNSIGNED           DEFAULT NULL   COMMENT '更新人ID',
  `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志(0存在 1删除)',
  `is_active`   TINYINT UNSIGNED GENERATED ALWAYS AS (IF(`del_flag` = 0, 1, NULL)) VIRTUAL COMMENT '唯一性守卫',
  PRIMARY KEY (`star_id`),
  UNIQUE KEY `uk_biz_user` (`biz_type`, `biz_id`, `user_id`, `is_active`),
  KEY `idx_user_biz` (`user_id`, `biz_type`, `del_flag`) COMMENT 'scope=starred 与列表批量关注态查询',
  KEY `idx_biz` (`biz_type`, `biz_id`, `del_flag`) COMMENT '反查:某条单据被谁关注'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CRM-关注收藏表';


SET FOREIGN_KEY_CHECKS = 1;



-- #############################################################################
--  第二部分  基础数据（部门 / 角色 / 单位 / 编号规则 / 审批流 / 公海规则 / 字典）
-- #############################################################################

-- 说明：用户（sys_user）与用户-角色映射（sys_user_role）的初始化数据
--       统一放在「第四部分 演示数据」中，因为那里包含全部 14 个演示账号。

-- =============================================================================
--  八、初始化数据（可选，按需执行）
-- =============================================================================

-- ---------- 默认部门 ----------
-- ⚠️ `ancestors` 必须与实际父子关系严格一致（形如 '0,1,2'，含全部祖先、不含自身）。
--    数据权限的「本部门及以下」靠它做前缀匹配（LIKE '0,1,6,%'）来圈出子部门，
--    写错了那个角色的可见范围就会算错 —— 而且不报错，只是数据不对。
--
-- ⚠️ 部门名以**前端 `aurum-crm-ui` 的 `src/mock/db.js` 里的 deptTree 为准**
--    （原型文档没有列举部门名，只有「组织架构」这个模块和它的动作，
--     所以前端那套「零捌集团 / 采购部 / 质量部 / 仓运部 / 销售部 / 生产部」是唯一具体口径）。
--    改这里时请同步前端 mock，否则两边显示不一致。
-- ⚠️⚠️ **部门 ID 不是随便给的** —— `crm_customer` / `crm_lead` / `crm_opportunity` /
--      `crm_contract` / `crm_quotation` / `crm_follow_up` / `fin_payment` 七张表的
--      `dept_id` 都指向这里，改动 id 的语义会让那批数据挂到错误的部门下。
--      **dept 2 必须保持「销售部」**（当前有 37 行业务数据引用它）。
--      要加部门请用新 id（5/6/7/50/51/60/61/62…），不要重排。
INSERT INTO `sys_dept` (`dept_id`, `parent_id`, `ancestors`, `dept_name`, `type`, `sort`, `status`) VALUES
-- id 1-3 是原有部门，名字保持不变（1 由「总公司」改为「零捌集团」，与前端 mock 的根节点一致）
(1,  0,  '0',       '零捌集团',   1, 1, 1),
(2,  1,  '0,1',     '销售部',     2, 2, 1),
(3,  1,  '0,1',     '财务部',     2, 3, 1),
-- id 4 原为「技术部」，但没有任何数据引用它，改名对齐前端 mock 无副作用
(4,  1,  '0,1',     '质量部',     2, 4, 1),
(5,  1,  '0,1',     '采购部',     2, 5, 1),
(6,  1,  '0,1',     '仓运部',     2, 6, 1),
(7,  1,  '0,1',     '生产部',     2, 7, 1),
-- 三级小组（type=3）。销售部下分三组，这样「本部门及以下 / 本部门 / 仅本人」三种范围
-- 才有肉眼可见的差别 —— 数据权限的演示就靠这个层次
(60, 2,  '0,1,2',   '销售一组',   3, 1, 1),
(61, 2,  '0,1,2',   '销售二组',   3, 2, 1),
(62, 2,  '0,1,2',   '销售三组',   3, 3, 1),
(50, 5,  '0,1,5',   '采购一组',   3, 1, 1),
(51, 5,  '0,1,5',   '采购二组',   3, 2, 1);


-- ---------- 默认角色 ----------
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `data_scope`, `group_id`, `sort`, `status`, `remark`) VALUES
(1, '超级管理员', 'admin',   1, NULL, 1, 1, '拥有全部权限'),
(2, '销售总监',   'sales_director', 4, 1, 2, 1, '可查看本部门及以下数据'),
(3, '销售专员',   'sales',   5, 1, 3, 1, '仅可查看本人数据'),
(4, '财务',       'finance', 1, 2, 4, 1, '可查看全部财务数据');

-- 角色组：原型里叫「组组合一 / 组组合二」（无业务含义），这里改成能看懂的名字
INSERT INTO `sys_role_group` (`group_id`, `group_name`, `sort`, `remark`) VALUES
(1, '管理组', 1, '系统管理类角色'),
(2, '销售组', 2, '销售相关角色'),
(3, '职能组', 3, '财务等职能角色');

-- 角色归属哪个组（NULL = 未分组）。角色组只是分组标签，改成什么值都不影响权限判定
UPDATE `sys_role` SET `group_id` = 1 WHERE `role_id` = 1;   -- 超级管理员 → 管理组
UPDATE `sys_role` SET `group_id` = 2 WHERE `role_id` IN (2, 3);  -- 销售总监 / 销售专员 → 销售组
UPDATE `sys_role` SET `group_id` = 3 WHERE `role_id` = 4;   -- 财务 → 职能组


-- ---------- 计量单位 ----------
INSERT INTO `sys_unit` (`unit_id`, `unit_name`, `unit_code`, `decimal_places`, `sort`, `status`) VALUES
(1, '个', 'PCS', 0, 1, 1),
(2, '台', 'SET', 0, 2, 1),
(3, '套', 'KIT', 0, 3, 1),
(4, '年', 'YEAR', 0, 4, 1),
(5, '次', 'TIMES', 0, 5, 1);

-- ---------- 编号规则 ----------
INSERT INTO `sys_numbering_rule` (`rule_id`, `rule_code`, `rule_name`, `prefix`, `date_format`, `seq_length`, `reset_cycle`, `enabled`) VALUES
(1, 'LEAD',     '线索编号',   'XS', 'yyyyMMdd', 4, 1, 1),
(2, 'CUSTOMER', '客户编号',   'KH', 'yyyyMMdd', 4, 1, 1),
(3, 'OPP',      '商机编号',   'SJ', 'yyyyMMdd', 4, 1, 1),
(4, 'QUOTE',    '报价单编号', 'BJ', 'yyyyMMdd', 4, 1, 1),
(5, 'CONTRACT', '合同编号',   'HT', 'yyyyMMdd', 6, 1, 1),
(6, 'PAYMENT',  '回款编号',   'HK', 'yyyyMMdd', 6, 1, 1),
(7, 'INVOICE',  '发票申请号', 'FP', 'yyyyMMdd', 6, 1, 1);

INSERT INTO `sys_numbering_rule_item`
  (`item_id`, `rule_id`, `item_type`, `item_value`, `item_length`, `sort`, `sequence_basis`)
VALUES
  (10001,1,'FIXED_PREFIX','XS',NULL,0,0),(10002,1,'YEAR',NULL,NULL,1,0),(10003,1,'MONTH',NULL,NULL,2,0),(10004,1,'DAY',NULL,NULL,3,1),(10005,1,'SERIAL',NULL,4,4,1),
  (10006,2,'FIXED_PREFIX','KH',NULL,0,0),(10007,2,'YEAR',NULL,NULL,1,0),(10008,2,'MONTH',NULL,NULL,2,0),(10009,2,'DAY',NULL,NULL,3,1),(10010,2,'SERIAL',NULL,4,4,1),
  (10011,3,'FIXED_PREFIX','SJ',NULL,0,0),(10012,3,'YEAR',NULL,NULL,1,0),(10013,3,'MONTH',NULL,NULL,2,0),(10014,3,'DAY',NULL,NULL,3,1),(10015,3,'SERIAL',NULL,4,4,1),
  (10016,4,'FIXED_PREFIX','BJ',NULL,0,0),(10017,4,'YEAR',NULL,NULL,1,0),(10018,4,'MONTH',NULL,NULL,2,0),(10019,4,'DAY',NULL,NULL,3,1),(10020,4,'SERIAL',NULL,4,4,1),
  (10021,5,'FIXED_PREFIX','HT',NULL,0,0),(10022,5,'YEAR',NULL,NULL,1,0),(10023,5,'MONTH',NULL,NULL,2,0),(10024,5,'DAY',NULL,NULL,3,1),(10025,5,'SERIAL',NULL,6,4,1),
  (10026,6,'FIXED_PREFIX','HK',NULL,0,0),(10027,6,'YEAR',NULL,NULL,1,0),(10028,6,'MONTH',NULL,NULL,2,0),(10029,6,'DAY',NULL,NULL,3,1),(10030,6,'SERIAL',NULL,6,4,1),
  (10031,7,'FIXED_PREFIX','FP',NULL,0,0),(10032,7,'YEAR',NULL,NULL,1,0),(10033,7,'MONTH',NULL,NULL,2,0),(10034,7,'DAY',NULL,NULL,3,1),(10035,7,'SERIAL',NULL,6,4,1);

-- ---------- 审批流模板：合同审核（两级）----------
INSERT INTO `sys_approval_flow` (`flow_id`, `flow_code`, `flow_name`, `biz_type`, `status`) VALUES
(1, 'CONTRACT_AUDIT', '合同审核流', 'contract', 1),
(2, 'INVOICE_ISSUE',  '发票开票流', 'invoice',  1),
(3, 'PAYMENT_AUDIT',  '回款审核流', 'payment',  1);

INSERT INTO `sys_approval_node`
  (`node_id`, `flow_id`, `node_order`, `node_name`, `approve_type`, `approver_type`, `approver_value`, `allow_reject`) VALUES
(1, 1, 1, '部门主管审批', 1, 4, NULL,    1),
(2, 1, 2, '财务审批',     1, 2, '[4]',   1),
(3, 2, 1, '财务审批',     1, 2, '[4]',   1),
(4, 3, 1, '财务审批',     1, 2, '[4]',   1);

-- ---------- 公海规则 ----------
INSERT INTO `crm_pool_rule` (`rule_id`, `pool_type`, `rule_name`, `recycle_days`, `claim_limit`, `claim_cycle`, `notify_days`, `enabled`) VALUES
(1, 1, '线索池默认规则', 15, 50, 1, 3, 1),
(2, 2, '客户公海默认规则', 30, 20, 1, 3, 1);

-- ---------- 字典类型 ----------
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `remark`) VALUES
(1,  '客户来源',   'crm_source',          1, '客户/线索来源'),
(2,  '所属行业',   'crm_industry',        1, '行业分类'),
(3,  '客户级别',   'crm_customer_level',  1, '客户重要程度'),
(11, '成交状态',   'crm_deal_status',     1, '客户是否成交'),
(4,  '公海原因',   'crm_pool_reason',     1, '进入公海/池的原因'),
(5,  '商机阶段',   'crm_opp_stage',       1, '销售漏斗阶段'),
(6,  '合同类型',   'crm_contract_type',   1, '合同分类'),
(7,  '发票类型',   'fin_invoice_type',    1, '发票种类'),
(8,  '回款方式',   'fin_payment_method',  1, '回款支付方式'),
(9,  '附件类型',   'crm_attachment_type', 1, '附件分类'),
(10, '数据范围',   'sys_data_scope',      1, '角色数据权限范围');

-- ---------- 字典数据 ----------
INSERT INTO `sys_dict_data` (`dict_code`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`) VALUES
-- 客户来源
(1, 1, '官网',   '1', 'crm_source', 0, 1),
(2, 2, '电话',   '2', 'crm_source', 0, 1),
(3, 3, '展会',   '3', 'crm_source', 0, 1),
(4, 4, '转介绍', '4', 'crm_source', 0, 1),
(5, 5, '广告',   '5', 'crm_source', 0, 1),
(6, 6, '地推',   '6', 'crm_source', 0, 1),
(7, 7, '其他',   '7', 'crm_source', 1, 1),
-- 所属行业
(11, 1, '制造业', '1', 'crm_industry', 0, 1),
(12, 2, '互联网', '2', 'crm_industry', 0, 1),
(13, 3, '金融',   '3', 'crm_industry', 0, 1),
(14, 4, '教育',   '4', 'crm_industry', 0, 1),
(15, 5, '医疗',   '5', 'crm_industry', 0, 1),
(16, 6, '政府',   '6', 'crm_industry', 0, 1),
(17, 7, '零售',   '7', 'crm_industry', 0, 1),
(18, 8, '其他',   '8', 'crm_industry', 1, 1),
-- 客户级别
(21, 1, '重要',   '1', 'crm_customer_level', 0, 1),
(22, 2, '普通',   '2', 'crm_customer_level', 1, 1),
(23, 3, '非优先', '3', 'crm_customer_level', 0, 1),
-- 成交状态
(36, 1, '未成交', '1', 'crm_deal_status', 1, 1),
(37, 2, '已成交', '2', 'crm_deal_status', 0, 1),
-- 公海原因
(31, 1, '长期未跟进', '1', 'crm_pool_reason', 0, 1),
(32, 2, '主动放弃',   '2', 'crm_pool_reason', 0, 1),
(33, 3, '管理员操作', '3', 'crm_pool_reason', 0, 1),
(34, 4, '规则自动回收', '4', 'crm_pool_reason', 0, 1),
(35, 5, '客户失联',   '5', 'crm_pool_reason', 0, 1),
-- 商机阶段
(41, 1, '初步接洽', '1', 'crm_opp_stage', 1, 1),
(42, 2, '需求确认', '2', 'crm_opp_stage', 0, 1),
(43, 3, '方案报价', '3', 'crm_opp_stage', 0, 1),
(44, 4, '商务谈判', '4', 'crm_opp_stage', 0, 1),
(45, 5, '赢单',     '5', 'crm_opp_stage', 0, 1),
(46, 6, '输单',     '6', 'crm_opp_stage', 0, 1),
-- 合同类型
(51, 1, '销售合同', '1', 'crm_contract_type', 1, 1),
(52, 2, '框架合同', '2', 'crm_contract_type', 0, 1),
(53, 3, '补充协议', '3', 'crm_contract_type', 0, 1),
-- 发票类型
(61, 1, '增值税专票', '1', 'fin_invoice_type', 1, 1),
(62, 2, '增值税普票', '2', 'fin_invoice_type', 0, 1),
(63, 3, '电子专票',   '3', 'fin_invoice_type', 0, 1),
(64, 4, '电子普票',   '4', 'fin_invoice_type', 0, 1),
-- 回款方式
(71, 1, '银行转账', '1', 'fin_payment_method', 1, 1),
(72, 2, '现金',     '2', 'fin_payment_method', 0, 1),
(73, 3, '支票',     '3', 'fin_payment_method', 0, 1),
(74, 4, '在线支付', '4', 'fin_payment_method', 0, 1),
(75, 5, '其他',     '5', 'fin_payment_method', 0, 1),
-- 附件类型
(81, 1, '合同扫描件', '1', 'crm_attachment_type', 0, 1),
(82, 2, '发票PDF',    '2', 'crm_attachment_type', 0, 1),
(83, 3, '营业执照',   '3', 'crm_attachment_type', 0, 1),
(84, 4, '其他',       '4', 'crm_attachment_type', 1, 1),
-- 数据范围
(91, 1, '全部数据',       '1', 'sys_data_scope', 0, 1),
(92, 2, '自定义数据',     '2', 'sys_data_scope', 0, 1),
(93, 3, '本部门数据',     '3', 'sys_data_scope', 0, 1),
(94, 4, '本部门及以下数据', '4', 'sys_data_scope', 0, 1),
(95, 5, '仅本人数据',     '5', 'sys_data_scope', 1, 1);

-- =============================================================================
--  脚本结束
--  共 49 张表（48 必需 + 1 可选：crm_region）
--  注：fin_invoice_payment 原先是「可选扩展」，现已升为在用 —— 发票的
--      回款明细以它为准，不再往 fin_invoice 上挂一对一约束。
-- =============================================================================



-- #############################################################################
--  第三部分  菜单与权限
-- #############################################################################

-- =============================================================================
--  3.1 菜单树
-- =============================================================================

INSERT INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
VALUES

-- ---------------------------------------------------------------------------
--  一级导航（8 项，与原型一致）
-- ---------------------------------------------------------------------------
(1,   0,  '首页',       2, '/dashboard',   'dashboard/index',   'dashboard:view',     'HomeFilled',     1, 1, 1),

(10,  0,  '线索',       1, NULL              ,          NULL, 'crm:lead',           'Promotion',      2, 1, 1),
(20,  0,  '客户',       1, NULL              ,      NULL, 'crm:customer',       'User',           3, 1, 1),
(30,  0,  '商机',       1, NULL              ,   NULL, 'crm:opportunity',    'TrendCharts',    4, 1, 1),
(40,  0,  '合同',       1, NULL              ,      NULL, 'crm:contract',       'Document',       5, 1, 1),
(50,  0,  '产品类型',   1, NULL              ,       NULL, 'crm:product',        'Goods',          6, 1, 1),
(60,  0,  '财务',       1, NULL              ,           NULL, 'fin:payment',        'Money',          7, 1, 1),
(70,  0,  '系统设置',   1, NULL              ,            NULL, 'system:dept',        'OfficeBuilding', 8, 1, 1),

-- ---------------------------------------------------------------------------
--  线索（10）
-- ---------------------------------------------------------------------------
(11,   10, '线索',       2, '/crm/lead'       ,        'crm/lead/index',        'crm:lead:list',      'Tickets',  1, 1, 1),
(1101, 11, '新增线索',   3, NULL, NULL,    'crm:lead:create',   NULL, 1, 1, 1),
(1102, 11, '修改线索',   3, NULL, NULL,    'crm:lead:update',   NULL, 2, 1, 1),
(1103, 11, '删除线索',   3, NULL, NULL,    'crm:lead:delete',   NULL, 3, 1, 1),
(1104, 11, '导出线索',   3, NULL, NULL,    'crm:lead:export',   NULL, 4, 1, 1),
(1105, 11, '放入线索池', 3, NULL, NULL,    'crm:lead:toPool',   NULL, 5, 1, 1),
(1106, 11, '线索转客户', 3, NULL, NULL,    'crm:lead:convert',  NULL, 6, 1, 1),

(12,   10, '线索池',     2, '/crm/lead-pool'  ,   'crm/lead-pool/index',   'crm:leadPool:list',  'Box',      2, 1, 1),
(1201, 12, '分配',       3, NULL, NULL,    'crm:leadPool:assign', NULL, 1, 1, 1),
(1202, 12, '领取',       3, NULL, NULL,    'crm:leadPool:claim',  NULL, 2, 1, 1),
(1203, 12, '删除',       3, NULL, NULL,    'crm:leadPool:delete', NULL, 3, 1, 1),

-- 隐藏页：详情页由列表跳转，不出现在侧边栏
(13,   10, '线索详情',   2, '/crm/lead/:id'   ,    'crm/lead/detail',       'crm:lead:list',      'Document', 9, 0, 1),

-- ---------------------------------------------------------------------------
--  客户（20）
-- ---------------------------------------------------------------------------
(21,   20, '客户',       2, '/crm/customer'   ,      'crm/customer/index',       'crm:customer:list',   'User',     1, 1, 1),
(2101, 21, '新增客户',   3, NULL, NULL,      'crm:customer:create', NULL, 1, 1, 1),
(2102, 21, '修改客户',   3, NULL, NULL,      'crm:customer:update', NULL, 2, 1, 1),
(2103, 21, '删除客户',   3, NULL, NULL,      'crm:customer:delete', NULL, 3, 1, 1),
(2104, 21, '导出客户',   3, NULL, NULL,      'crm:customer:export', NULL, 4, 1, 1),
(2105, 21, '放入公海',   3, NULL, NULL,      'crm:customer:toPool', NULL, 5, 1, 1),
(2106, 21, '客户共享',   3, NULL, NULL,      'crm:customer:share',  NULL, 6, 1, 1),

(22,   20, '公海',       2, '/crm/customer-pool', 'crm/customer-pool/index',  'crm:customerPool:list', 'Ship', 2, 1, 1),
(2201, 22, '分配',       3, NULL, NULL,      'crm:customerPool:assign', NULL, 1, 1, 1),
(2202, 22, '领取',       3, NULL, NULL,      'crm:customerPool:claim',  NULL, 2, 1, 1),
(2203, 22, '删除',       3, NULL, NULL,      'crm:customerPool:delete', NULL, 3, 1, 1),

(23,   20, '客户详情',   2, '/crm/customer/:id', 'crm/customer/detail',      'crm:customer:list',   'Document', 9, 0, 1),
(2301, 23, '新增联系人', 3, NULL, NULL,      'crm:contact:create',      NULL, 1, 1, 1),
(2302, 23, '修改联系人', 3, NULL, NULL,      'crm:contact:update',      NULL, 2, 1, 1),
(2303, 23, '删除联系人', 3, NULL, NULL,      'crm:contact:delete',      NULL, 3, 1, 1),
(2304, 23, '维护开票信息', 3, NULL, NULL,    'crm:invoiceInfo:update',  NULL, 4, 1, 1),

(24,   20, '公海规则',   2, '/crm/pool-rule'  ,    'crm/pool-rule/index',      'crm:poolRule:list',   'Setting', 9, 0, 1),
(2401, 24, '修改公海规则', 3, NULL, NULL,    'crm:poolRule:update',     NULL, 1, 1, 1),

-- ---------------------------------------------------------------------------
--  商机（30）
-- ---------------------------------------------------------------------------
(31,   30, '商机',       2, '/crm/opportunity',      'crm/opportunity/index',      'crm:opportunity:list',  'TrendCharts', 1, 1, 1),
(3101, 31, '新增商机',   3, NULL, NULL,        'crm:opportunity:create',      NULL, 1, 1, 1),
(3102, 31, '修改商机',   3, NULL, NULL,        'crm:opportunity:update',      NULL, 2, 1, 1),
(3103, 31, '删除商机',   3, NULL, NULL,        'crm:opportunity:delete',      NULL, 3, 1, 1),
(3104, 31, '导出商机',   3, NULL, NULL,        'crm:opportunity:export',      NULL, 4, 1, 1),
(3105, 31, '变更阶段',   3, NULL, NULL,        'crm:opportunity:changeStage', NULL, 5, 1, 1),
(3106, 31, '添加产品',   3, NULL, NULL,        'crm:opportunity:addProduct',  NULL, 6, 1, 1),

(32,   30, '商机详情',   2, '/crm/opportunity/:id', 'crm/opportunity/detail',     'crm:opportunity:list',  'Document', 9, 0, 1),

(33,   30, '商机报价单', 2, '/crm/quotation'  ,       'crm/quotation/index',        'crm:quotation:list',    'Tickets', 2, 1, 1),
(3301, 33, '新增报价单', 3, NULL, NULL,        'crm:quotation:create',        NULL, 1, 1, 1),
(3302, 33, '修改报价单', 3, NULL, NULL,        'crm:quotation:update',        NULL, 2, 1, 1),
(3303, 33, '删除报价单', 3, NULL, NULL,        'crm:quotation:delete',        NULL, 3, 1, 1),

(34,   30, '报价单详情', 2, '/crm/quotation/:id', 'crm/quotation/detail', 'crm:quotation:list', 'Document', 9, 0, 1),
-- ---------------------------------------------------------------------------
--  合同（40）
-- ---------------------------------------------------------------------------
(41,   40, '合同',       2, '/crm/contract'   ,       'crm/contract/index',       'crm:contract:list',      'Document', 1, 1, 1),
(4101, 41, '新增合同',   3, NULL, NULL,       'crm:contract:create',   NULL, 1, 1, 1),
(4102, 41, '修改合同',   3, NULL, NULL,       'crm:contract:update',   NULL, 2, 1, 1),
(4103, 41, '删除合同',   3, NULL, NULL,       'crm:contract:delete',   NULL, 3, 1, 1),
(4104, 41, '导出合同',   3, NULL, NULL,       'crm:contract:export',   NULL, 4, 1, 1),
(4105, 41, '提交审核',   3, NULL, NULL,       'crm:contract:submit',   NULL, 5, 1, 1),
(4106, 41, '作废合同',   3, NULL, NULL,       'crm:contract:void',     NULL, 6, 1, 1),

(42,   40, '合同审核',   2, '/crm/contract-audit', 'crm/contract/audit',       'crm:contract:auditList', 'Stamp', 2, 1, 1),
(4201, 42, '审核',       3, NULL, NULL,       'crm:contract:audit',      NULL, 1, 1, 1),
(4202, 42, '批量审核',   3, NULL, NULL,       'crm:contract:batchAudit', NULL, 2, 1, 1),

(43,   40, '合同详情',   2, '/crm/contract/:id',  'crm/contract/detail',      'crm:contract:list',      'Document', 9, 0, 1),

-- ---------------------------------------------------------------------------
--  产品（50）
-- ---------------------------------------------------------------------------
(51,   50, '产品',       2, '/crm/product'    ,           'crm/product/index',        'crm:product:list',         'Goods', 1, 1, 1),
(5101, 51, '新增产品',   3, NULL, NULL,          'crm:product:create',   NULL, 1, 1, 1),
(5102, 51, '修改产品',   3, NULL, NULL,          'crm:product:update',   NULL, 2, 1, 1),
(5103, 51, '删除产品',   3, NULL, NULL,          'crm:product:delete',   NULL, 3, 1, 1),
(5104, 51, '上架',       3, NULL, NULL,          'crm:product:onShelf',  NULL, 4, 1, 1),
(5105, 51, '下架',       3, NULL, NULL,          'crm:product:offShelf', NULL, 5, 1, 1),

(52,   50, '产品分类',   2, '/crm/product-category',  'crm/product/category',     'crm:productCategory:list', 'Menu', 2, 1, 1),
(5201, 52, '新增分类',   3, NULL, NULL,          'crm:productCategory:create', NULL, 1, 1, 1),
(5202, 52, '修改分类',   3, NULL, NULL,          'crm:productCategory:update', NULL, 2, 1, 1),
(5203, 52, '删除分类',   3, NULL, NULL,          'crm:productCategory:delete', NULL, 3, 1, 1),

(53,   50, '产品详情',   2, '/crm/product/:id',   'crm/product/detail',   'crm:product:list',   'Document', 9, 0, 1),
-- ---------------------------------------------------------------------------
--  财务（60）
-- ---------------------------------------------------------------------------
(61,   60, '回款登记',   2, '/finance/payment',        'finance/payment/index',      'fin:payment:list',      'Money', 1, 1, 1),
(6101, 61, '新增回款',   3, NULL, NULL,       'fin:payment:create',   NULL, 1, 1, 1),
(6102, 61, '修改回款',   3, NULL, NULL,       'fin:payment:update',   NULL, 2, 1, 1),
(6103, 61, '删除回款',   3, NULL, NULL,       'fin:payment:delete',   NULL, 3, 1, 1),
(6104, 61, '导出回款',   3, NULL, NULL,       'fin:payment:export',   NULL, 4, 1, 1),
(6105, 61, '提交审核',   3, NULL, NULL,       'fin:payment:submit',   NULL, 5, 1, 1),

(62,   60, '回款审核',   2, '/finance/payment-audit',  'finance/payment/audit',      'fin:payment:auditList', 'Stamp', 2, 1, 1),
(6201, 62, '审核',       3, NULL, NULL,       'fin:payment:audit',    NULL, 1, 1, 1),

(63,   60, '发票登记',   2, '/finance/invoice',        'finance/invoice/index',      'fin:invoice:list',      'Tickets', 3, 1, 1),
(6301, 63, '新增发票',   3, NULL, NULL,       'fin:invoice:create',   NULL, 1, 1, 1),
(6302, 63, '修改发票',   3, NULL, NULL,       'fin:invoice:update',   NULL, 2, 1, 1),
(6303, 63, '删除发票',   3, NULL, NULL,       'fin:invoice:delete',   NULL, 3, 1, 1),
(6304, 63, '导出发票',   3, NULL, NULL,       'fin:invoice:export',   NULL, 4, 1, 1),
(6305, 63, '提交审核',   3, NULL, NULL,       'fin:invoice:submit',   NULL, 5, 1, 1),

(64,   60, '财务开票',   2, '/finance/invoice-issue',  'finance/invoice/issue',      'fin:invoice:issueList', 'Stamp', 4, 1, 1),
(6401, 64, '开票',       3, NULL, NULL,       'fin:invoice:issue',    NULL, 1, 1, 1),
(6402, 64, '作废发票',   3, NULL, NULL,       'fin:invoice:void',     NULL, 2, 1, 1),

(65,   60, '回款详情',   2, '/finance/payment/:id',    'finance/payment/detail',     'fin:payment:list',      'Document', 9, 0, 1),
-- ⚠️ 66 是「发票登记」列表的跳转目标（前端 `detail-path="/finance/invoice/:id"`）。
--    路由是菜单驱动的，没有这一行则点发票编号跳详情会落到不存在的路由。
--    原先只存在于增量脚本 alter-2026-09-15-invoice-detail-menu.sql 里，
--    全量脚本漏了 —— 2026-09-15 补入。
(66,   60, '发票详情',   2, '/finance/invoice/:id',    'finance/invoice/detail',     'fin:invoice:list',      'Document', 10, 0, 1),

-- ---------------------------------------------------------------------------
--  系统设置（70）
-- ---------------------------------------------------------------------------
(71,   70, '组织架构',   2, '/system/dept'    ,           'system/dept/index',          'system:dept:list',      'OfficeBuilding', 1, 1, 1),
(7101, 71, '新增部门',   3, NULL, NULL,       'system:dept:create',    NULL, 1, 1, 1),
(7102, 71, '修改部门',   3, NULL, NULL,       'system:dept:update',    NULL, 2, 1, 1),
(7103, 71, '删除部门',   3, NULL, NULL,       'system:dept:delete',    NULL, 3, 1, 1),
(7104, 71, '设置部门负责人', 3, NULL, NULL,   'system:dept:setLeader', NULL, 4, 1, 1),

(72,   70, '人员管理',   2, '/system/user'    ,           'system/user/index',          'system:user:list',      'User', 2, 1, 1),
(7201, 72, '新增人员',   3, NULL, NULL,       'system:user:create',       NULL, 1, 1, 1),
(7202, 72, '修改人员',   3, NULL, NULL,       'system:user:update',       NULL, 2, 1, 1),
(7203, 72, '删除人员',   3, NULL, NULL,       'system:user:delete',       NULL, 3, 1, 1),
(7204, 72, '重置密码',   3, NULL, NULL,       'system:user:resetPwd',     NULL, 4, 1, 1),
(7205, 72, '启用/禁用',  3, NULL, NULL,       'system:user:changeStatus', NULL, 5, 1, 1),
(7206, 72, '操作离职',   3, NULL, NULL,       'system:user:resign',       NULL, 6, 1, 1),
(7207, 72, '导入人员',   3, NULL, NULL,       'system:user:import',       NULL, 7, 1, 1),
(7208, 72, '导出人员',   3, NULL, NULL,       'system:user:export',       NULL, 8, 1, 1),

(73,   70, '角色权限',   2, '/system/role'    ,           'system/role/index',          'system:role:list',      'Lock', 3, 1, 1),
(7301, 73, '新增角色',   3, NULL, NULL,       'system:role:create',            NULL, 1, 1, 1),
(7302, 73, '修改角色',   3, NULL, NULL,       'system:role:update',            NULL, 2, 1, 1),
(7303, 73, '删除角色',   3, NULL, NULL,       'system:role:delete',            NULL, 3, 1, 1),
(7304, 73, '权限配置',   3, NULL, NULL,       'system:role:assignPermission',  NULL, 4, 1, 1),
(7305, 73, '角色人员',   3, NULL, NULL,       'system:role:assignUser',        NULL, 5, 1, 1),
(7306, 73, '新增角色组', 3, NULL, NULL,       'system:roleGroup:create',       NULL, 6, 1, 1),
(7307, 73, '删除角色组', 3, NULL, NULL,       'system:roleGroup:delete',       NULL, 7, 1, 1),

(74,   70, '登陆日志',   2, '/system/login-log',      'system/log/login',           'system:loginLog:list',  'Clock', 4, 1, 1),
(7401, 74, '导出日志',   3, NULL, NULL,       'system:loginLog:export', NULL, 1, 1, 1),

(75,   70, '编号定义',   2, '/system/numbering-rule', 'system/numbering-rule/index', 'system:numberingRule:list', 'Postcard', 5, 1, 1),
(7501, 75, '新增编号规则', 3, NULL, NULL,     'system:numberingRule:create', NULL, 1, 1, 1),
(7502, 75, '修改编号规则', 3, NULL, NULL,     'system:numberingRule:update', NULL, 2, 1, 1),
(7503, 75, '删除编号规则', 3, NULL, NULL,     'system:numberingRule:delete', NULL, 3, 1, 1),

(76,   70, '计量单位',   2, '/system/unit'    ,           'system/unit/index',          'system:unit:list',      'ScaleToOriginal', 6, 1, 1),
(7601, 76, '新增单位',   3, NULL, NULL,       'system:unit:create', NULL, 1, 1, 1),
(7602, 76, '修改单位',   3, NULL, NULL,       'system:unit:update', NULL, 2, 1, 1),
(7603, 76, '删除单位',   3, NULL, NULL,       'system:unit:delete', NULL, 3, 1, 1),

-- 字典管理：原型未设此页面，但字典数据需要可视化维护，故补充
(78,   70, '字典管理',   2, '/system/dict'    ,           'system/dict/index',          'system:dict:list',      'Collection', 7, 1, 1),
(7801, 78, '新增字典',   3, NULL, NULL,       'system:dict:create', NULL, 1, 1, 1),
(7802, 78, '修改字典',   3, NULL, NULL,       'system:dict:update', NULL, 2, 1, 1),
(7803, 78, '删除字典',   3, NULL, NULL,       'system:dict:delete', NULL, 3, 1, 1),

-- 操作日志：不单独占菜单，由登陆日志页切 Tab 展示
(77,   70, '操作日志',   2, '/system/operation-log',  'system/log/operation',       'system:operationLog:list', 'Document', 9, 0, 1),

-- ---------------------------------------------------------------------------
--  AI 小助手（80）
--
--  这是一个【只承载权限点】的一级目录：唯一子节点是按钮 8001，没有页面、没有路由。
--
--  ⚠️ 新行必须插在块尾那个分号【之前】。scripts/gen-menu.js 是用
--     sql.indexOf 找【第一个】分号来截取 INSERT 块的，插到它后面就不会被解析到。
--     同理，下面这段注释里也**不能出现分号** —— 一旦出现，解析会在那里提前截断，
--     本行的 AI 行会被静默丢掉（构建不报错，只是菜单里少两项，极难发现）。
--
--  它不会在顶部导航里多出一个空菜单：前端 permissionStore.buildSidebarMenus
--  会把「子节点全被过滤掉」的目录整条剔除（按钮先被滤掉 → 目录没有可见子节点 → 目录也消失）。
--  导航里那个「AI小助手」由 TopNav 自己插入，点击是打开右下角浮层而不是跳路由。
--
--  ⚠️ 权限点必须写成三段式 ai:chat:use：超管的 *:*:* 在 Sa-Token 里只是普通通配模式，
--     模式中的冒号是字面量，只能命中三段式标识。写成两段式会让超管反而调不通（见 BUG-003）。
-- ---------------------------------------------------------------------------
(80,   0,  'AI小助手',      1, NULL, NULL, 'ai:chat',     'MagicStick', 9, 1, 1),
(8001, 80, '使用AI小助手',  3, NULL, NULL, 'ai:chat:use', NULL,         1, 1, 1);


-- =============================================================================
--  3.2 角色-菜单授权
--
--  说明：用 perms 精确匹配而非硬编码 menu_id —— 增删菜单时不用同步改这里。
--        目录节点的 perms 是两段式（如 crm:lead），菜单是三段式（crm:lead:list），
--        按钮的 action 各不相同，因此列表里要写全。
-- =============================================================================

-- ---------------------------------------------------------------------------
--  角色 1：超级管理员 —— 拥有全部菜单
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu`;

-- ---------------------------------------------------------------------------
--  角色 2：销售主管 —— 销售专员权限 + 分配/删除/导出 + 合同审核
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `perms` IN (
    -- 首页
    'dashboard:view',
    -- 线索
    'crm:lead', 'crm:lead:list', 'crm:lead:create', 'crm:lead:update', 'crm:lead:delete',
    'crm:lead:export', 'crm:lead:toPool', 'crm:lead:convert',
    'crm:leadPool', 'crm:leadPool:list', 'crm:leadPool:assign', 'crm:leadPool:claim', 'crm:leadPool:delete',
    -- 客户
    'crm:customer', 'crm:customer:list', 'crm:customer:create', 'crm:customer:update',
    'crm:customer:delete', 'crm:customer:export', 'crm:customer:toPool', 'crm:customer:share',
    'crm:customerPool', 'crm:customerPool:list', 'crm:customerPool:assign',
    'crm:customerPool:claim', 'crm:customerPool:delete',
    'crm:contact:create', 'crm:contact:update', 'crm:contact:delete', 'crm:invoiceInfo:update',
    'crm:poolRule:list', 'crm:poolRule:update',
    -- 商机
    'crm:opportunity', 'crm:opportunity:list', 'crm:opportunity:create', 'crm:opportunity:update',
    'crm:opportunity:delete', 'crm:opportunity:export', 'crm:opportunity:changeStage',
    'crm:opportunity:addProduct',
    'crm:quotation:list', 'crm:quotation:create', 'crm:quotation:update', 'crm:quotation:delete',
    -- 合同（含审核）
    'crm:contract', 'crm:contract:list', 'crm:contract:create', 'crm:contract:update',
    'crm:contract:delete', 'crm:contract:export', 'crm:contract:submit', 'crm:contract:void',
    'crm:contract:auditList', 'crm:contract:audit', 'crm:contract:batchAudit',
    -- 产品（只读）
    'crm:product', 'crm:product:list', 'crm:productCategory:list',
    -- 财务单据由销售登记并提交，审核/开票仍由财务负责
    'fin:payment', 'fin:payment:list', 'fin:payment:create', 'fin:payment:update',
    'fin:payment:delete', 'fin:payment:export', 'fin:payment:submit',
    'fin:invoice:list', 'fin:invoice:create', 'fin:invoice:update',
    'fin:invoice:delete', 'fin:invoice:export', 'fin:invoice:submit',
    -- AI 小助手（只读查询，天然受行级数据权限约束）
    'ai:chat', 'ai:chat:use'
);

-- ---------------------------------------------------------------------------
--  角色 3：销售专员 —— 只看自己 + 公海，无删除/审核/分配权
--  对应《权限设计文档》决策 1：销售不应看到他人私海数据
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `menu_id` FROM `sys_menu` WHERE `perms` IN (
    -- 首页
    'dashboard:view',
    -- 线索：可增改、可放入池、可领取、可转化，但【不可删除】
    'crm:lead', 'crm:lead:list', 'crm:lead:create', 'crm:lead:update',
    'crm:lead:toPool', 'crm:lead:convert',
    'crm:leadPool', 'crm:leadPool:list', 'crm:leadPool:claim',
    -- 客户：同上，【不可删除】【不可分配】
    'crm:customer', 'crm:customer:list', 'crm:customer:create', 'crm:customer:update',
    'crm:customer:toPool',
    'crm:customerPool', 'crm:customerPool:list', 'crm:customerPool:claim',
    'crm:contact:create', 'crm:contact:update',
    -- 商机
    'crm:opportunity', 'crm:opportunity:list', 'crm:opportunity:create', 'crm:opportunity:update',
    'crm:opportunity:changeStage', 'crm:opportunity:addProduct',
    'crm:quotation:list', 'crm:quotation:create', 'crm:quotation:update',
    -- 合同：可新建、可提审，【不可审核】【不可删除】
    'crm:contract', 'crm:contract:list', 'crm:contract:create', 'crm:contract:update',
    'crm:contract:submit',
    -- 产品（只读）
    'crm:product', 'crm:product:list', 'crm:productCategory:list',
    -- 财务单据由销售登记并提交；专员不拥有财务审核与开票权限
    'fin:payment', 'fin:payment:list', 'fin:payment:create', 'fin:payment:update',
    'fin:payment:submit',
    'fin:invoice:list', 'fin:invoice:create', 'fin:invoice:update', 'fin:invoice:submit',
    -- AI 小助手（只读查询，天然受行级数据权限约束）
    'ai:chat', 'ai:chat:use'
);

-- ---------------------------------------------------------------------------
--  角色 4：财务 —— 数据范围放开（data_scope=1），但菜单收紧
--  对应《权限设计文档》决策 3：财务可以看到客户信息
-- ---------------------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 4, `menu_id` FROM `sys_menu` WHERE `perms` IN (
    -- 首页
    'dashboard:view',
    -- 客户：决策 3 —— 财务能看客户（开票需要客户信息）
    'crm:customer', 'crm:customer:list', 'crm:customer:export',
    'crm:customerPool', 'crm:customerPool:list',
    'crm:invoiceInfo:update',
    -- 合同：开票要挂合同，需要能查；
    --       且财务【同时是合同审批流 CONTRACT_AUDIT 的第二级审批人】
    --       （sys_approval_node: approver_type=2 指定角色、approver_value=[4]），
    --       所以三个审核菜单必须给全 —— 少一个的后果不是"看不到按钮"，
    --       而是节点1 通过后【没有任何人能推进节点2】，合同审批流卡死，只能改库。
    --       ⚠️ 改这里之前先确认审批流里的角色审批人还是 4；
    --          审批流若换了角色，要同步改这里。
    --          存量库的对齐脚本（以审批流为派生依据）：
    --          sql/upgrade/alter-2026-09-15-contract-audit-finance-menu.sql
    'crm:contract', 'crm:contract:list', 'crm:contract:export',
    'crm:contract:auditList', 'crm:contract:audit', 'crm:contract:batchAudit',
    -- 回款：全流程含审核
    'fin:payment', 'fin:payment:list', 'fin:payment:create', 'fin:payment:update',
    'fin:payment:delete', 'fin:payment:export', 'fin:payment:submit',
    'fin:payment:auditList', 'fin:payment:audit',
    -- 发票：全流程含开票
    'fin:invoice', 'fin:invoice:list', 'fin:invoice:create', 'fin:invoice:update',
    'fin:invoice:delete', 'fin:invoice:export', 'fin:invoice:submit',
    'fin:invoice:issueList', 'fin:invoice:issue', 'fin:invoice:void',
    -- AI 小助手（只读查询，天然受行级数据权限约束）
    'ai:chat', 'ai:chat:use'
    -- 注意：财务【没有】线索、商机、产品的菜单权限
);


-- =============================================================================
--  3.3 校验
-- =============================================================================

SELECT '菜单总数' AS 项, COUNT(*) AS 值 FROM `sys_menu`
UNION ALL SELECT '  目录(type=1)', COUNT(*) FROM `sys_menu` WHERE `menu_type` = 1
UNION ALL SELECT '  菜单(type=2)', COUNT(*) FROM `sys_menu` WHERE `menu_type` = 2
UNION ALL SELECT '  按钮(type=3)', COUNT(*) FROM `sys_menu` WHERE `menu_type` = 3
UNION ALL SELECT '角色菜单关联总数', COUNT(*) FROM `sys_role_menu`
UNION ALL SELECT '  管理员(role=1)', COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 1
UNION ALL SELECT '  销售主管(role=2)', COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 2
UNION ALL SELECT '  销售专员(role=3)', COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 3
UNION ALL SELECT '  财务(role=4)', COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 4;

-- 孤立检查：有子节点被授权、但父节点没授权的（会导致前端建不出树）
SELECT '孤立节点(父未授权)' AS 项, COUNT(*) AS 值
  FROM `sys_role_menu` c
  JOIN `sys_menu` m ON m.`menu_id` = c.`menu_id`
 WHERE m.`parent_id` <> 0
   AND NOT EXISTS (
       SELECT 1 FROM `sys_role_menu` p
        WHERE p.`role_id` = c.`role_id` AND p.`menu_id` = m.`parent_id`
   );



-- #############################################################################
--  第四部分  演示账号与业务数据
--  ⚠️ 生产环境可删除本部分
-- #############################################################################

-- =============================================================================
--  4.1 用户与部门
--  密码统一为 123456（BCrypt strength=10，已回验）
-- =============================================================================

INSERT INTO `sys_user`
  (`user_id`, `username`, `password`, `real_name`, `nickname`, `dept_id`, `superior_id`, `phone`, `email`, `gender`, `status`, `remark`)
VALUES
(1, 'admin',     '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '超级管理员', 'Admin',  1,  NULL, '13800000001', 'admin@aurum.com',    1, 1, '系统内置管理员'),
(2, 'director',  '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '王总监',     '老王',   6,  1,    '13800000002', 'director@aurum.com', 1, 1, '销售部主管'),
(3, 'sales01',   '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '张三',       '小张',   2,  2,    '13800000003', 'sales01@aurum.com',  1, 1, '销售部 · 销售专员'),
(4, 'sales02',   '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '李四',       '小李',   2,  2,    '13800000004', 'sales02@aurum.com',  2, 1, '销售部 · 销售专员'),
(5, 'finance',   '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '赵财务',     '小赵',   3,  1,    '13800000005', 'finance@aurum.com',  2, 1, '财务专员'),
-- ↓ 以下为「让各部门都有数据」补的示例人员。密码同样是 123456。
--
--   ⚠️ **sales01 / sales02 刻意留在销售部(2)，没有下放到销售一组** ——
--      因为 `crm_customer` 等 7 张表里有 37 行业务数据的 `dept_id = 2`，
--      把它们的负责人挪到子部门会造成「负责人部门 ≠ 数据归属部门」的错位。
--      新增的销售人员才放进小组里。
--
--   ⚠️ 种子里只定义了 4 个角色（管理员/销售总监/销售专员/财务），
--      采购/质量/仓运/生产 这些岗位没有对应角色，这里统一先挂 `sales`（权限最小的普通员工角色）。
--      要更贴合的话，需要另建角色并补 sys_role_menu 授权 —— 那属于权限设计变更，不在示例数据范围内。
(6,  'sales03',    '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '王五',   '小王', 60, 2,    '13800000006', 'sales03@aurum.com',    1, 1, '销售一组 · 销售专员'),
(7,  'sales04',    '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '赵六',   '小赵', 61, 2,    '13800000007', 'sales04@aurum.com',    2, 1, '销售二组 · 销售专员'),
(8,  'sales05',    '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '孙七',   '小孙', 62, 2,    '13800000008', 'sales05@aurum.com',    1, 1, '销售三组 · 销售专员'),
(9,  'purchase01', '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '刘采购', '小刘', 5,  1,    '13800000009', 'purchase01@aurum.com', 1, 1, '采购部负责人'),
(10, 'purchase02', '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '陈采购', '小陈', 51, 9,    '13800000010', 'purchase02@aurum.com', 2, 1, '采购二组负责人'),
(11, 'quality01',  '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '吴质检', '小吴', 4,  1,    '13800000011', 'quality01@aurum.com',  1, 1, '质量部负责人'),
(12, 'warehouse01','$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '郑仓管', '小郑', 6,  1,    '13800000012', 'warehouse01@aurum.com',2, 1, '仓运部负责人'),
(13, 'produce01',  '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '冯生产', '小冯', 7,  1,    '13800000013', 'produce01@aurum.com',  1, 1, '生产部负责人'),
(14, 'finance02',  '$2a$10$lY9zC7thNJQ/NSvJGfqWpe4sD.RYEOsOpQGRQ.vypPv41OLEyCrES', '陈会计', '小陈', 3,  5,    '13800000014', 'finance02@aurum.com',  2, 1, '财务部 · 会计');

-- 一个用户一个角色（保持简单，便于验证权限边界）
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),   -- admin       -> 超级管理员
(2, 2),   -- director    -> 销售总监
(3, 3),   -- sales01     -> 销售专员
(4, 3),   -- sales02     -> 销售专员
(5, 4),   -- finance     -> 财务
(6, 3),   -- sales03     -> 销售专员
(7, 3),   -- sales04     -> 销售专员
(8, 3),   -- sales05     -> 销售专员
(9, 3),   -- purchase01  -> 销售专员（种子只有 4 个角色，非销售岗位暂挂它）
(10, 3),  -- purchase02  -> 销售专员（同上）
(11, 3),  -- quality01   -> 销售专员（同上）
(12, 3),  -- warehouse01 -> 销售专员（同上）
(13, 3),  -- produce01   -> 销售专员（同上）
(14, 4);  -- finance02   -> 财务

-- 部门负责人（对应原型「组织架构 - 部门负责人」）
-- ⚠️ 这些用户必须在上面 4.1 的 sys_user 里存在，改用户种子时要一起改
UPDATE `sys_dept` SET `leader_user_id` = 1  WHERE `dept_id` = 1;   -- 零捌集团
UPDATE `sys_dept` SET `leader_user_id` = 2  WHERE `dept_id` = 2;   -- 销售部
UPDATE `sys_dept` SET `leader_user_id` = 5  WHERE `dept_id` = 3;   -- 财务部
UPDATE `sys_dept` SET `leader_user_id` = 11 WHERE `dept_id` = 4;   -- 质量部
UPDATE `sys_dept` SET `leader_user_id` = 9  WHERE `dept_id` = 5;   -- 采购部
UPDATE `sys_dept` SET `leader_user_id` = 12 WHERE `dept_id` = 6;   -- 仓运部
UPDATE `sys_dept` SET `leader_user_id` = 13 WHERE `dept_id` = 7;   -- 生产部
UPDATE `sys_dept` SET `leader_user_id` = 6  WHERE `dept_id` = 60;  -- 销售一组
UPDATE `sys_dept` SET `leader_user_id` = 7  WHERE `dept_id` = 61;  -- 销售二组
UPDATE `sys_dept` SET `leader_user_id` = 8  WHERE `dept_id` = 62;  -- 销售三组
UPDATE `sys_dept` SET `leader_user_id` = 9  WHERE `dept_id` = 50;  -- 采购一组
UPDATE `sys_dept` SET `leader_user_id` = 10 WHERE `dept_id` = 51;  -- 采购二组


-- =============================================================================
--  4.2 产品分类与产品
-- =============================================================================

INSERT INTO `crm_product_category`
  (`category_id`, `parent_id`, `ancestors`, `category_name`, `sort`, `status`, `remark`)
VALUES
(1, 0, '0',   '软件产品', 1, 1, NULL),
(2, 0, '0',   '硬件设备', 2, 1, NULL),
(3, 0, '0',   '技术服务', 3, 1, NULL),
(4, 1, '0,1', 'CRM 套件', 1, 1, '软件产品下的二级分类');

INSERT INTO `crm_product`
  (`product_id`, `product_no`, `product_name`, `category_id`, `unit_id`, `price`, `cost_price`, `tax_rate`, `description`, `status`, `remark`)
VALUES
(1, 'CP20260001', 'CRM 标准版',      1, 3,  29800.00,  8000.00, 6.00, '面向中小团队的标准化 CRM',        1, NULL),
(2, 'CP20260002', 'CRM 企业版',      1, 3,  98000.00, 25000.00, 6.00, '支持私有化部署与多组织',          1, NULL),
(3, 'CP20260003', '数据看板模块',    1, 1,  15800.00,  4000.00, 6.00, '销售漏斗与业绩分析看板',          1, NULL),
(4, 'CP20260004', '服务器一体机',    2, 2,  45000.00, 28000.00, 13.00, '预装 CRM 的软硬一体设备',        1, NULL),
(5, 'CP20260005', '实施部署服务',    3, 5,  12000.00,  6000.00, 6.00, '上门实施与数据迁移',              1, NULL),
(6, 'CP20260006', '年度运维服务',    3, 4,  18000.00,  7000.00, 6.00, '7x24 运维与版本升级',             0, '已下架，用于演示上下架状态');


-- =============================================================================
--  4.3 客户与联系人
--
--  数据权限演示的核心：
--    客户 1-3 → sales01(3)
--    客户 4-6 → sales02(4)
--    客户 7-8 → 公海（owner_user_id = NULL）
-- =============================================================================

INSERT INTO `crm_customer`
  (`customer_id`, `customer_no`, `customer_name`, `industry`, `level`, `deal_status`, `source`, `phone`, `email`, `province`, `city`, `district`,
   `address`, `owner_user_id`, `dept_id`, `enter_pool_time`, `enter_pool_reason_code`, `enter_pool_remark`,
   `prev_owner_id`, `last_follow_time`, `remark`)
VALUES
-- sales01 名下
(1, 'KH20260001', '北京星辰科技有限公司', 2, 1, 2, 3, '13911000001', 'contact@xingchen.com', '北京市', '北京市', '海淀区',
 '中关村大街 1 号', 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), '重点客户'),
(2, 'KH20260002', '上海鸿远物流有限公司', 1, 2, 1, 2, '13911000002', 'info@hongyuan.com', '上海市', '上海市', '浦东新区',
 '世纪大道 100 号', 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL),
(3, 'KH20260003', '广州云图教育科技',     2, 2, 1, 5, '13911000003', 'hi@yuntu.com', '广东省', '广州市', '天河区',
 '珠江新城 8 号', 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),

-- sales02 名下
(4, 'KH20260004', '深圳前海金服集团',     3, 1, 2, 1, '13911000004', 'bd@qianhai.com', '广东省', '深圳市', '南山区',
 '前海路 88 号', 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), '金融行业标杆'),
(5, 'KH20260005', '杭州云栖医疗科技',     5, 2, 1, 4, '13911000005', 'med@yunqi.com', '浙江省', '杭州市', '西湖区',
 '文三路 200 号', 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL),
(6, 'KH20260006', '成都天府制造集团',     1, 3, 2, 6, '13911000006', 'sales@tianfu.com', '四川省', '成都市', '高新区',
 '天府大道 500 号', 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), NULL),

-- 公海客户（owner_user_id = NULL）—— 所有角色都能看到，可领取
(7, 'KH20260007', '武汉光谷信息技术',     2, 2, 1, 5, '13911000007', 'it@guanggu.com', '湖北省', '武汉市', '洪山区',
 '光谷大道 66 号', NULL, NULL, DATE_SUB(NOW(), INTERVAL 12 DAY), 4, '原负责人长期未跟进，规则自动回收', 4, DATE_SUB(NOW(), INTERVAL 45 DAY), '公海数据'),
(8, 'KH20260008', '西安丝路贸易有限公司', 1, 3, 1, 7, '13911000008', 'trade@silu.com', '陕西省', '西安市', '雁塔区',
 '高新路 9 号', NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), 2, '客户明确表示暂缓合作', 3, DATE_SUB(NOW(), INTERVAL 30 DAY), '公海数据');

INSERT INTO `crm_contact`
  (`contact_id`, `customer_id`, `contact_name`, `position`, `phone`, `telephone`, `email`, `gender`, `is_primary`, `remark`)
VALUES
(1,  1, '陈总',    '总经理',      '13811110001', '010-88880001', 'chen@xingchen.com', 1, 1, '决策人'),
(2,  1, '刘经理',  '技术经理',    '13811110002', NULL,           'liu@xingchen.com',  2, 0, NULL),
(3,  2, '孙主任',  '信息中心主任','13811110003', '021-66660001', 'sun@hongyuan.com',  1, 1, NULL),
(4,  3, '周老师',  '教务负责人',  '13811110004', NULL,           'zhou@yuntu.com',    1, 1, NULL),
(5,  4, '吴总',    '副总裁',      '13811110005', '0755-88880001','wu@qianhai.com',    1, 1, '决策人'),
(6,  4, '郑风控',  '风控总监',    '13811110006', NULL,           'zheng@qianhai.com', 2, 0, NULL),
(7,  5, '冯院长',  '副院长',      '13811110007', NULL,           'feng@yunqi.com',    1, 1, NULL),
(8,  6, '蒋厂长',  '生产厂长',    '13811110008', '028-88880001', 'jiang@tianfu.com',  1, 1, NULL),
(9,  7, '韩经理',  '采购经理',    '13811110009', NULL,           'han@guanggu.com',   2, 1, '公海客户联系人'),
(10, 8, '杨总',    '总经理',      '13811110010', NULL,           'yang@silu.com',     1, 1, '公海客户联系人');

-- 开票信息（对应原型「客户详情 - 开票信息」Tab）
INSERT INTO `crm_customer_invoice_info`
  (`info_id`, `customer_id`, `invoice_title`, `tax_no`, `bank_name`, `bank_account`, `invoice_addr`, `invoice_phone`, `is_default`)
VALUES
(1, 1, '北京星辰科技有限公司', '91110108MA01XXXX1A', '中国银行北京中关村支行', '123456789012345678', '北京市海淀区中关村大街 1 号', '010-88880001', 1),
(2, 4, '深圳前海金服集团有限公司', '91440300MA5FXXXX2B', '招商银行深圳前海支行', '987654321098765432', '深圳市南山区前海路 88 号', '0755-88880001', 1),
-- 客户 2 的开票信息：发票 #2 的快照就是照抄这一行（选客户自动带出的效果）
(3, 2, '上海鸿远物流有限公司', '91310115MA1KXXXX3C', '中国工商银行上海浦东支行', '6222021234567890123', '上海市浦东新区世纪大道 200 号', '021-58880001', 1);


-- =============================================================================
--  4.4 线索
--  6 条有负责人（私海），4 条无线索（线索池）
-- =============================================================================

INSERT INTO `crm_lead`
  (`lead_id`, `lead_no`, `lead_name`, `contact_name`, `position`, `phone`, `email`, `industry`, `source`,
   `province`, `city`, `district`, `address`,
   `owner_user_id`, `dept_id`, `enter_pool_time`, `enter_pool_reason_code`, `enter_pool_remark`,
   `prev_owner_id`, `last_follow_time`, `remark`)
VALUES
-- sales01 的私海线索
(1, 'XS20260001', '南京智联科技',     '钱总',  '总经理',   '13711110001', 'qian@zhilian.com', 2, 1, '江苏省', '南京市', '鼓楼区', '中山北路 10 号',
 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
(2, 'XS20260002', '天津海河建材',     '吴经理','采购经理', '13711110002', 'wu@haihe.com',    1, 3, '天津市', '天津市', '和平区', '南京路 20 号',
 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL),
(3, 'XS20260003', '青岛蓝海渔业',     '郑总',  '董事长',   '13711110003', 'zheng@lanhai.com',1, 4, '山东省', '青岛市', '市南区', '香港中路 30 号',
 3, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL),

-- sales02 的私海线索
(4, 'XS20260004', '重庆山城餐饮连锁', '冯总',  '创始人',   '13711110004', 'feng@shancheng.com', 7, 5, '重庆市', '重庆市', '渝中区', '解放碑 1 号',
 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
(5, 'XS20260005', '长沙湘江传媒',     '蒋主编','主编',     '13711110005', 'jiang@xiangjiang.com', 8, 6, '湖南省', '长沙市', '岳麓区', '麓山南路 5 号',
 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL),
(6, 'XS20260006', '福州闽江电子',     '韩工',  '技术总监', '13711110006', 'han@minjiang.com', 1, 2, '福建省', '福州市', '鼓楼区', '五四路 15 号',
 4, 2, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), NULL),

-- 线索池（owner_user_id = NULL）—— 任何销售都能「领取」
(7,  'XS20260007', '合肥科创园招商处', '沈主任','招商主任', '13711110007', 'shen@hefei.com',   6, 3, '安徽省', '合肥市', '蜀山区', '望江西路 100 号',
 NULL, NULL, DATE_SUB(NOW(), INTERVAL 18 DAY), 4, '规则自动回收：超过 15 天未跟进', 3, DATE_SUB(NOW(), INTERVAL 40 DAY), '线索池数据'),
(8,  'XS20260008', '昆明滇池旅游开发', '朱经理','市场经理', '13711110008', 'zhu@dianchi.com',  7, 1, '云南省', '昆明市', '西山区', '滇池路 66 号',
 NULL, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), 1, '原负责人主动放弃', 4, DATE_SUB(NOW(), INTERVAL 50 DAY), '线索池数据'),
(9,  'XS20260009', '哈尔滨冰城食品',   '施总',  '总经理',   '13711110009', 'shi@bingcheng.com',1, 5, '黑龙江省','哈尔滨市','道里区','中央大街 8 号',
 NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), 1, '原负责人长期未跟进', 4, DATE_SUB(NOW(), INTERVAL 25 DAY), '线索池数据'),
(10, 'XS20260010', '石家庄冀中药业',   '尤经理','商务经理', '13711110010', 'you@jizhong.com',  5, 4, '河北省', '石家庄市','裕华区','槐安东路 3 号',
 NULL, NULL, DATE_SUB(NOW(), INTERVAL 14 DAY), 2, '客户明确表示暂缓', 3, DATE_SUB(NOW(), INTERVAL 35 DAY), '线索池数据');


-- =============================================================================
--  4.5 商机与报价单
-- =============================================================================

INSERT INTO `crm_opportunity`
  (`opportunity_id`, `opportunity_no`, `opportunity_name`, `customer_id`, `contact_id`, `amount`, `stage`, `discount_rate`,
   `source`, `owner_user_id`, `dept_id`, `expected_close_date`, `actual_close_date`, `remark`)
VALUES
(1, 'SJ20260001', '星辰科技 CRM 标准版采购', 1, 1,  41800.00, 5, 100.00, 3, 3, 2, DATE_SUB(CURDATE(), INTERVAL 10 DAY), CURDATE(), '已赢单，已签合同'),
(2, 'SJ20260002', '鸿远物流企业版部署',     2, 3,  98000.00, 5, 100.00, 2, 3, 2, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY), '已赢单'),
(3, 'SJ20260003', '云图教育看板模块试点',   3, 4,  31600.00, 3,  60.00, 5, 3, 2, DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL, '方案报价中'),
(4, 'SJ20260004', '前海金服一体化方案',     4, 5,  63000.00, 4,  80.00, 1, 4, 2, DATE_ADD(CURDATE(), INTERVAL 5 DAY),  NULL, '商务谈判中'),
(5, 'SJ20260005', '云栖医疗数据看板',       5, 7,  15800.00, 2,  40.00, 4, 4, 2, DATE_ADD(CURDATE(), INTERVAL 30 DAY), NULL, '需求确认中'),
(6, 'SJ20260006', '天府制造年度运维续约',   6, 8,  18000.00, 6,   0.00, 6, 4, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY),  NULL, '已输单');

INSERT INTO `crm_opportunity_stage_log`
  (`log_id`, `opportunity_id`, `from_stage`, `to_stage`, `win_rate`, `change_reason`, `source`, `operator_id`, `create_time`)
VALUES
(1, 1, 1, 2, 20.00, '电话沟通确认需求', 1, 3, DATE_SUB(NOW(), INTERVAL 25 DAY)),
(2, 1, 3, 5, 100.00, '客户确认签约',     1, 3, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(3, 2, 1, 2, 30.00, '上门拜访',         1, 3, DATE_SUB(NOW(), INTERVAL 40 DAY)),
(4, 2, 4, 5, 100.00, '合同已签',         1, 3, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(5, 4, 3, 4, 80.00, '进入价格谈判',      1, 4, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ⚠️ status 的 5 个码值对外只显示 3 个标签（多对一归并，见《状态流转图》§1）：
--     0草稿/1已发出 → 报价中   2已接受 → 已生成合同   3已拒绝/4已作废 → 已失效
--    这里刻意让三个标签各有数据，三个筛选胶囊开箱都看得到东西。
--    注意 2 的语义就是「已生成合同」：下面 status = 2 的两条，都能在 4.6 的 crm_contract
--    里用 quotation_id 找到对应的合同（列表的「关联合同」列就是按它反查的）。
INSERT INTO `crm_quotation`
  (`quotation_id`, `quotation_no`, `quotation_name`, `opportunity_id`, `customer_id`, `version`,
   `total_amount`, `tax_amount`, `total_with_tax`, `valid_date`, `owner_user_id`, `dept_id`, `status`, `remark`)
VALUES
(1, 'BJ20260001', '星辰科技标准版报价', 1, 1, 1,  39433.96,  2366.04,  41800.00, DATE_ADD(CURDATE(), INTERVAL 20 DAY), 3, 2, 2, '客户已接受，已生成合同 HT20260001'),
(2, 'BJ20260002', '鸿远物流企业版报价', 2, 2, 1,  92452.83,  5547.17,  98000.00, DATE_ADD(CURDATE(), INTERVAL 15 DAY), 3, 2, 2, '客户已接受，已生成合同 HT20260002'),
-- 有合同 HT20260003（quotation_id = 3），所以状态必须是 2 而不是 1 ——
-- 否则列表上会出现「报价中」旁边挂着「关联合同」这种自相矛盾的行
(3, 'BJ20260003', '云图教育看板报价',   3, 3, 1,  29811.32,  1788.68,  31600.00, DATE_ADD(CURDATE(), INTERVAL 25 DAY), 3, 2, 2, '已生成合同 HT20260003'),
(4, 'BJ20260004', '前海金服一体化报价', 4, 4, 2,  59433.96,  3566.04,  63000.00, DATE_ADD(CURDATE(), INTERVAL 10 DAY), 4, 2, 1, '第二版报价，待客户确认'),
-- 已作废：用来演示「已失效」胶囊与「作废」动作的结果（唯一一条没有合同的失效报价）
(5, 'BJ20260005', '云图教育增补报价',   3, 3, 2,  17547.17,  1052.83,  18600.00, DATE_ADD(CURDATE(), INTERVAL 5 DAY),  3, 2, 4, '客户预算取消，报价作废');

-- 报价单明细（金额 = 数量 × 单价，税率 6%，逐行算税后汇总）
INSERT INTO `crm_quotation_item`
  (`item_id`, `quotation_id`, `product_id`, `product_name`, `product_type_id`, `unit_id`,
   `quantity`, `unit_price`, `discount_rate`, `tax_rate`, `amount`, `sort`)
VALUES
(1, 1, 1, 'CRM 标准版',     1, 3, 1.0000, 29800.00, 0.00, 6.00, 29800.00, 1),
(2, 1, 5, '实施部署服务',   3, 5, 1.0000, 12000.00, 0.00, 6.00, 12000.00, 2),
(3, 2, 2, 'CRM 企业版',     1, 3, 1.0000, 98000.00, 0.00, 6.00, 98000.00, 1),
(4, 3, 3, '数据看板模块',   1, 1, 2.0000, 15800.00, 0.00, 6.00, 31600.00, 1),
(5, 4, 4, '服务器一体机',   2, 2, 1.0000, 45000.00, 0.00,13.00, 45000.00, 1),
(6, 4, 6, '年度运维服务',   3, 4, 1.0000, 18000.00, 0.00, 6.00, 18000.00, 2),
(7, 5, 1, 'CRM 标准版',     1, 3, 2.0000,  6800.00, 0.00, 6.00, 13600.00, 1),
(8, 5, 5, '实施部署服务',   3, 5, 1.0000,  5000.00, 0.00, 6.00,  5000.00, 2);


-- =============================================================================
--  4.6 合同
--  金额必须等于明细汇总（应用层会校验，这里保证数据本身是对的）
--  覆盖四种审核状态，便于演示审批流
-- =============================================================================

INSERT INTO `crm_contract`
  (`contract_id`, `contract_no`, `contract_name`, `customer_id`, `opportunity_id`, `quotation_id`,
   `contract_type`, `amount`, `owner_user_id`, `dept_id`,
   `sign_date`, `start_date`, `end_date`, `customer_signer`, `company_signer`,
   `audit_status`, `status`, `remark`)
VALUES
-- 已通过 + 已生效（可继续演示开票、回款）
(1, 'HT20260001', '星辰科技 CRM 标准版采购合同', 1, 1, 1, 1, 41800.00, 3, 2,
 DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 355 DAY),
 '陈总', '王总监', 2, 1, '已生效'),

(2, 'HT20260002', '鸿远物流 CRM 企业版合同',     2, 2, 2, 1, 98000.00, 3, 2,
 DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_ADD(CURDATE(), INTERVAL 353 DAY),
 '孙主任', '王总监', 2, 1, '已生效'),

-- 审批中（演示「我的待办」）
(3, 'HT20260003', '云图教育数据看板合同',        3, 3, 3, 1, 31600.00, 3, 2,
 NULL, NULL, NULL, '周老师', '张三', 1, 0, '已提交，等待主管审批'),

-- 草稿（未提交）
(4, 'HT20260004', '前海金服一体化服务合同',      4, 4, 4, 1, 63000.00, 4, 2,
 NULL, NULL, NULL, NULL, NULL, 0, 0, '草稿，尚未提交审批');

INSERT INTO `crm_contract_item`
  (`item_id`, `contract_id`, `product_id`, `product_name`, `product_type_id`, `unit_id`,
   `quantity`, `unit_price`, `discount_rate`, `tax_rate`, `amount`, `sort`)
VALUES
(1, 1, 1, 'CRM 标准版',   1, 3, 1.0000, 29800.00, 0.00,  6.00, 29800.00, 1),
(2, 1, 5, '实施部署服务', 3, 5, 1.0000, 12000.00, 0.00,  6.00, 12000.00, 2),
(3, 2, 2, 'CRM 企业版',   1, 3, 1.0000, 98000.00, 0.00,  6.00, 98000.00, 1),
(4, 3, 3, '数据看板模块', 1, 1, 2.0000, 15800.00, 0.00,  6.00, 31600.00, 1),
(5, 4, 4, '服务器一体机', 2, 2, 1.0000, 45000.00, 0.00, 13.00, 45000.00, 1),
(6, 4, 6, '年度运维服务', 3, 4, 1.0000, 18000.00, 0.00,  6.00, 18000.00, 2);


-- =============================================================================
--  4.7 回款与发票
-- =============================================================================

INSERT INTO `fin_payment`
  (`payment_id`, `payment_no`, `contract_id`, `customer_id`, `owner_user_id`, `dept_id`,
   `amount`, `payment_date`, `payment_method`, `account_name`, `account_no`, `serial_no`,
   `audit_status`, `status`, `remark`)
VALUES
-- 已确认（财务已审核）
(1, 'HK20260001', 1, 1, 3, 2, 41800.00, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 1,
 '北京星辰科技有限公司', '123456789012345678', 'SN20260907001', 2, 1, '全额回款'),
(2, 'HK20260002', 2, 2, 3, 2, 50000.00, DATE_SUB(CURDATE(), INTERVAL 8 DAY), 1,
 '上海鸿远物流有限公司', '987654321098765432', 'SN20260904002', 2, 1, '首期款'),

-- 待审核（演示「财务回款审核」）—— 金额刻意留出余量：合同 2 只有 98000，
-- 三笔回款合计必须正好 98000（50000 + 18000 + 30000），否则回款登记页
-- 的「累计回款不能超过合同金额」校验会拒绝任何一次编辑。
(3, 'HK20260003', 2, 2, 3, 2, 18000.00, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1,
 '上海鸿远物流有限公司', '987654321098765432', 'SN20260910003', 1, 0, '尾款，待财务确认'),

-- 待提审（草稿）
(4, 'HK20260004', 4, 4, 4, 2, 15800.00, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 5,
 NULL, NULL, NULL, 0, 0, '销售自填，尚未提交'),

-- 已确认：回款 5 是「一票多回款」+「一票被多张发票分摊」两个演示的共用载体
--   发票 2 拿 12000、发票 3 拿 8000 → 合计 20000 < 30000，余 10000 仍可开票
(5, 'HK20260005', 2, 2, 3, 2, 30000.00, DATE_SUB(CURDATE(), INTERVAL 7 DAY), 1,
 '上海鸿远物流有限公司', '987654321098765432', 'SN20260905005', 2, 1, '二期款');

-- 发票信息快照列取自 crm_customer_invoice_info 的默认行（发票 #1 抄客户 1，发票 #2 抄客户 2）。
-- 发票 #3 的快照**故意留空**：它代表「快照机制上线前创建的存量发票」，编辑这类单据时
-- 六个字段应原样回显为空、不自动回填，直到用户重新选客户。
INSERT INTO `fin_invoice`
  (`invoice_id`, `apply_no`, `invoice_no`, `payment_id`, `contract_id`, `customer_id`, `owner_user_id`, `dept_id`,
   `invoice_type`, `invoice_title`, `tax_no`, `bank_name`, `bank_account`, `invoice_addr`, `invoice_phone`,
   `amount`, `tax_rate`, `tax_amount`, `total_amount`,
   `apply_date`, `issue_date`, `issue_user_id`, `audit_status`, `status`, `remark`)
VALUES
-- 已开票（invoice_no 已回填）—— 注意申请编号与实际发票号是两个字段
-- 单回款发票：明细见 fin_invoice_payment，write_off_amount = 41800.00
(1, 'FP20260001', '04512345', 1, 1, 1, 3, 2, 1,
 '北京星辰科技有限公司', '91110108MA01XXXX1A', '中国银行北京中关村支行', '123456789012345678',
 '北京市海淀区中关村大街 1 号', '010-88880001',
 '39433.96', 6.00,  2366.04,  41800.00,
 DATE_SUB(CURDATE(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 5, 2, 1, '已开票'),

-- 待开票（已审批通过，等财务开票）
-- ⚠️ 一票多回款演示：明细 = 回款 2 (50000) + 回款 5 (12000)，均含税 6%。
--    不含税**逐行** 用 HALF_UP 保留两位再汇总（不是先汇总再算税），否则
--    父子单据的 total = amount + tax 会差几分钱：
--      50000 → 47169.81 (税 2830.19)；12000 → 11320.75 (税 679.25)
--      ⇒ amount 58490.56、tax 3509.44、total 62000.00
--    payment_id 只存「第一条明细的回款」，明细本身以 fin_invoice_payment 为准。
(2, 'FP20260002', NULL, 2, 2, 2, 3, 2, 1,
 '上海鸿远物流有限公司', '91310115MA1KXXXX3C', '中国工商银行上海浦东支行', '6222021234567890123',
 '上海市浦东新区世纪大道 200 号', '021-58880001',
 '58490.56', 6.00,  3509.44,  62000.00,
 DATE_SUB(CURDATE(), INTERVAL 4 DAY), NULL, NULL, 2, 0, '已审批，待开票（合并两笔回款）'),

-- 审批中（演示「财务开票」审核队列）
-- 原指向回款 3（audit_status=1，未确认，按新校验不可开票），改与发票 2 共用回款 5，
-- 用它验证「跨发票的累计开票金额」这一条校验：12000 + 8000 = 20000 ≤ 30000。
(3, 'FP20260003', NULL, 5, 2, 2, 3, 2, 2,
 NULL, NULL, NULL, NULL, NULL, NULL,
 '7547.17', 6.00,  452.83,  8000.00,
 DATE_SUB(CURDATE(), INTERVAL 1 DAY), NULL, NULL, 1, 0, '部分开票，审批中');

-- 发票回款明细：唯一事实来源。每张发票的 total_amount 必须等于其明细之和，
-- 且每笔回款的累计 write_off_amount 不能超过该回款的 amount。
--   回款 5 被两张发票分摊（12000 + 8000 = 20000），余 10000 ——
--   这 10000 就是「新增发票登记」里该客户仍可选的那一行。
INSERT INTO `fin_invoice_payment`
  (`id`, `invoice_id`, `payment_id`, `write_off_amount`, `create_by`, `create_time`)
VALUES
(1, 1, 1, 41800.00, 3, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(2, 2, 2, 50000.00, 3, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(3, 2, 5, 12000.00, 3, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(4, 3, 5,  8000.00, 3, DATE_SUB(NOW(), INTERVAL 1 DAY));


-- =============================================================================
--  4.8 跟进记录（会刷新客户的 last_follow_time）
-- =============================================================================

INSERT INTO `crm_follow_up`
  (`followup_id`, `biz_type`, `biz_id`, `customer_id`, `contact_id`, `followup_type`,
   `content`, `result`, `next_follow_time`, `source`, `owner_user_id`, `dept_id`, `create_time`)
VALUES
(1, 2, 1, 1, 1, 1, '电话沟通，确认了部署范围与时间节点', '客户认可方案，约定下周签约', DATE_ADD(NOW(), INTERVAL 3 DAY),  1, 3, 2, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(2, 2, 1, 1, 2, 2, '上门做技术对接，梳理了历史数据迁移方案', '技术侧无阻塞',             DATE_ADD(NOW(), INTERVAL 7 DAY),  1, 3, 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, 2, 2, 2, 3, 3, '邮件发送企业版功能介绍与报价单',        '客户已内部评审',           NULL,                            1, 3, 2, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(4, 2, 3, 3, 4, 1, '电话回访，客户在评估看板模块的必要性',  '需要再提供两个同行业案例', DATE_ADD(NOW(), INTERVAL 2 DAY),  1, 3, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5, 2, 4, 4, 5, 2, '当面汇报一体化方案，风控部门提出合规要求','待补充合规说明材料',       DATE_ADD(NOW(), INTERVAL 4 DAY),  1, 4, 2, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(6, 2, 5, 5, 7, 1, '初次电话接触，了解现有系统痛点',        '约定下周做需求调研',       DATE_ADD(NOW(), INTERVAL 5 DAY),  1, 4, 2, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(7, 2, 6, 6, 8, 4, '微信沟通续约事宜',                      '客户表示预算收紧，暂不续约', NULL,                          1, 4, 2, DATE_SUB(NOW(), INTERVAL 20 DAY)),
-- 公海客户的最后跟进时间（回收规则据此判定）
(8, 2, 7, 7, 9, 1, '电话未接通，发送了短信',                '待再次联系',               NULL,                            1, 4, NULL, DATE_SUB(NOW(), INTERVAL 45 DAY)),
(9, 2, 8, 8, 10, 3, '邮件跟进合作意向',                     '客户回复暂缓',             NULL,                            1, 3, NULL, DATE_SUB(NOW(), INTERVAL 30 DAY));


-- =============================================================================
--  4.9 归属流转记录（线索池 / 公海的来龙去脉）
-- =============================================================================

INSERT INTO `crm_owner_change_log`
  (`log_id`, `biz_type`, `biz_id`, `biz_name`, `action`, `from_owner_id`, `to_owner_id`,
   `reason_code`, `reason_remark`, `operator_id`, `create_time`)
VALUES
(1, 2, 7, '武汉光谷信息技术',   4, 4,    NULL, 4, '超过 30 天未跟进，规则自动回收', NULL, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(2, 2, 8, '西安丝路贸易有限公司', 3, 3,    NULL, 2, '客户明确表示暂缓合作',           3,    DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, 1, 7, '合肥科创园招商处',   4, 3,    NULL, 4, '超过 15 天未跟进，规则自动回收', NULL, DATE_SUB(NOW(), INTERVAL 18 DAY)),
(4, 1, 8, '昆明滇池旅游开发',   3, 4,    NULL, 2, '原负责人主动放弃',               4,    DATE_SUB(NOW(), INTERVAL 20 DAY)),
(5, 1, 9, '哈尔滨冰城食品',     3, 4,    NULL, 1, '长期未跟进',                     4,    DATE_SUB(NOW(), INTERVAL 9 DAY)),
(6, 1, 10,'石家庄冀中药业',     3, 3,    NULL, 2, '客户明确表示暂缓',               3,    DATE_SUB(NOW(), INTERVAL 14 DAY)),
(7, 2, 1, '北京星辰科技有限公司', 1, NULL, 3, NULL, NULL, 2, DATE_SUB(NOW(), INTERVAL 30 DAY)),
(8, 2, 7, '武汉光谷信息技术',   6, 4,    3, NULL, '线索转化', 3, DATE_SUB(NOW(), INTERVAL 60 DAY));


-- =============================================================================
--  4.10 补齐审计字段 create_by
--
--  上面各业务表的 INSERT 都是**显式列出列名**的，而当时没有列 `create_by`
--  （它是 BaseDO 的审计字段，只在代码写入时由 DBFieldHandler 自动填充）。
--  后果是演示数据的「创建人」列永远是 NULL，前端列表显示成 `--` ——
--  而 LeadRespVO / OpportunityRespVO / QuotationRespVO / ContractRespVO /
--  ProductRespVO / ProductCategoryRespVO 都提供了 `creator` 字段、
--  Service 也都会从 create_by 跨表补名，**代码是齐的，缺的只是数据**。
--
--  用 UPDATE 统一补，而不是回头去改每个 INSERT 的列清单：
--  那些 INSERT 是多行长语句，逐行插一个值很容易和列对不齐，
--  而这里一句一条、一眼能看出改了什么。
--
--  ⚠️ 客户表没有 creator 字段（CustomerRespVO 不提供），这里一并补上只是
--     为了让 6 张业务表的审计字段口径一致，不影响任何接口输出。
-- =============================================================================

-- 有 Owner 的表：创建人取负责人，更贴近真实录入场景；
-- 线索池 / 公海里的行 owner 为 NULL，回退成超级管理员(1)。
UPDATE `crm_lead`             SET `create_by` = COALESCE(`owner_user_id`, 1) WHERE `create_by` IS NULL;
UPDATE `crm_customer`         SET `create_by` = COALESCE(`owner_user_id`, 1) WHERE `create_by` IS NULL;
UPDATE `crm_opportunity`      SET `create_by` = COALESCE(`owner_user_id`, 1) WHERE `create_by` IS NULL;
UPDATE `crm_quotation`        SET `create_by` = COALESCE(`owner_user_id`, 1) WHERE `create_by` IS NULL;
UPDATE `crm_contract`         SET `create_by` = COALESCE(`owner_user_id`, 1) WHERE `create_by` IS NULL;

-- 产品与产品分类没有归属人，统一记在超级管理员名下
UPDATE `crm_product`          SET `create_by` = 1 WHERE `create_by` IS NULL;
UPDATE `crm_product_category` SET `create_by` = 1 WHERE `create_by` IS NULL;


-- =============================================================================
--  4.11 客户的下次联系时间（首页看板「待回访客户」的数据来源）
--
--  判定口径（《05-接口文档》9.1）：**下次联系时间已过 且 未成交**。
--  新加的列在演示数据里是 NULL，那样看板的「待回访客户」永远是 0，看不出效果。
--
--  按 customer_id % 3 分三组给不同的过期天数，而不是用 INTERVAL 套表达式 ——
--  这里只用字面量，任何 MySQL 8 都能跑。留一组是「今天」（不算过期），
--  用来验证边界：`< CURDATE()` 不该把今天算成已过期。
-- =============================================================================

UPDATE `crm_customer` SET `next_contact_time` = DATE_SUB(CURDATE(), INTERVAL 2 DAY)
 WHERE `deal_status` = 1 AND MOD(`customer_id`, 3) = 0;
UPDATE `crm_customer` SET `next_contact_time` = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
 WHERE `deal_status` = 1 AND MOD(`customer_id`, 3) = 1;
UPDATE `crm_customer` SET `next_contact_time` = CURDATE()
 WHERE `deal_status` = 1 AND MOD(`customer_id`, 3) = 2;

-- 已成交客户给一个未来的时间，表示暂无回访压力；
-- 顺带覆盖「已成交即使过期也不算待回访」这条规则。
UPDATE `crm_customer` SET `next_contact_time` = DATE_ADD(CURDATE(), INTERVAL 30 DAY)
 WHERE `deal_status` = 2;


-- =============================================================================
--  4.12 系统消息通知（首页看板的「消息」与「公告 | 通知」）
--
--  sys_notice 建了表但一直没有种子数据，于是看板的这两个区块永远是空的。
--
--  ⚠️ notice_type 的语义：1审批提醒 2公海回收提醒 3系统公告 4任务提醒。
--     · 「消息」区块 = 审批待办（来自 sys_approval_task，不是这张表）+ 通知 + 公告
--     · 「公告 | 通知」区块 = 本表里 notice_type 为 2/3/4 的行
--     所以这里**不放 type=1**（审批提醒由审批任务本身表达，放这里会重复）。
--
--  接收人：1 admin / 5 finance。两人看到的条数不同，便于验证
--  「消息按接收人隔离」——这是本项目里少数几个不靠数据权限拦截器、
--  而是靠显式 receiver_id 条件保证可见性的表（它不在白名单里）。
-- =============================================================================

INSERT INTO `sys_notice`
  (`notice_id`, `title`, `content`, `notice_type`, `biz_type`, `biz_id`, `receiver_id`,
   `is_read`, `send_time`, `create_by`)
VALUES
(1, '关于调整客户公海回收规则的通知',       '回收周期由 60 天调整为 30 天，请及时跟进名下客户。', 2, 'customer', NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR),  1),
(2, '本月销售目标填报提醒',                 '请在本周五前完成本月销售目标填报。',                 4, NULL,       NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY),   1),
(3, '关于新增「产品类型」二级分类的公告',   '产品类型现已支持二级分类，历史数据已自动归档到一级分类。', 3, NULL,   NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY),   1),
(4, '系统维护公告',                         '系统将于本周日 02:00-04:00 维护，期间不可用。',       3, NULL,       NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 6 DAY),   1),
(5, '您有 1 笔回款待财务审核',              '客户「北京星辰科技」的回款申请等待审核。',           2, 'payment',  1,    5, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE), 1),
(6, '发票开具完成提醒',                     '申请单号 FP20260001 已完成开票，请核对发票号。',      4, 'invoice',  1,    5, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR),   1),
(7, '关于调整回款审核流程的公告',           '单笔 10 万元以上的回款需总监复核，即日生效。',        3, NULL,       NULL, 5, 1, DATE_SUB(NOW(), INTERVAL 2 DAY),   1),
-- ---------------------------------------------------------------------------
--  以下 20 条为测试数据（notice_id 8-27），用途：验证「我的消息」分页 / 已读 /
--  「公告 | 通知」列表与未读数。notice_type 仍避开 1（审批提醒走审批任务表）。
-- ---------------------------------------------------------------------------
(8,  '客户「武汉光谷信息技术」已进公海',      '您名下客户因 90 天未跟进已自动转入公海。',            2, 'customer', 7,    1, 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE), 1),
(9,  '客户「西安丝路贸易」已进公海',          '您名下客户因 90 天未跟进已自动转入公海。',            2, 'customer', 8,    1, 0, DATE_SUB(NOW(), INTERVAL 20 MINUTE), 1),
(10, '客户「武汉光谷信息技术」已进公海',      '您名下客户因 90 天未跟进已自动转入公海。',            2, 'customer', 7,    1, 0, DATE_SUB(NOW(), INTERVAL 35 MINUTE), 1),
(11, '本周重点客户跟进提醒',                  '「杭州云端」与「成都启点」本周需至少各跟进一次。',      4, NULL,       NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 3 HOUR),   1),
(12, '线索「合肥科创园招商处」待跟进',       '线索创建超过 7 天未跟进，请尽快联系。',              4, 'lead',     7,    1, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR),   1),
(13, '商机「云图教育看板模块试点」阶段更新提醒', '该商机预计本周内进入方案确认阶段。',                4, 'opportunity', 3,  1, 0, DATE_SUB(NOW(), INTERVAL 8 HOUR), 1),
(14, '合同「前海金服一体化服务合同」待提交审核', '合同当前为草稿状态，请及时提交审核。',              4, 'contract', 4,    1, 0, DATE_SUB(NOW(), INTERVAL 12 HOUR),  1),
(15, '您有 1 笔回款待提交审核',               '回款金额 58,000 元尚未提交财务审核。',               2, 'payment',  3,    5, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR),   1),
(16, '您有 1 笔回款待提交审核',               '回款金额 120,000 元尚未提交财务审核。',              2, 'payment',  4,    5, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR),   1),
(17, '发票申请待开票提醒',                    '申请单号 FP20260003 已进入开票队列。',                4, 'invoice',  2,    5, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR),   1),
(18, '发票申请待开票提醒',                    '申请单号 FP20260005 已进入开票队列。',                4, 'invoice',  3,    5, 0, DATE_SUB(NOW(), INTERVAL 9 HOUR),   1),
(19, '系统公告：新增「公海回收规则」配置页',  '公海回收规则已独立成页面，可按客户等级配置回收天数。', 3, NULL,       NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY),    1),
(20, '系统公告：新增「消息中心」入口',        '顶栏新增消息中心，可集中查看通知与未读待办。',        3, NULL,       NULL, 1, 0, DATE_SUB(NOW(), INTERVAL 1 DAY),    1),
(21, '系统公告：移动端即将上线',              '移动端 App 即将内测，支持客户与商机快速查看。',        3, NULL,       NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 4 DAY),    1),
(22, '系统公告：春节放假安排',                '2 月 9 日至 2 月 15 日放假，请提前处理待办。',         3, NULL,       NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 30 DAY),   1),
(23, '任务提醒：季度销售回款目标待完成',      '本季度回款目标完成度 62%，请加快回款进度。',           4, NULL,       NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 4 HOUR),   1),
(24, '任务提醒：本月新增客户目标待完成',      '本月新增客户目标 20 家，当前完成 13 家。',            4, NULL,       NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 7 HOUR),   1),
(25, '公海回收提醒：客户「武汉光谷信息技术」','该客户进入公海回收名单，可前往公海领取。',            2, 'customer', 7,    5, 0, DATE_SUB(NOW(), INTERVAL 40 MINUTE), 1),
(26, '公海回收提醒：客户「西安丝路贸易」',    '该客户进入公海回收名单，可前往公海领取。',            2, 'customer', 8,    5, 0, DATE_SUB(NOW(), INTERVAL 50 MINUTE), 1),
(27, '测试：全员公告（无定向接收人）',        '这是一条全员可见的公告，用于验证 receiver_id IS NULL 的全员口径。', 3, NULL, NULL, NULL, 0, DATE_SUB(NOW(), INTERVAL 5 MINUTE), 1);


-- =============================================================================
--  4.13 校验
-- =============================================================================

SELECT '=== 用户 ===' AS '';
SELECT u.user_id, u.username, u.real_name, d.dept_name, r.role_name,
       CASE r.data_scope WHEN 1 THEN '全部' WHEN 3 THEN '本部门'
                         WHEN 4 THEN '本部门及以下' WHEN 5 THEN '仅本人' END AS 数据范围
FROM sys_user u
LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
LEFT JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.del_flag = 0 ORDER BY u.user_id;

SELECT '=== 各角色能看到的客户（模拟数据权限）===' AS '';
SELECT 'sales01 (张三, 仅本人+公海)' AS 角色, GROUP_CONCAT(customer_name ORDER BY customer_id SEPARATOR ' | ') AS 可见客户
FROM crm_customer WHERE del_flag = 0 AND (owner_user_id = 3 OR owner_user_id IS NULL)
UNION ALL
SELECT 'sales02 (李四, 仅本人+公海)', GROUP_CONCAT(customer_name ORDER BY customer_id SEPARATOR ' | ')
FROM crm_customer WHERE del_flag = 0 AND (owner_user_id = 4 OR owner_user_id IS NULL)
UNION ALL
SELECT 'director (销售部及以下+公海)', GROUP_CONCAT(customer_name ORDER BY customer_id SEPARATOR ' | ')
FROM crm_customer WHERE del_flag = 0 AND (dept_id = 2 OR owner_user_id IS NULL);

SELECT '=== 合同金额与明细汇总是否一致 ===' AS '';
SELECT c.contract_no, c.contract_name, c.amount AS 合同金额,
       IFNULL(SUM(i.amount), 0) AS 明细汇总,
       IF(c.amount = IFNULL(SUM(i.amount), 0), '✓ 一致', '✗ 不一致') AS 校验
FROM crm_contract c LEFT JOIN crm_contract_item i ON i.contract_id = c.contract_id AND i.del_flag = 0
WHERE c.del_flag = 0 GROUP BY c.contract_id ORDER BY c.contract_id;

SELECT '=== 各表数据量 ===' AS '';
SELECT '用户' AS 表名, COUNT(*) AS 行数 FROM sys_user WHERE del_flag = 0
UNION ALL SELECT '产品分类', COUNT(*) FROM crm_product_category WHERE del_flag = 0
UNION ALL SELECT '产品',     COUNT(*) FROM crm_product WHERE del_flag = 0
UNION ALL SELECT '客户',     COUNT(*) FROM crm_customer WHERE del_flag = 0
UNION ALL SELECT '  └ 公海客户', COUNT(*) FROM crm_customer WHERE del_flag = 0 AND owner_user_id IS NULL
UNION ALL SELECT '联系人',   COUNT(*) FROM crm_contact WHERE del_flag = 0
UNION ALL SELECT '线索',     COUNT(*) FROM crm_lead WHERE del_flag = 0
UNION ALL SELECT '  └ 线索池', COUNT(*) FROM crm_lead WHERE del_flag = 0 AND owner_user_id IS NULL
UNION ALL SELECT '商机',     COUNT(*) FROM crm_opportunity WHERE del_flag = 0
UNION ALL SELECT '报价单',   COUNT(*) FROM crm_quotation WHERE del_flag = 0
UNION ALL SELECT '合同',     COUNT(*) FROM crm_contract WHERE del_flag = 0
UNION ALL SELECT '回款',     COUNT(*) FROM fin_payment WHERE del_flag = 0
UNION ALL SELECT '发票',     COUNT(*) FROM fin_invoice WHERE del_flag = 0
UNION ALL SELECT '跟进记录', COUNT(*) FROM crm_follow_up WHERE del_flag = 0
UNION ALL SELECT '流转记录', COUNT(*) FROM crm_owner_change_log;

SELECT '=== 创建人是否已补齐（期望全为 0）===' AS '';
SELECT '线索' AS 表名, SUM(create_by IS NULL) AS 创建人为空 FROM crm_lead
UNION ALL SELECT '客户',     SUM(create_by IS NULL) FROM crm_customer
UNION ALL SELECT '商机',     SUM(create_by IS NULL) FROM crm_opportunity
UNION ALL SELECT '报价单',   SUM(create_by IS NULL) FROM crm_quotation
UNION ALL SELECT '合同',     SUM(create_by IS NULL) FROM crm_contract
UNION ALL SELECT '产品',     SUM(create_by IS NULL) FROM crm_product
UNION ALL SELECT '产品分类', SUM(create_by IS NULL) FROM crm_product_category;

SELECT '=== 看板「待回访客户」（下次联系时间已过 且 未成交，期望 > 0）===' AS '';
SELECT COUNT(*) AS 待回访客户 FROM crm_customer
 WHERE del_flag = 0 AND deal_status = 1
   AND next_contact_time IS NOT NULL AND next_contact_time < CURDATE();

SELECT '=== 看板「公告 | 通知」按接收人分布（两人条数应不同）===' AS '';
SELECT u.real_name AS 接收人, COUNT(*) AS 条数, SUM(n.is_read = 0) AS 未读
FROM sys_notice n JOIN sys_user u ON u.user_id = n.receiver_id
WHERE n.del_flag = 0 GROUP BY n.receiver_id, u.real_name ORDER BY n.receiver_id;


-- =============================================================================
--  脚本结束
--  49 张表 · 14 个演示账号（密码 123456）
-- =============================================================================
