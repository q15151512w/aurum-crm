-- =============================================================================
--  存量库升级脚本：sys_notice 补 handle_status / handle_time（「已处理」功能）
--
--  背景：消息/通知新增「已处理」状态 → 在「已读」之外再加一对
--        handle_status（0 未处理 / 1 已处理）+ handle_time（处理时间）。
--        记录某条待办/任务消息是否已被当事人「处理」掉，区别于单纯「看过」。
--
--  ⚠️ 什么时候跑这个脚本：
--      - 已有数据的库（本地 / 远程联调库）→ **执行本脚本**，不用重建。
--      - 全新的库 → 直接跑 sql/crm.sql 即可，它的建表语句**已经含这两列**，
--        再执行本脚本会报 "Duplicate column name 'handle_status'" 之类错误，忽略即可。
--
--  幂等性：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，
--          重复执行会报 1060 Duplicate column name。忽略即可。
-- =============================================================================

ALTER TABLE `sys_notice`
    ADD COLUMN `handle_status` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '是否已处理(0未处理 1已处理)' AFTER `read_time`,
    ADD COLUMN `handle_time` DATETIME DEFAULT NULL
        COMMENT '处理时间' AFTER `handle_status`;

-- 校验：应看到 handle_status / handle_time 两列，且默认 handle_status=0
-- SHOW COLUMNS FROM `sys_notice`;
