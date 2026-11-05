-- =============================================================================
--  演示数据回填：让「发票登记 → 财务开票」这条链路的弹窗有内容可看
--
--  症状（跑起来能看到的）：
--    1) 财务开票 → 选中待开票的 FP20260002 →「确认开票」弹窗里
--       「回款信息」表是空的、「发票信息」六项全是 --。
--    2) 发票登记 → 选客户「上海鸿远物流有限公司」时，
--       发票抬头 / 税号 / 开户行 等**不会**被带出来（全是空）。
--
--  根因（两条，都是数据、不是代码）：
--    A. `crm_customer_invoice_info` 缺 customer_id = 2 的那一行。
--       sql/crm.sql:2141-2144 的种子数据是给客户 1、2、4 各插一行，
--       但线上库只落了 1 和 4（客户 2 那行从没进来）。
--       → `GET /finance/invoice/customer-form?customerId=2` 返回的 invoiceInfo 是 null，
--         所以选客户带不出任何开票信息。
--    B. 发票 1/2/3 是**本功能上线前**用 SQL 直接插的历史行：
--       payment_id、六个快照列全是 NULL，且 fin_invoice_payment 一条明细都没有
--       （本表共 0 行）。而「回款信息」表的唯一事实来源就是 fin_invoice_payment。
--       → 所以开票弹窗的回款信息是空的、发票信息没有快照可显示。
--
--  本脚本做什么：
--    1) 补客户 2 的开票信息行（照抄 sql/crm.sql:2144）。
--    2) 按「该客户的开票信息」回填 3 张发票的六个快照列 —— 这正是
--       「选客户自动带出预设发票抬头」在登记时会做的事，历史行补上同一个来源。
--    3) 给发票 1、2 补 fin_invoice_payment 明细，并把主回款 payment_id 回填。
--       金额与回款严格对齐，不编数（见下方「为什么只有发票 1、2」）。
--    4) 一组校验查询 + 回滚语句。
--
--  ⚠️ 只应在**演示/开发库**执行。生产库若已有真实发票，别跑第 2、3 步 ——
--     给真实单据补一个「猜出来的」回款关联会污染核销轨迹。
--
--  幂等：三步都带守卫（NOT EXISTS / IS NULL），重复执行不会重复插、也不会
--        覆盖用户后来在界面上改过的值。
--
--  回滚：
--    DELETE FROM `fin_invoice_payment` WHERE `invoice_id` IN (1, 2);
--    UPDATE `fin_invoice` SET `payment_id` = NULL,
--           `invoice_title` = NULL, `tax_no` = NULL, `bank_name` = NULL,
--           `bank_account` = NULL, `invoice_addr` = NULL, `invoice_phone` = NULL
--     WHERE `invoice_id` IN (1, 2, 3);
--    DELETE FROM `crm_customer_invoice_info` WHERE `customer_id` = 2 AND `info_id` = 3;
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) 补客户 2 的开票信息（唯一缺失的那一行）
--    来源：sql/crm.sql:2144，逐字照抄。
--    守卫：客户 2 已有一行有效开票信息时不再插（避免造出两个默认行）。
--
--    ⚠️ 子查询用派生表 `(SELECT 1) d` 包一层：MySQL 对「INSERT 的目标表
--       同时出现在子查询里」在 UPDATE/DELETE 上会报 1093，派生表是标准绕法，
--       插这条时行为稳定。（fin_invoice_payment 有唯一键，第 3 步改用 INSERT IGNORE。）
-- -----------------------------------------------------------------------------
INSERT INTO `crm_customer_invoice_info`
  (`info_id`, `customer_id`, `invoice_title`, `tax_no`, `bank_name`, `bank_account`,
   `invoice_addr`, `invoice_phone`, `is_default`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
SELECT 3, 2, '上海鸿远物流有限公司', '91310115MA1KXXXX3C', '中国工商银行上海浦东支行', '6222021234567890123',
       '上海市浦东新区世纪大道 200 号', '021-58880001', 1, 1, NOW(), 1, NOW(), 0
  FROM (SELECT 1) d
 WHERE NOT EXISTS (
     SELECT 1 FROM `crm_customer_invoice_info` i
      WHERE i.`customer_id` = 2 AND i.`del_flag` = 0
 );


-- -----------------------------------------------------------------------------
-- 2) 回填 3 张发票的六个开票信息快照
--
--    来源取「该客户当前有效的默认开票信息」—— 与登记时前端带出、后端
--    applyFields 持久化的是同一份数据（见 InvoiceServiceImpl#applyFields）。
--    守卫 `v.invoice_title IS NULL`：只补历史空行，不覆盖界面上改过的值。
-- -----------------------------------------------------------------------------
UPDATE `fin_invoice` v
  JOIN `crm_customer_invoice_info` i
    ON i.`customer_id` = v.`customer_id`
   AND i.`del_flag` = 0
   AND i.`is_default` = 1
   SET v.`invoice_title` = i.`invoice_title`,
       v.`tax_no`        = i.`tax_no`,
       v.`bank_name`     = i.`bank_name`,
       v.`bank_account`  = i.`bank_account`,
       v.`invoice_addr`  = i.`invoice_addr`,
       v.`invoice_phone` = i.`invoice_phone`
 WHERE v.`invoice_title` IS NULL
   AND v.`del_flag` = 0;


-- -----------------------------------------------------------------------------
-- 3) 补发票 1、2 的回款明细（fin_invoice_payment）+ 主回款 payment_id
--
--    为什么只有发票 1、2，没有发票 3：
--      发票 1（客户 1，价税合计 41,800）= 回款 HK20260001（41,800）      —— 全额对齐
--      发票 2（客户 2，价税合计 98,000）= 回款 HK20260002（50,000）
--                                       + 回款 HK20260003（48,000）      —— 全额对齐
--      发票 3（客户 2，价税合计 50,000）没有可对齐的回款 —— 客户 2 的两笔已被
--        发票 2 占满。硬凑会编出超过回款总额的明细（违反「累计开票不得超回款」），
--        所以**故意不补**。它当前是 审批中(1)/待开票(0)，属预期状态。
--
--    主回款 payment_id 取该票明细里最小的 payment_id（确定性选择）；
--    语义上它只是给列表展示与 idx_payment_id 用的去规范化列，
--    真正的关联以 fin_invoice_payment 为准。
--
--    id 用 9000001~9000003 这个明显的人造区间：业务代码走雪花算法
--    （IdUtil.getSnowflakeNextId()，约 2×10^18），不可能撞上；本表当前为空。
--    INSERT IGNORE 靠 uk_inv_pay(invoice_id, payment_id) 天然幂等。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `fin_invoice_payment`
  (`id`, `invoice_id`, `payment_id`, `write_off_amount`, `create_by`, `create_time`)
VALUES
  (9000001, 1, 1, 41800.00, 1, NOW()),
  (9000002, 2, 2, 50000.00, 1, NOW()),
  (9000003, 2, 3, 48000.00, 1, NOW());

-- 主回款：只补 payment_id 还是 NULL 的行
UPDATE `fin_invoice` v
   SET v.`payment_id` = (
       SELECT MIN(ip.`payment_id`) FROM `fin_invoice_payment` ip WHERE ip.`invoice_id` = v.`invoice_id`
   )
 WHERE v.`payment_id` IS NULL
   AND v.`del_flag` = 0
   AND EXISTS (
       SELECT 1 FROM `fin_invoice_payment` ip WHERE ip.`invoice_id` = v.`invoice_id`
   );


-- -----------------------------------------------------------------------------
-- 4) 校验
-- -----------------------------------------------------------------------------

-- 4.1 客户 2 的开票信息已就位（应 1 行：上海鸿远物流有限公司）
--   SELECT info_id, customer_id, invoice_title, tax_no, bank_name, is_default
--     FROM `crm_customer_invoice_info` WHERE `customer_id` = 2 AND `del_flag` = 0;

-- 4.2 三张发票的快照已回填（invoice_title 不再是 NULL）
--   SELECT invoice_id, apply_no, customer_id, invoice_title, tax_no, bank_name
--     FROM `fin_invoice` ORDER BY invoice_id;

-- 4.3 明细就位，且「每票明细金额之和 = 该票价税合计」
--     （期望：票 1 → 41800 = 41800；票 2 → 98000 = 98000；票 3 无明细）
--   SELECT v.invoice_id, v.total_amount,
--          COALESCE(SUM(ip.write_off_amount), 0) AS alloc_sum,
--          COUNT(ip.id) AS rows_cnt
--     FROM `fin_invoice` v
--     LEFT JOIN `fin_invoice_payment` ip ON ip.invoice_id = v.invoice_id
--    GROUP BY v.invoice_id, v.total_amount
--    ORDER BY v.invoice_id;

-- 4.4 主回款已回填（票 1 → 1；票 2 → 2）
--   SELECT invoice_id, payment_id FROM `fin_invoice` WHERE del_flag = 0 ORDER BY invoice_id;

-- 4.5 接口层最终判据 —— 下面两条都要能看到内容：
--   GET /finance/invoice/customer-form?customerId=2
--       → invoiceInfo 不再是 null（快照能带出来了）；
--         payments 里 HK20260002 / HK20260003 的 remainingAmount 变成 0
--         （已被发票 2 全额开票），按 listInvoiceablePayments 的口径从候选里过滤掉
--         —— 这是**正确**的：它们确实没有可开余额了。
--   GET /finance/invoice/2
--       → 六个快照列有值，allocations 有 2 行（HK20260002 / HK20260003）。


-- -----------------------------------------------------------------------------
-- 5) 执行后，想试「新建一张发票登记」该选哪个客户
--
--    客户 1 与客户 2 名下的回款已被本脚本全额开票（发票 1、2），
--    可开余额为 0，选它们会提示「该客户暂无未全部开票的回款」—— 属预期。
--
--    还有余额、可用来演示新建登记的客户：
--      客户 3  → 回款 HK20260914000001  余额 100.00
--      客户 5  → 回款 HK20260004        余额 15,800.00
--      客户 7  → 回款 HK202609140002    余额 100.00
--    ⚠️ 其中只有客户 1、2、4 在 crm_customer_invoice_info 里有开票信息。
--       客户 3/5/7 没有，所以「选客户自动带出抬头/税号」对它们是空的 ——
--       这是数据本身的状态，不是功能坏了。想演示自动带出，就在
--       「客户详情 → 开票信息」里给该客户维护一行（或在下面的查询里确认）：
--
--    SELECT c.customer_id, c.customer_name,
--           CASE WHEN i.info_id IS NULL THEN '无开票信息' ELSE '有' END AS invoice_info
--      FROM `crm_customer` c
--      LEFT JOIN `crm_customer_invoice_info` i
--             ON i.`customer_id` = c.`customer_id` AND i.`del_flag` = 0
--     WHERE c.`del_flag` = 0
--     ORDER BY c.customer_id;
-- -----------------------------------------------------------------------------
