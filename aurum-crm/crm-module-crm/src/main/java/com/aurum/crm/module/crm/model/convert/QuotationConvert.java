package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.QuotationDO;
import com.aurum.crm.module.crm.model.QuotationItemDO;
import com.aurum.crm.module.crm.model.QuotationStatus;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSaveReqVO;
import com.aurum.crm.module.crm.model.vo.DocumentItemRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationSimpleRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价单对象转换器（MapStruct，编译期生成实现）。
 *
 * <p><b>为什么有这么多 {@code @Mapping}</b>：DO 的字段名必须跟数据库列一一对应
 * （{@code quotation_name} / {@code status} / {@code total_with_tax}），
 * 而对外字段名要跟前端一致（{@code name} / 中文状态 / {@code finalAmount}）。
 * 两边名字不同，所以在这里显式建立对应关系 —— 只在这一处做映射，Service 层就全用前端字段名。
 * 与产品 / 商机模块的转换器是同一套做法。
 *
 * <p><b>本类还承担两条业务规则</b>（都是「一个字段两种表示」的换算，放在这里最省事）：
 * <ol>
 *     <li>{@code status}：{@code TINYINT} 码值 ↔ {@link QuotationStatus} 中文标签（读方向做多对一归并）</li>
 *     <li>{@code quoteDate}：{@code crm_quotation} 没有这一列，**派生自 {@code create_time} 的日期部分**
 *       （「报价日期就是建单日期」）。要改成别的口径，只改 {@link #dateTimeToDate} 一处</li>
 * </ol>
 *
 * <p>下面这些字段在 DO 里没有对应来源、由 Service 负责，因此一律 {@code ignore}：
 * {@code customerName} / {@code opportunityName} / {@code ownerName} / {@code creator}（跨表补值）、
 * {@code productCount} / {@code products}（按明细派生）、
 * {@code profit}（读时现算）、
 * {@code discount} / {@code finalAmount}（**取关联商机的折扣**并按它折算，见
 * {@code QuotationServiceImpl#toRespVO} —— 报价单自己没有折扣列）。
 */
@Mapper(componentModel = "spring")
public interface QuotationConvert {

    // ==================== DO → VO ====================

    @Mapping(target = "id", source = "quotationId")
    @Mapping(target = "name", source = "quotationName")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusCodeToLabel")
    @Mapping(target = "quoteDate", source = "createTime", qualifiedByName = "dateTimeToDate")
    @Mapping(target = "finalAmount", source = "totalWithTax")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "opportunityName", ignore = true)
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "discount", ignore = true)
    @Mapping(target = "profit", ignore = true)
    @Mapping(target = "productCount", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "starred", ignore = true)
    // 关联合同三件套：反查 crm_contract.quotation_id 得来，Service 负责批量补值（1:N，只给最新一份）
    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "contractNo", ignore = true)
    @Mapping(target = "contractCount", ignore = true)
    QuotationRespVO toRespVO(QuotationDO quotationDO);

    /**
     * 明细 DO → VO。
     *
     * <p>{@code unitName} 在 DO 里没有（库中只有 {@code unit_id}），由 Service 查 {@code sys_unit} 补值
     * —— 编辑器的单位下拉靠它回填，缺了会让用户一编辑就把单位清空。
     */
    @Mapping(target = "id", source = "itemId")
    @Mapping(target = "unitName", ignore = true)
    DocumentItemRespVO toItemRespVO(QuotationItemDO itemDO);

    @Mapping(target = "id", source = "quotationId")
    @Mapping(target = "name", source = "quotationName")
    @Mapping(target = "customerName", ignore = true)
    // 商机名称要跨表查 crm_opportunity，由 Service 批量补值（选择器响应里也要，理由见 VO 注释）
    @Mapping(target = "opportunityName", ignore = true)
    // 产品明细：DO 里没有这个来源，由 Service 按 quotation_id 批量查明细后补值
    // （合同表单靠它做「选中报价单 → 带出产品」的回显，见 QuotationServiceImpl#simpleList）
    @Mapping(target = "products", ignore = true)
    QuotationSimpleRespVO toSimpleRespVO(QuotationDO quotationDO);

    // ==================== 请求 → DO ====================

    /**
     * 主单请求 → DO。
     *
     * <p>以下字段刻意不在这里映射，由 Service 显式处理，因为它们都需要「查库 / 校验 / 换算」：
     * <ul>
     *     <li>{@code status} —— 要做「标签是否变了」的防漂移判断，不能直接覆盖</li>
     *     <li>{@code validDate} —— 请求是字符串（可能是空串），要宽容解析</li>
     *     <li>{@code quotationNo} / {@code version} —— 服务端生成</li>
     *     <li>{@code totalAmount} / {@code taxAmount} / {@code totalWithTax} —— 由明细重算，不采信客户端</li>
     * </ul>
     */
    @Mapping(target = "quotationId", ignore = true)
    @Mapping(target = "quotationNo", ignore = true)
    @Mapping(target = "quotationName", source = "name")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "totalWithTax", ignore = true)
    @Mapping(target = "validDate", ignore = true)
    @Mapping(target = "deptId", ignore = true)
    @Mapping(target = "status", ignore = true)
    QuotationDO toDO(QuotationSaveReqVO reqVO);

    /**
     * 明细请求 → DO。
     *
     * <p>由 Service 补齐/重算的字段：{@code quotationId}（主单建好才有）、
     * {@code productTypeId}（产品的分类快照）、{@code unitId}（按单位名解析）、
     * {@code amount}（3.6 重算）、{@code sort}（按提交顺序）。
     */
    @Mapping(target = "itemId", ignore = true)
    @Mapping(target = "quotationId", ignore = true)
    @Mapping(target = "productTypeId", ignore = true)
    @Mapping(target = "unitId", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "sort", ignore = true)
    QuotationItemDO toItemDO(DocumentItemSaveReqVO reqVO);

    // ==================== 字段换算规则 ====================

    /** 状态码值 → 中文标签（归并后的）；未知码值返回 {@code null}，读路径不能因脏数据整体失败 */
    @Named("statusCodeToLabel")
    default String statusCodeToLabel(Integer statusCode) {
        return QuotationStatus.labelOf(statusCode);
    }

    /**
     * 建单时间 → 报价日期。
     *
     * <p>{@code crm_quotation} 没有「报价日期」列，而对一张报价单来说
     * 「报价日期」就等于它的建单日期。派生而不是返回 null，是为了让列表列有值、
     * 编辑弹窗能回填 —— 否则这一项是必填的，用户每次编辑都要重选，选完还不落库。
     *
     * <p>⚠️ 前端表单里手改这一天不会生效（写路径完全忽略 {@code quoteDate}），
     * 这一条偏差写在 {@code docs/11-报价单模块字段契约.md} 里。
     */
    @Named("dateTimeToDate")
    default LocalDate dateTimeToDate(LocalDateTime createTime) {
        return createTime == null ? null : createTime.toLocalDate();
    }
}
