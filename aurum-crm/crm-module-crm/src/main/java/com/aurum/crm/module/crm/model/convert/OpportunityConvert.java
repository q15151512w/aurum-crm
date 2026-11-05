package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.OpportunityDO;
import com.aurum.crm.module.crm.model.OpportunityItemDO;
import com.aurum.crm.module.crm.model.OpportunityStage;
import com.aurum.crm.module.crm.model.OpportunityStageLogDO;
import com.aurum.crm.module.crm.model.dto.OpportunitySaveReqVO;
import com.aurum.crm.module.crm.model.vo.DocumentItemRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunitySimpleRespVO;
import com.aurum.crm.module.crm.model.vo.OpportunityStageLogRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 商机对象转换器（MapStruct，编译期生成实现）。
 *
 * <p><b>为什么有这么多 {@code @Mapping}</b>：DO 的字段名必须跟数据库列一一对应
 * （{@code opportunity_name} / {@code expected_close_date} / {@code amount} / {@code stage}），
 * 而对外字段名要跟前端一致（{@code name} / {@code expectDealTime} / {@code totalAmount} / 中文阶段）。
 * 两边名字不同，所以在这里显式建立对应关系 —— 只在这一处做映射，
 * Service 层就全用前端字段名。这与产品模块 {@code ProductConvert} 是同一套做法。
 *
 * <p>{@code customerName} / {@code ownerName} / {@code creator} 在 DO 中没有对应字段，
 * 由 Service 跨表批量补值，这里一律 {@code ignore}；
 * 而 {@code nextContactTime} / {@code finalAmount} / {@code productKinds} / {@code productCount}
 * 是「库里还没有 / 由别的字段派生」的，也由 Service 统一赋值
 * （见 {@code OpportunityServiceImpl#applyDerivedFields}）。
 */
@Mapper(componentModel = "spring")
public interface OpportunityConvert {

    // ==================== DO → VO ====================

    @Mapping(target = "id", source = "opportunityId")
    @Mapping(target = "name", source = "opportunityName")
    @Mapping(target = "stage", source = "stage", qualifiedByName = "stageCodeToLabel")
    @Mapping(target = "expectDealTime", source = "expectedCloseDate")
    @Mapping(target = "totalAmount", source = "amount")
    @Mapping(target = "discount", source = "discountRate")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "finalAmount", ignore = true)
    @Mapping(target = "productKinds", ignore = true)
    @Mapping(target = "productCount", ignore = true)
    @Mapping(target = "products", ignore = true)
    @Mapping(target = "starred", ignore = true)
    OpportunityRespVO toRespVO(OpportunityDO opportunityDO);

    /**
     * 明细 DO → VO。
     *
     * <p>{@code unitName} 在 DO 里没有（库中只有 {@code unit_id}），由 Service 查 {@code sys_unit} 补值
     * —— 详情页编辑器的单位下拉靠它回填，缺了会让用户一编辑就把单位清空。
     */
    @Mapping(target = "id", source = "itemId")
    @Mapping(target = "unitName", ignore = true)
    DocumentItemRespVO toItemRespVO(OpportunityItemDO itemDO);

    @Mapping(target = "id", source = "opportunityId")
    @Mapping(target = "name", source = "opportunityName")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "ownerName", ignore = true)
    // 产品明细：DO 里没有这个来源，由 Service 按 opportunity_id 批量查明细后补值
    // （合同表单在商机没有报价单时靠它带出产品，见 OpportunityServiceImpl#simpleList）
    @Mapping(target = "products", ignore = true)
    OpportunitySimpleRespVO toSimpleRespVO(OpportunityDO opportunityDO);

    @Mapping(target = "id", source = "logId")
    @Mapping(target = "fromStage", source = "fromStage", qualifiedByName = "stageCodeToLabel")
    @Mapping(target = "toStage", source = "toStage", qualifiedByName = "stageCodeToLabel")
    @Mapping(target = "time", source = "createTime")
    @Mapping(target = "remark", source = "changeReason")
    @Mapping(target = "operator", ignore = true)
    OpportunityStageLogRespVO toStageLogRespVO(OpportunityStageLogDO logDO);

    // ==================== 请求 → DO ====================

    /**
     * 请求 → DO。
     *
     * <p>下面这几项刻意不在这里映射，而是由 Service 显式处理，因为它们都需要
     * 「查库 / 校验 / 转换」，放在转换器里会把异常语义藏起来：
     * <ul>
     *     <li>{@code stage} —— 请求是中文标签，要校验合法性并给出「可填哪些值」的提示</li>
     *     <li>{@code expectedCloseDate} —— 请求是字符串（可能是空串），要宽容解析</li>
     *     <li>{@code opportunityNo} / {@code deptId} / {@code actualCloseDate} —— 服务端生成</li>
     *     <li>{@code contactId} / {@code source} —— 前端表单没有这两项，本轮不写入</li>
     * </ul>
     *
     * <p>{@code discountRate} 反而**直接映射**（{@code discount → discountRate}）：
     * 靠全局 {@code update-strategy: not_null} 天然得到我们要的语义 ——
     * 新增时缺省由 Service 兜成 0，编辑时不传就表示「不改这一列」。
     */
    @Mapping(target = "opportunityId", ignore = true)
    @Mapping(target = "opportunityNo", ignore = true)
    @Mapping(target = "opportunityName", source = "name")
    @Mapping(target = "amount", source = "totalAmount")
    @Mapping(target = "discountRate", source = "discount")
    @Mapping(target = "stage", ignore = true)
    @Mapping(target = "expectedCloseDate", ignore = true)
    @Mapping(target = "nextContactTime", ignore = true)
    @Mapping(target = "actualCloseDate", ignore = true)
    @Mapping(target = "deptId", ignore = true)
    @Mapping(target = "contactId", ignore = true)
    @Mapping(target = "source", ignore = true)
    OpportunityDO toDO(OpportunitySaveReqVO reqVO);

    // ==================== 阶段码值 ↔ 中文标签 ====================

    /**
     * 码值 → 中文标签。未知码值原样返回 {@code null}（读路径不能因为脏数据整体失败）。
     *
     * <p>注意 {@code @Named} 的方法也参与 {@code null} 判断：源值为 {@code null} 时
     * MapStruct 默认直接跳过取值，只有显式声明 {@code nullValueMappingStrategy} 才会调用。
     */
    @Named("stageCodeToLabel")
    default String stageCodeToLabel(Integer stageCode) {
        return OpportunityStage.labelOf(stageCode);
    }
}
