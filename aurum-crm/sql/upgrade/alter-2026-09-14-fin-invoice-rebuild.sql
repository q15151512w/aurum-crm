-- =============================================================================
--  修复脚本：重建 fin_invoice（把停留在旧 schema 的表拉齐到最新）
--
--  ⚠️⚠️ 只在「表是旧 schema」时执行 ⚠️⚠️
--      这是一次性修复，**不是**常规迁移。执行前必须先确认诊断：
--
--        SHOW COLUMNS FROM `fin_invoice`;
--
--      需要修复的表**缺少 `payment_id` 与 `attachment_ids` 列**（且缺六个开票信息
--      快照列、`uk_payment_active` 索引可能还在）。典型症状是列表接口报
--        ERROR 1054 (42S22): Unknown column 'payment_id' in 'field list'
--      因为 InvoiceDO 里有 paymentId / attachmentIds，任何 selectPage 都会带上它们。
--
--      ⚠️ 如果表**已经**是最新结构（有 payment_id + attachment_ids），
--         **不要执行本脚本** —— 它会把好表 RENAME 走，再插一遍数据。
--
--  背景：为什么不用 alter-2026-09-14-invoice-allocation.sql？
--      那份脚本假定 `payment_id` **已存在**（其第 2 步 DROP uk_payment_active、
--      第 3 步 MODIFY payment_id）。对本库这张更早的表，它第 2 步就会断。
--      本脚本改为「整表重建」，覆盖从任意旧版本拉齐的路径。
--
--  安全性：
--      - 用 RENAME 而非 DROP：旧表保留为 `fin_invoice_bak_20260914`，可随时回滚。
--      - 无外键、无触发器引用 fin_invoice（已核实）。
--      - 只动 fin_invoice 一张表，其余表不碰。
--      - 回插的 3 行**不补 fin_invoice_payment 明细** —— 历史行没有可靠的
--        回款关联，编一条出来是伪造数据。代价：这些旧单日后被编辑时会被
--        「累计开票不能超过回款金额」挡住（属预期，非 bug）。
--
--  回滚：RENAME TABLE `fin_invoice` TO `fin_invoice_broken`,
--                     `fin_invoice_bak_20260914` TO `fin_invoice`;
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 旧表改名留档（不删）
--    幂等性：重跑会报 "Table 'fin_invoice_bak_20260914' already exists"（1050），
--    说明上次已执行过，忽略即可 —— 别把 bak 名字改掉重跑。
-- -----------------------------------------------------------------------------
RENAME TABLE `fin_invoice` TO `fin_invoice_bak_20260914`;

-- -----------------------------------------------------------------------------
-- 2) 按最新结构重建（与 sql/crm.sql 的 fin_invoice 逐字一致）
-- -----------------------------------------------------------------------------
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
-- 3) 回插历史 3 行
--
-- 新列（payment_id / 六个快照列 / attachment_ids）一律留 NULL：
--   - payment_id  NULL 正是「一票多回款」下多明细发票的合法取值；
--   - 快照列历史无来源行，NULL 是诚实值（见 02-建表文档）；
--   - attachment_ids 无历史附件。
-- 金额 / 状态 / 日期等业务字段原样照搬。
-- -----------------------------------------------------------------------------
INSERT INTO `fin_invoice`
  (`invoice_id`, `apply_no`, `invoice_no`, `contract_id`, `customer_id`, `owner_user_id`,
   `dept_id`, `invoice_type`, `amount`, `tax_rate`, `tax_amount`, `total_amount`,
   `apply_date`, `issue_date`, `issue_user_id`, `audit_status`, `status`, `remark`,
   `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT `invoice_id`, `apply_no`, `invoice_no`, `contract_id`, `customer_id`, `owner_user_id`,
       `dept_id`, `invoice_type`, `amount`, `tax_rate`, `tax_amount`, `total_amount`,
       `apply_date`, `issue_date`, `issue_user_id`, `audit_status`, `status`, `remark`,
       `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`
  FROM `fin_invoice_bak_20260914`;

-- -----------------------------------------------------------------------------
-- 4) 校验
-- -----------------------------------------------------------------------------
-- 4.1 新列已存在（应看到 payment_id / attachment_ids / 六个快照列，共 8 行）
--   SELECT COLUMN_NAME FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fin_invoice'
--      AND COLUMN_NAME IN ('payment_id','attachment_ids','invoice_title','tax_no',
--                          'bank_name','bank_account','invoice_addr','invoice_phone');
--
-- 4.2 行数一致（两行应相等：3 与 3）
--   SELECT (SELECT COUNT(*) FROM `fin_invoice`) AS new_cnt,
--          (SELECT COUNT(*) FROM `fin_invoice_bak_20260914`) AS old_cnt;
--
-- 4.3 索引已就位（应能看到 idx_payment_id；uk_payment_active 应已不存在）
--   SHOW INDEX FROM `fin_invoice`;
--
-- 4.4 列表接口不再报 Unknown column（此为最终判据）
--   SELECT invoice_id, apply_no, payment_id, attachment_ids FROM `fin_invoice`;

-- -----------------------------------------------------------------------------
-- 5) 确认无误后清理旧表（⚠️ 手工执行，先确认 4.1~4.4 全过）
-- -----------------------------------------------------------------------------
--   DROP TABLE `fin_invoice_bak_20260914`;
