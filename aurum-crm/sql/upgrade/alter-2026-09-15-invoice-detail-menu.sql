-- =============================================================================
--  发票详情页的隐藏菜单路由
--
--  背景：
--    前端的业务路由**全部由后端菜单树生成**（store/permission.js：只有 menuType=2
--    且有 path 的节点才产生路由）。所以「发票详情」这个页面要能被导航到，
--    必须在 sys_menu 里有一行 —— 不能只写前端代码。
--
--    `visible = 0` 的约定（permission.js:99-100 已明确）：
--      可路由，但不进侧边栏。详情页都是这么挂的。
--
--  为什么单独一张表、一条菜单：
--    与「回款详情」完全对称 —— crm.sql:1780 那行就是回款详情的隐藏路由，
--    本脚本照抄它，只把 id / 名称 / path / 组件 / 权限码换成发票的。
--    权限码用 `fin:invoice:list`（读权限）：能看发票列表的人就该能看详情，
--    不需要新增权限点，也不动角色授权种子里任何已有权限。
--
--  受影响范围：仅新增 1 行 sys_menu（menu_id=66）+ 若干 sys_role_menu 授权行。
--              不改任何已有菜单、不删任何数据。
--
--  幂等：menu 用 INSERT IGNORE（menu_id 是主键）；
--        角色授权用 INSERT IGNORE（sys_role_menu 主键是 (role_id, menu_id)）。
--        重复执行不会报错、不会重复授权。
--
--  回滚：
--    DELETE FROM `sys_role_menu` WHERE `menu_id` = 66;
--    DELETE FROM `sys_menu`      WHERE `menu_id` = 66;
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) 新增「发票详情」隐藏菜单（menu_type=2 菜单，visible=0 隐藏）
--
--    参数逐项对照 crm.sql:1780 的「回款详情」：
--      parent_id = 60（财务目录）   sort = 10（紧接回款详情 9 之后）
--      path = '/finance/invoice/:id'  —— 与回款详情同款，:id 由前端列表替换
--      component = 'finance/invoice/detail' → views/finance/invoice/detail.vue
--      visible = 0 隐藏（不占侧边栏），status = 1 正常
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`)
VALUES
  (66, 60, '发票详情', 2, '/finance/invoice/:id', 'finance/invoice/detail', 'fin:invoice:list', 'Document', 10, 0, 1);


-- -----------------------------------------------------------------------------
-- 2) 给「已经有发票登记菜单的角色」补上详情页授权
--
--    不复刻 crm.sql 里那份 `WHERE perms IN (...)` 的角色清单 —— 那份清单是
--    种子数据，线上库的角色授权可能已经被人在界面上改过。以**现状**为准更稳：
--    谁能看「发票登记」(menu_id=63)，就给谁看「发票详情」(menu_id=66)。
--
--    子查询包一层派生表 t：MySQL 对「INSERT 目标表同时出现在子查询里」会报
--    1093，派生表是标准绕法。（本来 INSERT...SELECT FROM 同表也行，
--    但包一层在所有 MySQL 版本上都确定能过。）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT t.`role_id`, 66
  FROM (SELECT DISTINCT `role_id` FROM `sys_role_menu` WHERE `menu_id` = 63) t;


-- -----------------------------------------------------------------------------
-- 3) 校验
-- -----------------------------------------------------------------------------

-- 3.1 菜单行已就位（1 行：发票详情, visible=0, perms=fin:invoice:list）
--   SELECT menu_id, parent_id, menu_name, menu_type, path, component, perms, sort, visible, status
--     FROM `sys_menu` WHERE `menu_id` = 66;

-- 3.2 授权已铺开（应列出所有能看发票登记的角色）
--   SELECT rm.role_id, r.role_name, rm.menu_id
--     FROM `sys_role_menu` rm
--     LEFT JOIN `sys_role` r ON r.role_id = rm.role_id
--    WHERE rm.menu_id = 66 ORDER BY rm.role_id;

-- 3.3 对比：看得到发票登记的角色，都应该同时看得到发票详情
--     （两条查询的结果 role_id 集合应完全一致；差异为空才是对的）
--   SELECT role_id, 'has 63 only' AS diff FROM `sys_role_menu` WHERE menu_id = 63
--    EXCEPT SELECT role_id, 'has 63 only' FROM `sys_role_menu` WHERE menu_id = 66;
--     注：MySQL 8.0.31+ 才支持 EXCEPT；不支持就用下面的写法——
--   SELECT a.role_id FROM `sys_role_menu` a
--    WHERE a.menu_id = 63
--      AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` b WHERE b.role_id = a.role_id AND b.menu_id = 66);
--   期望：0 行。

-- 3.4 接口层判据：登录后 GET /auth/me（或菜单树接口）返回的菜单里应含
--     path = '/finance/invoice/:id' 的节点（visible=0）。前端 store/permission.js
--     会把它注册成 hidden 路由，点发票申请编号即可跳转。
--
--     ⚠️ 改完菜单要**重新登录**（或刷新页面重新拉菜单树）才会生效 ——
--        动态路由只在登录时生成一次。
