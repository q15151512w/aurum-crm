package com.aurum.crm.module.crm.model.convert;

import com.aurum.crm.module.crm.model.ContractAuditStatus;
import com.aurum.crm.module.crm.model.ContractDO;
import com.aurum.crm.module.crm.model.ContractItemDO;
import com.aurum.crm.module.crm.model.ContractStatus;
import com.aurum.crm.module.crm.model.dto.ContractItemReqVO;
import com.aurum.crm.module.crm.model.dto.ContractSaveReqVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 合同对象转换器（MapStruct）。
 *
 * <ul>
 *     <li>{@code id} / {@code name} 是给前端通用列表组件用的别名，分别取自
 *         {@code contractId} / {@code contractName} —— 与 {@code CustomerConvert} /
 *         {@code LeadConvert} / {@code OpportunityConvert} 同一套做法；</li>
 *     <li>{@code finalAmount} 是 {@code amount} 的前端列名别名（同一个含税金额）；</li>
 *     <li>{@code auditStatus} / {@code status} 由 TINYINT 码值转成中文标签，
 *         与 {@code OpportunityStage} / {@code QuotationStatus} 同一套做法；</li>
 *     <li>{@code customerName} / {@code opportunityName} / {@code quotationNo} /
 *         {@code creator} 在 DO 中无对应字段（或只有 ID），由 Service 跨表批量补值，
 *         这里显式 {@code ignore}，把「这些字段由谁负责」写进契约。</li>
 * </ul>
 *
 * <p>⚠️ 本接口原本是**没有任何 {@code @Mapping} 的纯同名拷贝**，于是合同列表的主键是
 * {@code undefined}（详情页跳到 {@code /crm/contract/undefined}），
 * 且审核态/合同状态返回的是裸码值。
 */
@Mapper(componentModel = "spring")
public interface ContractConvert {

    @Mapping(source = "contractId", target = "id")
    @Mapping(source = "contractName", target = "name")
    @Mapping(source = "amount", target = "finalAmount")
    @Mapping(source = "auditStatus", target = "auditStatus", qualifiedByName = "auditStatusToLabel")
    @Mapping(source = "status", target = "status", qualifiedByName = "statusToLabel")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "opportunityName", ignore = true)
    @Mapping(target = "quotationNo", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "starred", ignore = true)
    ContractRespVO toVO(ContractDO contract);

    ContractDO toDO(ContractSaveReqVO req);

    ContractItemDO toItemDO(ContractItemReqVO req);

    ContractItemReqVO toItemReq(ContractItemDO item);

    // ==================== 状态码值 ↔ 中文标签 ====================

    /**
     * 审核状态码值 → 中文标签。未知码值原样返回 {@code null}
     * （读路径不能因为一条脏数据让整个列表接口 500，前端用 {@code dash()} 兜底成 {@code --}）。
     */
    @Named("auditStatusToLabel")
    default String auditStatusToLabel(Integer code) {
        return ContractAuditStatus.labelOf(code);
    }

    /** 合同履约状态码值 → 中文标签，未知码值返回 {@code null} */
    @Named("statusToLabel")
    default String statusToLabel(Integer code) {
        return ContractStatus.labelOf(code);
    }
}
