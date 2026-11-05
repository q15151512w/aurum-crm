-- =============================================================================
--  存量库升级脚本：发票改为「一票多回款」明细模型，并补发票信息快照列
--
--  背景：原型「新增发票登记」是一张发票合并多笔回款（回款信息子表，每行带各自的
--        「本次开票金额」，底部汇总），而 fin_invoice 原本是 payment_id NOT NULL +
--        UNIQUE KEY uk_payment_active 的硬性一对一。fin_invoice_payment 这张明细表
--        在 sql/crm.sql 里早已建好却完全没被使用，本脚本把它接上。
--
--        同时新增六个发票信息快照列（发票抬头/纳税人识别号/开户行/开户账号/
--        开户行地址/联系电话）—— 列名照抄 crm_customer_invoice_info，让「选客户自动
--        带出」成为 1:1 透传，MapStruct 无需写 @Mapping。
--
--  ⚠️ 什么时候跑这个脚本：
--      - 已有数据的库（本地 / 远程联调库）→ **执行本脚本**，不用重建。
--      - 全新的库 → 直接跑 sql/crm.sql 即可，它的建表语句**已经含这六列、
--        payment_id 已可空、uk_payment_active 已删除**，再执行本脚本会报
--        "Duplicate column name 'invoice_title'"（1060）/
--        "Can't DROP 'uk_payment_active'; check that column/key exists"（1091），
--        回报错忽略即可。
--
--  幂等性：MySQL 8 的 ADD COLUMN / DROP INDEX 都不支持 IF EXISTS，
--          重复执行会报 1060 / 1091。忽略即可。回填那一步重跑会报 1062（主键重复）。
--
--  顺序要紧：**先 DROP INDEX 再 MODIFY**。先改可空会顺带重建 uk_payment_active，
--            而它马上要被删掉 —— 在数据量大的表上纯属白费一次索引重建的 I/O。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 发票信息快照列
--
-- 全部可空：历史数据没有来源行；且「客户没有开票信息」是真实存在的分支
-- （crm_customer_invoice_info 目前只有客户 1、4 两行）。
-- -----------------------------------------------------------------------------
ALTER TABLE `fin_invoice`
    ADD COLUMN `invoice_title` VARCHAR(128) DEFAULT NULL COMMENT '开票名称(抬头,来自客户开票信息快照)' AFTER `invoice_type`,
    ADD COLUMN `tax_no`        VARCHAR(64)  DEFAULT NULL COMMENT '纳税人识别号(快照)'                 AFTER `invoice_title`,
    ADD COLUMN `bank_name`     VARCHAR(128) DEFAULT NULL COMMENT '开户行(快照)'                       AFTER `tax_no`,
    ADD COLUMN `bank_account`  VARCHAR(64)  DEFAULT NULL COMMENT '银行账号(快照)'                     AFTER `bank_name`,
    ADD COLUMN `invoice_addr`  VARCHAR(255) DEFAULT NULL COMMENT '开票地址(快照)'                     AFTER `bank_account`,
    ADD COLUMN `invoice_phone` VARCHAR(32)  DEFAULT NULL COMMENT '开票电话(快照)'                     AFTER `invoice_addr`;

-- -----------------------------------------------------------------------------
-- 2) 删掉一对一唯一约束
--
-- 必须删，不只是因为语义变了：唯一索引对 NULL 不生效，多回款发票的 payment_id 为
-- NULL 时会**静默绕过**这条约束 —— 变成「看着有约束，其实没有」。
--
-- ⚠️ is_active 这一列本身要保留！uk_apply_no (apply_no, is_active) 还在用它。
-- -----------------------------------------------------------------------------
ALTER TABLE `fin_invoice` DROP INDEX `uk_payment_active`;

-- -----------------------------------------------------------------------------
-- 3) payment_id 降级为「主回款」(去规范化，取首条明细的 payment_id)
--
-- 明细表 fin_invoice_payment 才是唯一事实来源；这一列保留给 legacy 展示与索引。
-- 多回款发票由服务端写入首条明细的 payment_id；写入端不再依赖它做关联。
-- -----------------------------------------------------------------------------
ALTER TABLE `fin_invoice`
    MODIFY COLUMN `payment_id` BIGINT UNSIGNED DEFAULT NULL
    COMMENT '主回款ID(去规范化,取首条明细的payment_id;明细表为准)';

ALTER TABLE `fin_invoice` ADD INDEX `idx_payment_id` (`payment_id`);

-- -----------------------------------------------------------------------------
-- 4) 回填明细：把历史发票的单一回款搬进 fin_invoice_payment
--
-- 回填值取 total_amount（价税合计），不是 amount（不含税）——
-- write_off_amount 与 fin_payment.amount 同口径（都含税）。若取 amount，
-- 每笔核销都会少掉税额，且重新算出的父单 total_amount 与原值对不上。
--
-- id 直接复用 invoice_id：两者都是雪花 ID，唯一；也因此重跑会撞 1062。
-- -----------------------------------------------------------------------------
INSERT INTO `fin_invoice_payment`
  (`id`, `invoice_id`, `payment_id`, `write_off_amount`, `create_by`, `create_time`)
SELECT `invoice_id`, `invoice_id`, `payment_id`, `total_amount`, `create_by`, `create_time`
  FROM `fin_invoice`
 WHERE `payment_id` IS NOT NULL AND `del_flag` = 0;

-- 校验：
--   应看到六列已加、payment_id 的 Null 为 YES、uk_payment_active 已消失
--   SHOW COLUMNS FROM `fin_invoice`;
--   SHOW INDEX FROM `fin_invoice`;
--   每条未删除发票都应有一条明细
--   SELECT i.invoice_id, i.total_amount, ip.write_off_amount
--     FROM fin_invoice i LEFT JOIN fin_invoice_payment ip ON ip.invoice_id = i.invoice_id
--    WHERE i.del_flag = 0 AND ip.id IS NULL;

-- ⚠️ 回填后必须体检一遍「一票超一款」的存量脏数据。
--    旧模型下 fin_invoice 与 fin_payment 是刚性一对一，但两边金额并没有任何约束，
--    所以可能有发票的 total_amount 大于其回款 amount（种子数据里发票 #2 就是
--    98000 挂 50000 的回款）。回填只是照搬，不会自动修正 —— 这类行以后一旦被编辑，
--    就会被新的「累计开票不能超过回款金额」校验拒绝，用户看到的是「报销不了」。
--   SELECT i.invoice_id, i.apply_no, ip.payment_id, p.payment_no,
--          ip.write_off_amount AS 本次开票, p.amount AS 回款金额,
--          ip.write_off_amount - p.amount AS 超额
--     FROM fin_invoice_payment ip
--     JOIN fin_invoice i ON i.invoice_id = ip.invoice_id AND i.del_flag = 0
--     JOIN fin_payment p ON p.payment_id = ip.payment_id
--    WHERE ip.write_off_amount > p.amount;

-- ⚠️ 同理，同一笔回款被多张发票分摊、且合计超过回款金额的组合也要查：
--   SELECT ip.payment_id, SUM(ip.write_off_amount) AS 累计开票, p.amount AS 回款金额
--     FROM fin_invoice_payment ip
--     JOIN fin_invoice i ON i.invoice_id = ip.invoice_id AND i.del_flag = 0 AND i.status <> 2
--     JOIN fin_payment p ON p.payment_id = ip.payment_id
--    GROUP BY ip.payment_id, p.amount
--   HAVING SUM(ip.write_off_amount) > p.amount;
--   两条查询若有输出，按业务确认后手工调整明细金额或回款金额再上线。
