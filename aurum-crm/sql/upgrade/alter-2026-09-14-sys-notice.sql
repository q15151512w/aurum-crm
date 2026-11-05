-- =============================================================================
--  存量库升级脚本：sys_notice 补 update_by / update_time，并把 receiver_id 改为可空
--
--  背景：消息/公告功能里，SysNoticeDO 继承 BaseDO（含 update_by / update_time），
--        且「全员公告 = receiver_id IS NULL」是可见性口径；但原始的 sys_notice
--        表既没有 update_by/update_time 两列，receiver_id 又是 NOT NULL ——
--        MyBatis-Plus 列表查询立刻报 "Unknown column 'update_by'"，广播公告也存不进去。
--        本脚本把表结构与代码、建表文档对齐。
--
--  ⚠️ 什么时候跑这个脚本：
--      - 已有数据的库（本地 / 远程联调库）→ **执行本脚本**，不用重建。
--      - 全新的库 → 直接跑 sql/crm.sql 即可，它的建表语句**已经含这两列、
--        receiver_id 已可空**，再执行本脚本会报
--        "Duplicate column name 'update_by'" 之类错误，忽略即可。
--
--  幂等性：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，
--          重复执行会报 1060 Duplicate column name。忽略即可。
-- =============================================================================

ALTER TABLE `sys_notice`
    ADD COLUMN `update_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '更新人ID'
    AFTER `create_time`,
    ADD COLUMN `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
    AFTER `update_by`;

ALTER TABLE `sys_notice`
    MODIFY COLUMN `receiver_id` BIGINT UNSIGNED DEFAULT NULL
    COMMENT '接收人ID(NULL=全员公告)';

-- 校验：应看到 update_by / update_time 两列，且 receiver_id 的 Null 为 YES
-- SHOW COLUMNS FROM `sys_notice`;
