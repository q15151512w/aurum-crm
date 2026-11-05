-- =============================================================================
--  给「合同审批流里的角色审批人」补上合同审核菜单
--
--  背景（这是一个断链，不是新需求）：
--
--    合同审批流 CONTRACT_AUDIT 是两级，定义见 crm.sql:1536-1541：
--      节点1「部门主管审批」 approver_type=4（发起人直属上级）
--      节点2「财务审批」     approver_type=2（指定角色）approver_value=[4]
--
--    节点1 能走通：角色 2（销售总监）在 crm.sql:1900 拿到了完整的三个权限码
--      crm:contract:auditList / crm:contract:audit / crm:contract:batchAudit
--
--    节点2 走不通：角色 4（财务）在 crm.sql:1959 只拿到了
--      'crm:contract', 'crm:contract:list', 'crm:contract:export'
--    —— 当初写角色授权时只想着「开票要挂合同，财务得能查」，
--       漏了「财务同时还是合同审批流的第二级审批人」。
--
--    后果：节点1 通过后 createTasks 会给财务插一条待办，但财务
--      · 侧边栏看不到「合同审核」菜单（没有 crm:contract:auditList）
--      · 手敲 URL 也会被 @SaCheckPermission("crm:contract:audit") 拒绝
--    超管虽然权限码过得去（*:*:* 由 SaTokenConfig#overridePermissionMatching
--    短路成"全部权限"），但 ContractServiceImpl#audit 要求待办任务的
--    approver_id = 当前用户，而任务派给了角色4的成员 —— 超管也审不了。
--    => 合同审批流一旦走到节点2，就再没有人能推进它，只能改库。
--
--  修法（为什么不直接给角色4硬编码补权限）：
--    硬编码能救这一次，但下次谁改了审批流里的审批角色，同样的断链会再来一遍，
--    而且不会再有人记得来补菜单。所以本脚本以【审批流配置本身】为准：
--    谁被配成合同流的角色审批人（approver_type=2），谁就拿到合同审核相关菜单。
--    审批流改了，重跑本脚本即可对齐，不需要改 SQL。
--
--  受影响范围：仅新增 sys_role_menu 授权行。
--              不改任何菜单行、不改审批流、不改业务数据、不删任何行。
--
--  幂等：INSERT IGNORE —— sys_role_menu 主键是 (role_id, menu_id)，
--        重复执行不报错、不重复授权。
--
--  什么时候跑：只对【存量库】。已经跑过旧版 crm.sql 的库都要跑一次，
--              2026-09-15 已在生产库执行过。
--              ⚠️ 但【全新建的库也要跑】—— 本脚本的授权行尚未折叠进 crm.sql
--              （见 ../upgrade/README.md「新增脚本时」第 3 条），别以为新库就免了。
--              审批流里换了角色审批人之后，重跑一次即可对齐。
--
--  回滚：见文件末尾第 4 节。⚠️ 那里的粗粒度写法会误伤原本就有的授权，
--        精确回滚请先跑 3.1 记下 role_id。
--
--  执行：cd 到本目录（sql/upgrade/），
--        mysql -h127.0.0.1 -uroot -p crm < alter-2026-09-15-contract-audit-finance-menu.sql
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) 主授权：审批流的角色审批人 → 合同审核三个菜单
--
--    菜单侧按 perms 取而不是写死 menu_id（对照 crm.sql:1736-1738）：
--      crm:contract:auditList → menu_id 42  「合同审核」页面
--      crm:contract:audit     → menu_id 4201「审核」按钮
--      crm:contract:batchAudit→ menu_id 4202「批量审核」按钮
--    三个一起给：财务在节点2 面对的很可能是积压的待办，只能逐条点说不过去；
--    而「能审却不能批量审」也不是有人想要的中间态。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT r.`role_id`, m.`menu_id`
  FROM `sys_role` r
  JOIN `sys_menu` m
    ON m.`perms` IN ('crm:contract:auditList', 'crm:contract:audit', 'crm:contract:batchAudit')
 WHERE EXISTS (
         SELECT 1
           FROM `sys_approval_node` n
           JOIN `sys_approval_flow` f ON f.`flow_id` = n.`flow_id`
          WHERE f.`flow_code` = 'CONTRACT_AUDIT'
            AND f.`del_flag` = 0
            AND n.`del_flag` = 0
            AND n.`approver_type` = 2
            AND n.`approver_value` IS NOT NULL
            -- approver_value 是 JSON 数组（如 [4]），判断当前角色是否在列。
            -- CAST(... AS JSON) 不能省：JSON_CONTAINS 的第二个参数必须是 JSON 值，
            -- 传裸的 4 会被当成字符串 "4" 而匹配不上 JSON 数字 4。
            AND JSON_CONTAINS(n.`approver_value`, CAST(r.`role_id` AS JSON))
       );


-- -----------------------------------------------------------------------------
-- 2) 补父节点
--
--    前端 store/permission.js 靠父子关系建树：父节点没授权，子节点建不出来，
--    表现是「权限明明给了，菜单就是不显示」——crm.sql:1988 有同样的孤立节点校验。
--    正常情况下角色4 已经有「合同管理」目录(menu_id 40)了，这里是兜底，
--    防止将来换一个没有合同目录的角色当审批人时又踩同一个坑。
--
--    子查询包一层派生表 t：MySQL 对「INSERT 的目标表同时出现在子查询里」会报
--    1093，派生表是标准绕法（与 alter-2026-09-15-invoice-detail-menu.sql 同款）。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT t.`role_id`, t.`parent_id`
  FROM (
      SELECT DISTINCT rm.`role_id`, m.`parent_id`
        FROM `sys_role_menu` rm
        JOIN `sys_menu` m ON m.`menu_id` = rm.`menu_id`
       WHERE m.`perms` IN ('crm:contract:auditList', 'crm:contract:audit', 'crm:contract:batchAudit')
  ) t
 WHERE t.`parent_id` <> 0;


-- -----------------------------------------------------------------------------
-- 3) 校验（复制出来单独执行）
-- -----------------------------------------------------------------------------

-- 3.1 谁被授权了（期望：财务那行出现，role_id=4 / 合同审核 / menu_id 42,4201,4202）
--   SELECT rm.role_id, r.role_name, rm.menu_id, m.menu_name, m.perms
--     FROM `sys_role_menu` rm
--     JOIN `sys_role` r ON r.role_id = rm.role_id
--     JOIN `sys_menu` m ON m.menu_id = rm.menu_id
--    WHERE m.perms IN ('crm:contract:auditList','crm:contract:audit','crm:contract:batchAudit')
--    ORDER BY rm.role_id, rm.menu_id;

-- 3.2 断链是否真的接上了（期望：0 行）
--     「是合同流的角色审批人、却没有 crm:contract:audit 权限」的角色
--   SELECT r.role_id, r.role_name
--     FROM `sys_role` r
--    WHERE EXISTS (
--            SELECT 1 FROM `sys_approval_node` n
--              JOIN `sys_approval_flow` f ON f.flow_id = n.flow_id
--             WHERE f.flow_code = 'CONTRACT_AUDIT' AND f.del_flag = 0
--               AND n.del_flag = 0 AND n.approver_type = 2
--               AND n.approver_value IS NOT NULL
--               AND JSON_CONTAINS(n.approver_value, CAST(r.role_id AS JSON))
--          )
--      AND NOT EXISTS (
--            SELECT 1 FROM `sys_role_menu` rm
--              JOIN `sys_menu` m ON m.menu_id = rm.menu_id
--             WHERE rm.role_id = r.role_id AND m.perms = 'crm:contract:audit'
--          );

-- 3.3 孤立节点检查（期望：0 行）—— 与 crm.sql:1988 同一条
--   SELECT '孤立节点(父未授权)' AS 项, COUNT(*) AS 值
--     FROM `sys_role_menu` c
--     JOIN `sys_menu` m ON m.menu_id = c.menu_id
--    WHERE m.parent_id <> 0
--      AND NOT EXISTS (SELECT 1 FROM `sys_role_menu` p
--                       WHERE p.role_id = c.role_id AND p.menu_id = m.parent_id);

-- 3.4 接口层判据：用 finance 账号重新登录（**必须重新登录**，菜单树只在登录时拉一次），
--     侧边栏应出现「合同管理 → 合同审核」。再确认该账号的 GET /auth/me
--     （或菜单树接口）返回的 permissions 里含 crm:contract:audit。


-- -----------------------------------------------------------------------------
-- 4) 回滚
--    ⚠️ 下面是【粗粒度】回滚：会把销售总监原本就有的合同审核授权一起删掉。
--       要精确回滚，先跑 3.1 记下 role_id（本次新增的应该只有财务），再改成
--       ... WHERE rm.role_id = 4 AND m.perms IN (...)。
-- -----------------------------------------------------------------------------
--   DELETE rm FROM `sys_role_menu` rm
--     JOIN `sys_menu` m ON m.menu_id = rm.menu_id
--    WHERE m.perms IN ('crm:contract:auditList','crm:contract:audit','crm:contract:batchAudit');
