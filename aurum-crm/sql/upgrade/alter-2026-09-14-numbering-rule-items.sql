-- =============================================================================
-- 存量库升级：编号定义完整结构（编号项、分组计数器、菜单权限）
-- 运行方式：已有数据库只执行本脚本；不要用 sql/crm.sql 重建存量库。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_numbering_rule_item` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统-编号规则项';

CREATE TABLE IF NOT EXISTS `sys_numbering_counter` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统-编号分组流水计数器';

-- 为没有明细的存量规则补入原型默认节点；已有配置不会被覆盖。
INSERT INTO `sys_numbering_rule_item`
  (`item_id`, `rule_id`, `item_type`, `item_value`, `item_length`, `sort`, `sequence_basis`)
SELECT UUID_SHORT(), r.rule_id, d.item_type,
       CASE WHEN d.item_type = 'FIXED_PREFIX' THEN r.prefix ELSE NULL END,
       CASE WHEN d.item_type = 'SERIAL' THEN r.seq_length ELSE NULL END,
       d.item_sort,
       CASE
         WHEN d.item_type = 'SERIAL' THEN 1
         WHEN d.item_type = 'YEAR' AND r.reset_cycle = 3 THEN 1
         WHEN d.item_type = 'MONTH' AND r.reset_cycle = 2 THEN 1
         WHEN d.item_type = 'DAY' AND r.reset_cycle = 1 THEN 1
         ELSE 0
       END
  FROM sys_numbering_rule r
  CROSS JOIN (
    SELECT 'FIXED_PREFIX' AS item_type, 0 AS item_sort
    UNION ALL SELECT 'YEAR', 1
    UNION ALL SELECT 'MONTH', 2
    UNION ALL SELECT 'DAY', 3
    UNION ALL SELECT 'SERIAL', 4
  ) d
 WHERE r.del_flag = 0
   AND r.rule_code IN ('LEAD','CUSTOMER','OPP','QUOTE','CONTRACT','PAYMENT','INVOICE')
   AND NOT EXISTS (
     SELECT 1 FROM sys_numbering_rule_item i
      WHERE i.rule_id = r.rule_id AND i.del_flag = 0
   );

-- =============================================================================
-- 编号定义菜单迁移
-- =============================================================================
-- 编号定义必须挂在「组织架构」（menu_id=70）下面。
-- 这部分用于补齐已经存在的存量库，不会覆盖业务单据数据。

UPDATE `sys_menu`
   SET `parent_id` = 70,
       `menu_name` = '编号定义',
       `menu_type` = 2,
       `path` = '/system/numbering-rule',
       `component` = 'system/numbering-rule/index',
       `perms` = 'system:numberingRule:list',
       `icon` = 'Postcard',
       `sort` = 5,
       `visible` = 1,
       `status` = 1,
       `del_flag` = 0
 WHERE `menu_id` = 75;

INSERT INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`,
   `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 75, 70, '编号定义', 2, '/system/numbering-rule',
       'system/numbering-rule/index', 'system:numberingRule:list',
       'Postcard', 5, 1, 1
 WHERE EXISTS (
       SELECT 1 FROM `sys_menu`
        WHERE `menu_id` = 70
          AND `menu_name` = '组织架构'
          AND `del_flag` = 0
   )
   AND NOT EXISTS (
       SELECT 1 FROM `sys_menu`
        WHERE `menu_id` = 75
   );

UPDATE `sys_menu`
   SET `parent_id` = 75,
       `menu_name` = '新增编号规则',
       `menu_type` = 3,
       `path` = NULL,
       `component` = NULL,
       `perms` = 'system:numberingRule:create',
       `icon` = NULL,
       `sort` = 1,
       `visible` = 1,
       `status` = 1,
       `del_flag` = 0
 WHERE `menu_id` = 7501;

UPDATE `sys_menu`
   SET `parent_id` = 75,
       `menu_name` = '修改编号规则',
       `menu_type` = 3,
       `path` = NULL,
       `component` = NULL,
       `perms` = 'system:numberingRule:update',
       `icon` = NULL,
       `sort` = 2,
       `visible` = 1,
       `status` = 1,
       `del_flag` = 0
 WHERE `menu_id` = 7502;

UPDATE `sys_menu`
   SET `parent_id` = 75,
       `menu_name` = '删除编号规则',
       `menu_type` = 3,
       `path` = NULL,
       `component` = NULL,
       `perms` = 'system:numberingRule:delete',
       `icon` = NULL,
       `sort` = 3,
       `visible` = 1,
       `status` = 1,
       `del_flag` = 0
 WHERE `menu_id` = 7503;

INSERT INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`,
   `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 7501, 75, '新增编号规则', 3, NULL, NULL,
       'system:numberingRule:create', NULL, 1, 1, 1
 WHERE EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 75)
   AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 7501);

INSERT INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`,
   `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 7502, 75, '修改编号规则', 3, NULL, NULL,
       'system:numberingRule:update', NULL, 2, 1, 1
 WHERE EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 75)
   AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 7502);

INSERT INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`,
   `perms`, `icon`, `sort`, `visible`, `status`)
SELECT 7503, 75, '删除编号规则', 3, NULL, NULL,
       'system:numberingRule:delete', NULL, 3, 1, 1
 WHERE EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 75)
   AND NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 7503);

-- 超级管理员补齐编号定义菜单及三个操作权限。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, ids.menu_id
  FROM (
    SELECT 75 AS menu_id
    UNION ALL SELECT 7501
    UNION ALL SELECT 7502
    UNION ALL SELECT 7503
  ) ids
 WHERE NOT EXISTS (
       SELECT 1 FROM `sys_role_menu` rm
        WHERE rm.`role_id` = 1
          AND rm.`menu_id` = ids.menu_id
   );

SELECT `menu_id`, `parent_id`, `menu_name`, `path`, `perms`
  FROM `sys_menu`
 WHERE `menu_id` IN (70, 75, 7501, 7502, 7503)
 ORDER BY `menu_id`;
