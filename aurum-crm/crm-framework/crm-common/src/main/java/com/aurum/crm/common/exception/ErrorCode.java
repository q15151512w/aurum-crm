package com.aurum.crm.common.exception;

import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>错误码分段约定，便于快速定位问题来源：
 * <pre>
 *   0          成功
 *   400-499    客户端错误（参数、认证、权限）
 *   500-599    服务端错误
 *   1000+      业务错误，按模块分段：
 *       1000-1999  系统模块（用户、角色、部门、字典）
 *       2000-2999  客户模块（线索、客户、商机、合同）
 *       3000-3999  财务模块（回款、发票）
 *       4000-4999  审批流
 *       5000-5999  AI 小助手（问答 / 工具调用）
 * </pre>
 */
@Getter
public enum ErrorCode {

    // ==================== 通用 ====================
    SUCCESS(0, "操作成功"),
    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有该操作权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不正确"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    // ==================== 服务端 ====================
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    NOT_IMPLEMENTED(501, "功能未实现"),

    // ==================== 系统模块 1000+ ====================
    // 1000-1099 认证（编号与《05-接口文档》1.5 错误码表严格一致，改动前先看文档）
    USER_NOT_EXISTS(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "账号或密码错误"),
    USER_OLD_PASSWORD_ERROR(1003, "原密码错误"),
    /** 邮箱验证码发送过于频繁（同一邮箱 60 秒一次）。码值 1004 不变，只是说明换成邮箱 */
    CODE_SEND_TOO_FREQUENT(1004, "验证码发送过于频繁"),
    USER_USERNAME_EXISTS(1005, "账号已存在"),
    /** 邮箱验证码错误或已过期。码值 1006 不变 */
    EMAIL_CODE_ERROR(1006, "验证码错误或已过期"),
    /**
     * 账号未启用：登录时 {@code sys_user.status != 1}。
     *
     * <p>这里只是兜底文案 —— 文档要求按情况给不同提示（待分配角色 / 已禁用 / 已离职），
     * Service 抛出时用 {@code new ServiceException(USER_NOT_ENABLED, 具体文案)} 覆写。
     */
    USER_NOT_ENABLED(1007, "账号未启用"),
    /**
     * 验证码错误或已过期（登录时的算术题，见接口文档 2.9）。
     *
     * <p>与 {@link #EMAIL_CODE_ERROR}（1006，邮箱验证码）分开编号 —— 两者共用一个码的话，
     * 日志里分不清用户是「邮箱码填错」还是「算术题答错/过期」，排障时要多绕一圈。
     */
    CAPTCHA_CODE_ERROR(1008, "验证码错误或已过期，请重新计算"),
    /**
     * 邮件发送失败（SMTP 连不上、授权码错、被 163 限流等）。
     *
     * <p><b>要有独立码</b>：这类失败与「用户操作错误」性质完全不同 ——
     * 是服务端/配置问题，用户重试也没用。共用一个通用码的话，
     * 前端只会弹「验证码发送过于频繁」，把排查方向带偏。
     */
    EMAIL_SEND_FAILED(1009, "验证码邮件发送失败，请稍后重试"),
    /**
     * 该邮箱已被注册。
     *
     * <p><b>只在注册接口用</b>。⚠️ 千万别在「发送验证码」接口也用这个码 ——
     * 那样任何人都能拿邮箱来试，逐个问出「这个邮箱有没有注册过」。
     */
    USER_EMAIL_EXISTS(1010, "该邮箱已被注册"),
    /**
     * 验证码连续校验失败次数超过上限，该验证码已被作废。
     *
     * <p><b>为什么单独给一个码</b>：{@link #EMAIL_CODE_ERROR} 有意把「填错」和「已过期」
     * 混成一句话以防枚举，但「连续错太多次」是用户自己造成的、必须告诉他去重新获取 ——
     * 继续回「验证码错误或已过期」的话，用户只会反复重试同一个已经作废的码，永远进不去。
     *
     * <p>它不会给爆破者提供额外信息：对方本来就知道自己错了，这个码只是告诉他
     * 「这个码别再试了，下一个」。
     */
    EMAIL_CODE_TOO_MANY_ATTEMPTS(1011, "验证码错误次数过多，请重新获取"),
    /**
     * 邮件服务<b>认证失败</b>（163 返回 {@code 535 Error: authentication failed}）。
     *
     * <p><b>为什么必须与 {@link #EMAIL_SEND_FAILED} 分开</b>：1009 的文案是
     * 「请稍后重试」，它覆盖的是「网络抖动 / 被限流」这类**重试可能有效**的失败。
     * 而认证失败是**配置问题** —— 授权码错了、或者 163 网页版的 SMTP 服务没开。
     * 用户重试一万次也不会成功，让他一直重试只会把排查方向带偏。
     *
     * <p>⚠️ 163 在「授权码错」和「SMTP 服务没开」两种情况下**都只回 535**，
     * 光看错误码区分不了 —— 所以真正的排查线索在服务端日志里
     * （见 {@code EmailCodeServiceImpl} 里那段针对性的错误日志），
     * 前端只需告诉用户「找管理员」而不是「稍后重试」。
     */
    EMAIL_SERVICE_AUTH_FAILED(1012, "邮件服务未正确配置，请联系管理员"),

    ROLE_NOT_EXISTS(1101, "角色不存在"),
    ROLE_NAME_EXISTS(1102, "角色名称已存在"),
    ROLE_HAS_USER(1103, "该角色下还有用户，无法删除"),
    ROLE_KEY_EXISTS(1104, "角色标识已存在"),

    ROLE_GROUP_NOT_EXISTS(1107, "角色组不存在"),
    ROLE_GROUP_NAME_EXISTS(1108, "角色组名称已存在"),

    USER_CANNOT_BE_OWN_SUPERIOR(1105, "不能把自己设为直属上级"),
    /**
     * 激活一个没有任何角色的账号。
     *
     * <p>「有账号必有角色」的硬约束（接口文档 2.4 / 3.1）——
     * 少了它，注册出来的账号能被直接激活，变成一个登录进去什么都看不到的「幽灵账号」。
     */
    USER_NOT_ASSIGNED_ROLE(1106, "请先为该人员分配角色"),

    DEPT_NOT_EXISTS(1201, "部门不存在"),
    DEPT_HAS_CHILD(1202, "存在子部门，无法删除"),
    DEPT_HAS_USER(1203, "部门下还有用户，无法删除"),

    DICT_TYPE_EXISTS(1301, "字典类型已存在"),
    DICT_TYPE_NOT_EXISTS(1302, "字典类型不存在"),
    DICT_DATA_NOT_EXISTS(1303, "字典项不存在"),
    /**
     * 同一字典类型下已存在相同的键值。
     *
     * <p>⚠️ 这是**应用级**校验：{@code sys_dict_data} 上只有普通索引 {@code idx_dict_type}，
     * <b>没有唯一键</b>（与 {@code sys_dict_type} 的 {@code uk_dict_type} 不同），
     * 并发插入仍可能漏过一个重复值。
     *
     * <p>但这条校验必须实现 —— 同类型下两个相同的 {@code dict_value} 会让
     * 「码值 → 标签」究竟取到哪一个取决于 id 大小，结果不可预测，排查时极难定位。
     */
    DICT_DATA_VALUE_EXISTS(1304, "该字典类型下已存在相同的键值"),
    DICT_TYPE_HAS_DATA(1305, "该字典类型下还有字典项，无法删除"),

    // ---- 附件 / 文件上传 1400+（《05-接口文档》3.7 附件）----
    ATTACHMENT_NOT_EXISTS(1401, "附件不存在"),

    // ---- 计量单位 1500+（《05-接口文档》3.6 基础配置；契约见《14-计量单位模块接口文档》）----
    UNIT_NOT_EXISTS(1501, "计量单位不存在"),
    UNIT_CODE_EXISTS(1502, "单位编码已存在"),
    /**
     * 单位重名。
     *
     * <p>⚠️ 这是<b>应用级</b>校验：{@code sys_unit} 只有 {@code uk_unit_code}，
     * {@code unit_name} 上没有唯一索引（《02-建表文档》4 节），并发插入可能漏过一个重复名。
     *
     * <p>但这条校验必须实现 —— 产品模块的 {@code ProductServiceImpl.resolveUnitId()}
     * 按名字查单位取的是第一条（{@code selectList(...).get(0)}），
     * 库里有两个同名单位时「产品到底存了哪个 unit_id」取决于 id 大小，结果不可预测。
     */
    UNIT_NAME_EXISTS(1503, "单位名称已存在"),
    /**
     * 单位被引用，禁止删除。
     *
     * <p>抛出时用 {@code new ServiceException(UNIT_IN_USE, 具体文案)} 覆写，
     * message 里带上「被哪些表引用了多少条」—— 否则用户只知道删不掉、不知道去哪解引用。
     */
    UNIT_IN_USE(1504, "该计量单位已被产品/单据引用，无法删除"),

    // ---- 消息通知 1600+（《05-接口文档》3.6 基础配置）----
    NOTICE_NOT_EXISTS(1601, "消息通知不存在"),
    /**
     * 非法通知类型。
     *
     * <p>管理端只允许 2/3/4；1（审批提醒）由审批流产生。传入其它值或 1 都拒绝。
     */
    NOTICE_TYPE_INVALID(1602, "通知类型不合法"),

    // ==================== 客户模块 2000+ ====================
    LEAD_NOT_EXISTS(2001, "线索不存在"),
    LEAD_ALREADY_CONVERTED(2002, "该线索已转化，不能重复转化"),
    LEAD_NOT_IN_POOL(2003, "该线索不在线索池中"),
    LEAD_ALREADY_OWNED(2004, "该线索已被他人领取"),
    /**
     * 已在池中的线索再点「放入线索池」。
     *
     * <p>以前这里复用的是 {@link #LEAD_NOT_IN_POOL}，于是提示成「该线索不在线索池中」——
     * 与用户眼前的事实正好相反。与公海的 {@link #CUSTOMER_ALREADY_IN_POOL} 对齐。
     */
    LEAD_ALREADY_IN_POOL(2005, "该线索已在线索池中"),

    CUSTOMER_NOT_EXISTS(2101, "客户不存在"),
    CUSTOMER_NOT_IN_POOL(2102, "该客户不在公海中"),
    CUSTOMER_ALREADY_OWNED(2103, "该客户已被他人领取"),
    CUSTOMER_ALREADY_IN_POOL(2104, "该客户已在公海中"),
    CUSTOMER_TARGET_USER_NOT_EXISTS(2105, "分配的目标用户不存在"),

    OPPORTUNITY_NOT_EXISTS(2201, "商机不存在"),
    QUOTATION_NOT_EXISTS(2251, "报价单不存在"),
    QUOTATION_STATUS_ERROR(2252, "当前报价单状态不允许该操作"),
    /**
     * 报价单的客户与所选商机不是同一个客户。
     *
     * <p>{@code crm_quotation} 的 {@code customer_id} 与 {@code opportunity_id} 都是逻辑外键，
     * 原先只各自校验存在性 —— 于是能把「客户A + 商机B」存进去：报价单挂在别人的商机上，
     * 列表照常显示，事后很难判断哪个才是对的。
     *
     * <p>**拒绝而不是静默改写客户**：改客户会连带改变数据权限与统计口径
     * （与合同侧的 {@code CONTRACT_RELATION_MISMATCH} 同一取舍）。前端表单会按所选商机
     * 对齐客户，正常操作不会撞到它。
     */
    QUOTATION_RELATION_MISMATCH(2253, "报价单的客户与所选商机不一致，请重新选择商机后重试"),
    CONTRACT_NOT_EXISTS(2301, "合同不存在"),
    CONTRACT_AMOUNT_MISMATCH(2302, "合同金额与产品明细汇总不一致"),
    CONTRACT_AUDIT_STATUS_ERROR(2303, "当前审核状态不允许该操作"),
    /**
     * 合同的客户与所选商机 / 报价单不一致。
     *
     * <p>这三个字段原本各选各的，接口上能把「客户A + 商机B + 报价单C」直接存进去 ——
     * 落库后「报价单编号」列指向的是别人的单子，而合同自己的金额 / 负责人 / 数据权限
     * 全挂在客户A上，谁也看不出对不上。校验与对齐口径见
     * {@code com.aurum.crm.module.crm.support.ContractRelationSupport}。
     *
     * <p>客户选错时**拒绝**而不是静默改写：改客户会连带改变数据权限与统计口径。
     */
    CONTRACT_RELATION_MISMATCH(2304, "合同的客户与所选商机 / 报价单不一致，请重新选择商机 / 报价单后重试"),

    // ---- 产品 / 产品分类 2400+（《05-接口文档》5.3 产品与分类）----
    PRODUCT_NOT_EXISTS(2401, "产品不存在"),
    PRODUCT_NO_EXISTS(2402, "产品编号已存在"),
    PRODUCT_CATEGORY_NOT_EXISTS(2403, "产品分类不存在"),
    PRODUCT_CATEGORY_HAS_CHILD(2404, "该分类下存在子分类，无法删除"),
    PRODUCT_CATEGORY_IN_USE(2405, "该分类下存在产品，无法删除"),
    PRODUCT_CATEGORY_NAME_EXISTS(2406, "同级下已存在同名分类"),
    PRODUCT_CATEGORY_PARENT_INVALID(2407, "父分类不能是自己或其子分类"),
    PRODUCT_UNIT_NOT_EXISTS(2408, "计量单位不存在"),

    // ---- 关注 2500+（通用：线索 / 客户 / 商机 / 报价单 / 合同 / 回款 / 发票）----
    STAR_BIZ_TYPE_INVALID(2501, "不支持关注的业务类型"),

    // ---- 公海 / 线索池 2600+（领取上限）----
    POOL_CLAIM_LIMIT(2601, "已达到周期领取上限"),

    // ==================== 财务模块 3000+ ====================
    PAYMENT_NOT_EXISTS(3001, "回款记录不存在"),
    INVOICE_NOT_EXISTS(3101, "发票不存在"),
    INVOICE_ALREADY_ISSUED(3102, "该发票已开具，不能重复开票"),
    /** 发票至少要挂一笔回款；明细为空说明前端漏传，属参数问题而非并发问题 */
    INVOICE_PAYMENT_REQUIRED(3103, "请至少选择一笔回款"),
    /** 回款本身不可开票：不存在 / 已删除 / 未审核通过 / 未确认收款 */
    INVOICE_PAYMENT_INVALID(3104, "所选回款不存在或当前状态不可开票"),
    /** 一张发票只能开给一个客户，合并回款时必须同客户 */
    INVOICE_PAYMENT_CUSTOMER_MISMATCH(3105, "所选回款必须属于同一客户"),
    /** 跨发票的累计开票金额超过该笔回款的可开余额 */
    INVOICE_AMOUNT_EXCEED_PAYMENT(3106, "开票金额超过回款可开余额"),
    INVOICE_AMOUNT_INVALID(3107, "开票金额必须大于 0"),
    /** 同一笔回款在同一张发票里只能出现一次（否则撞 uk_inv_pay 报 500） */
    INVOICE_PAYMENT_DUPLICATE(3108, "同一笔回款不能重复添加"),
    INVOICE_TAX_RATE_INVALID(3109, "税率必须在 0 到 100 之间"),

    // ==================== 审批流 4000+ ====================
    APPROVAL_FLOW_NOT_EXISTS(4001, "审批流模板不存在"),
    APPROVAL_FLOW_DISABLED(4002, "该审批流已停用"),
    APPROVAL_INSTANCE_NOT_EXISTS(4003, "审批实例不存在"),
    APPROVAL_TASK_NOT_EXISTS(4004, "审批任务不存在"),
    APPROVAL_TASK_HANDLED(4005, "该审批任务已处理"),
    APPROVAL_NOT_YOUR_TASK(4006, "不是您的审批任务"),

    // ==================== 并发 4900+ ====================
    CONCURRENT_OPERATION(4901, "操作过于频繁，请稍后重试"),
    RESOURCE_ALREADY_TAKEN(4902, "该资源已被他人操作"),
    //QUOTATION_NOT_EXISTS(4903, "报价单不存在");

    // ==================== AI 小助手 5000+ ====================
    /**
     * 上游（DeepSeek 等）不可用：连接失败、返回 5xx、响应体解析不了。
     *
     * <p>对用户统一说「稍后重试」，具体原因（HTTP 状态码、上游错误体）只进日志 ——
     * 上游的报错文案对用户没有意义，还可能把内部信息带出去。
     */
    AI_SERVICE_UNAVAILABLE(5001, "AI 服务暂时不可用，请稍后重试"),
    /** 上游响应超时（连接或读取）。 */
    AI_TIMEOUT(5002, "AI 服务响应超时，请稍后重试"),
    /**
     * LLM 请求调用了一个未注册的工具。
     *
     * <p>正常情况下不会发生 —— 工具清单是我们自己发给上游的。出现即说明上游返回了
     * 未经请求的工具名（模型幻觉，或响应被篡改），必须拒绝，而不是「尽力执行」。
     */
    AI_TOOL_NOT_ALLOWED(5003, "不支持的工具调用"),
    /** 工具调用轮数达到上限 —— 防模型绕圈，既有成本也有时延。 */
    AI_TOOL_ROUNDS_EXCEEDED(5004, "AI 查询步骤过多，请把问题拆小一点再问"),
    /** 提问或携带的历史过长，超出模型上下文。 */
    AI_PROMPT_TOO_LONG(5005, "提问内容过长，请精简后重试"),
    /**
     * AI 未启用：没配 api-key，或 {@code ai.enabled=false}。
     *
     * <p>刻意做成**运行期**错误而不是启动失败 —— AI 是可选能力，
     * 缺 key 不该让整个 CRM 起不来（与 OSS 的 fail-fast 取向不同，理由见 {@code AiConfig}）。
     */
    AI_NOT_CONFIGURED(5006, "AI 小助手未启用，请联系管理员配置模型");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
