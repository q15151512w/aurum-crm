-- =============================================================================
--  存量库升级脚本：补两条「详情页」菜单（商机报价单详情 / 产品详情）
--
--  背景：前端路由是【菜单驱动】的 —— 只有 sys_menu 里 menu_type=2 且有 path 的行
--        才会被 store/permission.js 转成路由（visible=0 的详情页照样产生路由，
--        visible 只决定它进不进侧边栏）。
--        这两条菜单行已经写进 sql/crm.sql（提交 926d28d），但存量库没有同步，
--        于是运行时压根没有 /crm/quotation/:id、/crm/product/:id 两条路由，
--        点列表里的「报价单名称 / 产品名称」会落到兜底路由 /:pathMatch(.*)* → 404 页。
--        本脚本把库与 crm.sql、文档对齐。
--
--  症状与排查（给下次再撞到的人）：
--        详情页 404 但页面文件与后端接口都正常 → 先看 /auth/me 的菜单树里有没有
--        对应 path，再比对 sys_menu 与 sql/crm.sql 的 menu_id 差集。
--
--  ⚠️ 什么时候跑这个脚本：
--      - 存量库（本地 / 远程联调 / 测试 / 生产）→ **执行本脚本**，然后前端整页刷新；
--      - 全新的库 → 直接跑 sql/crm.sql 即可（它已含这两行），再跑本脚本是无操作。
--
--  幂等性：menu_id 是主键。菜单 INSERT 用 ON DUPLICATE KEY UPDATE menu_id = menu_id
--          （主键冲突时什么都不做），**刻意不用 INSERT IGNORE** —— 那会把「列数不匹配」
--          之类的真错误一起吞掉，正是本项目反复记过的「脚本静默失效」坑。
--          授权部分用 INSERT IGNORE，靠 (role_id, menu_id) 复合主键幂等。
--
--  生效方式：不用改代码、不用重启后端、不用重新构建。前端刷新页面即可生效
--            （动态路由在每次整页加载时从 /auth/me 现取）。
--
--  影响面：只新增 2 行菜单 + 若干授权行，不修改、不删除任何既有数据。
-- =============================================================================

-- -----------------------------------------------------------------------------
--  0. 前置校验：下面这条应返回 0 行（若已有，说明本脚本已经跑过或库是最新的）
-- -----------------------------------------------------------------------------
SELECT `menu_id`, `menu_name`, `path`
  FROM `sys_menu`
 WHERE `menu_id` IN (34, 53);

-- -----------------------------------------------------------------------------
--  1. 补两条菜单（取值与 sql/crm.sql 的 sys_menu 种子完全一致）
--     perms 与各自的列表菜单相同 —— 详情页不新增权限点，「能看列表就能看详情」
-- -----------------------------------------------------------------------------
INSERT INTO `sys_menu`
    (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
VALUES
    (34, 30, '报价单详情', 2, '/crm/quotation/:id', 'crm/quotation/detail', 'crm:quotation:list', 'Document', 9, 0, 1),
    (53, 50, '产品详情',   2, '/crm/product/:id',   'crm/product/detail',   'crm:product:list',   'Document', 9, 0, 1)
ON DUPLICATE KEY UPDATE `menu_id` = `menu_id`;

-- -----------------------------------------------------------------------------
--  2. 角色授权：谁能看列表，谁就能看详情
--     与种子里「按 perms 精确匹配」的授权口径一致（不硬编码 role_id）：
--     从现有授权里反查「已经拥有该列表菜单的角色」，把菜单行的可见性对齐过去。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 34
  FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
 WHERE m.`perms` = 'crm:quotation:list';

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, 53
  FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
 WHERE m.`perms` = 'crm:product:list';

-- -----------------------------------------------------------------------------
--  3. 校验：期望 7 条详情菜单（13 线索 / 23 客户 / 32 商机 / 34 报价单 / 43 合同 / 53 产品 / 65 回款）
-- -----------------------------------------------------------------------------
SELECT `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `visible`, `status`
  FROM `sys_menu`
 WHERE `path` LIKE '%:id%'
 ORDER BY `menu_id`;

-- 期望 130 行（8 目录 + 31 菜单 + 91 按钮）
SELECT `menu_type`, COUNT(*) AS `cnt` FROM `sys_menu` GROUP BY `menu_type`;
SELECT COUNT(*) AS `total` FROM `sys_menu`;

-- 本次两条菜单的授权情况（应与 32 / 43 等既有详情菜单的角色一致）
SELECT rm.`menu_id`, m.`menu_name`, GROUP_CONCAT(rm.`role_id` ORDER BY rm.`role_id`) AS `roles`
  FROM `sys_role_menu` rm
  JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
 WHERE rm.`menu_id` IN (34, 53)
 GROUP BY rm.`menu_id`, m.`menu_name`;

-- -----------------------------------------------------------------------------
--  4. 回滚（只在确实要撤销时执行；撤回后前端会重新变回 404）
-- -----------------------------------------------------------------------------
-- DELETE FROM `sys_role_menu` WHERE `menu_id` IN (34, 53);
-- DELETE FROM `sys_menu`      WHERE `menu_id` IN (34, 53);
