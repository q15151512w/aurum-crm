package com.aurum.crm.module.crm.support;

import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.OpportunityMapper;
import com.aurum.crm.module.crm.mapper.QuotationMapper;
import com.aurum.crm.module.crm.model.OpportunityDO;
import com.aurum.crm.module.crm.model.QuotationDO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 合同的三个关联字段（客户 / 商机 / 报价单）的**校验与对齐**。
 *
 * <p><b>为什么需要它</b>：这三列在库里都是「逻辑外键」，客户 NOT NULL、商机与报价单可空
 * （《02-建表文档》：合同可以直接挂在客户上，也可以从商机或报价单来）。校验原先只有
 * {@code ContractServiceImpl#checkCustomer} —— 只确认客户存在，**完全不看**商机与报价单。
 * 于是接口上能把「客户A + 商机B + 报价单C」直接存进去：合同列表的「报价单编号」列指向别人的单子，
 * 而金额、负责人、数据权限全挂在客户A上，界面上完全看不出对不上。
 *
 * <p><b>规则（与前端 {@code crm-ui} 的 {@code views/crm/contract/form-config.js} 逐条对应）</b>：
 * <ol>
 *     <li><b>存在性</b>：报价单必须存在（{@code 2251}）、商机必须存在（{@code 2201}）。
 *       越权与不存在走同一条路 —— {@code selectById} 被数据权限拦截器改写，
 *       取不到即视为不存在（与项目其它模块一致，不泄漏「存在但无权限」）。</li>
 *     <li><b>客户一致</b>：报价单 / 商机所属客户必须等于合同的客户，否则 {@code 2304}。</li>
 *     <li><b>商机对齐</b>：报价单挂了商机 → 商机**以报价单为准**；
 *       报价单没挂商机（{@code crm_quotation.opportunity_id} 可空）→ <b>保留</b>入参里用户选的商机。
 *       报价单是更晚、更具体的商务结论，两者冲突时它更权威。</li>
 * </ol>
 *
 * <p><b>客户为什么「只校验、不对齐」</b>：静默把合同改到另一个客户，会连带改变它的数据权限
 * （谁还能看见这张合同）与统计口径 —— 一次报错让用户重选，代价小得多。
 * 反过来，商机/报价单的对齐只影响「这张合同是从哪来的」，不影响权限，
 * 所以可以直接对齐（也让历史脏数据在保存时自愈）。
 *
 * <p>⚠️ <b>副作用</b>：历史数据里客户与商机/报价单本来就不一致的合同，**编辑保存会被 2304 拒绝**。
 * 这是刻意的取舍 —— 报错文案已经指了怎么改（重选一次报价单即自动对齐）。
 * 若某天决定改成「永不阻塞」（客户也按报价单自动对齐），只需把下面两处
 * {@code throw} 换成赋值 + {@code log.warn}，但请先想清楚数据权限的影响。
 */
@Slf4j
@Component
public class ContractRelationSupport {

    @Resource
    private QuotationMapper quotationMapper;
    @Resource
    private OpportunityMapper opportunityMapper;

    /**
     * 校验并「对齐」合同的三个关联字段。
     *
     * @param customerId   合同客户（必填，调用方已保证非空）
     * @param opportunityId 表单/请求里的商机 ID，可空
     * @param quotationId  表单/请求里的报价单 ID，可空
     * @return <b>应当落库的 {@code opportunityId}</b>（可能被报价单覆盖，也可能与入参相同）；
     *         三个都为空时返回 {@code null}（客户直签合同，合法）
     * @throws ServiceException 报价单/商机不存在（2251 / 2201），或客户与它们不一致（2304）
     */
    public Long alignAndValidate(Long customerId, Long opportunityId, Long quotationId) {
        Long alignedOpportunityId = opportunityId;

        if (quotationId != null) {
            QuotationDO quotation = quotationMapper.selectById(quotationId);
            if (quotation == null) {
                throw new ServiceException(ErrorCode.QUOTATION_NOT_EXISTS);
            }
            if (!Objects.equals(quotation.getCustomerId(), customerId)) {
                throw new ServiceException(ErrorCode.CONTRACT_RELATION_MISMATCH,
                        "所选报价单属于其它客户，请重新选择报价单");
            }
            // 报价单挂了商机 → 以它为准；没挂商机则保留用户选的商机（不能把商机抹成 null）
            if (quotation.getOpportunityId() != null) {
                if (!Objects.equals(quotation.getOpportunityId(), opportunityId)) {
                    log.info("[合同] 商机按报价单对齐：client={}, quotation={}, quotationId={}",
                            opportunityId, quotation.getOpportunityId(), quotationId);
                }
                alignedOpportunityId = quotation.getOpportunityId();
            }
        }

        if (alignedOpportunityId != null) {
            OpportunityDO opportunity = opportunityMapper.selectById(alignedOpportunityId);
            if (opportunity == null) {
                throw new ServiceException(ErrorCode.OPPORTUNITY_NOT_EXISTS);
            }
            if (!Objects.equals(opportunity.getCustomerId(), customerId)) {
                throw new ServiceException(ErrorCode.CONTRACT_RELATION_MISMATCH,
                        "所选商机属于其它客户，请重新选择商机");
            }
        }

        return alignedOpportunityId;
    }
}
