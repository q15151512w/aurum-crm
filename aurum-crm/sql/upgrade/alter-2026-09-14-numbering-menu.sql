-- =============================================================================
-- 存量库升级：将「编号定义」归入「组织架构」菜单
-- 运行方式：已有数据库按需执行；不会重建表，也不会修改业务单据数据。
-- =============================================================================

-- 现有菜单若已存在，统一校正为：
-- 组织架构（menu_id=70） -> 编号定义（menu_id=75）
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

-- 菜单不存在时补齐。前提是存量库已有 menu_id=70 的「组织架构」目录。
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

-- 三个页面操作权限：新增、修改、删除。
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

-- 超级管理员补齐入口及操作权限；其他角色仍按原权限设计，不自动放开。
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

-- 执行后检查：75 的 parent_id 应为 70，且管理员拥有 75/7501/7502/7503。
SELECT `menu_id`, `parent_id`, `menu_name`, `path`, `perms`
  FROM `sys_menu`
 WHERE `menu_id` IN (70, 75, 7501, 7502, 7503)
 ORDER BY `menu_id`;
