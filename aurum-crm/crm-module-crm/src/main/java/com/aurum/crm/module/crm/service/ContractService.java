package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ContractAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractBatchAuditReqVO;
import com.aurum.crm.module.crm.model.dto.ContractPageReqVO;
import com.aurum.crm.module.crm.model.dto.ContractSaveReqVO;
import com.aurum.crm.module.crm.model.vo.ContractApprovalRespVO;
import com.aurum.crm.module.crm.model.vo.ContractRespVO;

public interface ContractService {
    PageResult<ContractRespVO> pageQuery(ContractPageReqVO req);
    PageResult<ContractRespVO> auditPage(ContractPageReqVO req);
    ContractRespVO get(Long id);

    /**
     * 合同审批进度：节点顺序、每节点结果与审批人、当前节点与还在等谁、数据异常提示。
     *
     * <p>纯查询。合同不存在或不在当前登录人的数据权限内抛 2301。
     */
    ContractApprovalRespVO approval(Long id);

    Long create(ContractSaveReqVO req);
    void update(Long id, ContractSaveReqVO req);
    void delete(Long id);
    void submit(Long id);
    void audit(Long id, ContractAuditReqVO req);
    void batchAudit(ContractBatchAuditReqVO req);
    void voidContract(Long id);
    void star(Long id);
    void unstar(Long id);
    String export(ContractPageReqVO req);
    String exportXml(ContractPageReqVO req);
}
