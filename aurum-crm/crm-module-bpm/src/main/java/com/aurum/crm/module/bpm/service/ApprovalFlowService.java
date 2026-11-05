package com.aurum.crm.module.bpm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.bpm.model.SysApprovalFlowDO;
import com.aurum.crm.module.bpm.model.SysApprovalNodeDO;
import com.aurum.crm.module.bpm.model.dto.FlowSaveReqVO;
import com.aurum.crm.module.bpm.model.dto.NodeSaveReqVO;

import java.util.List;

/** 审批流模板与节点配置服务。 */
public interface ApprovalFlowService {

    /** 模板列表（分页，返回 DO 透出）。 */
    PageResult<SysApprovalFlowDO> pageQuery(Integer pageNo, Integer pageSize);

    /** 全部启用模板（供引擎选流），返回 DO 列表。 */
    List<SysApprovalFlowDO> listAll();

    /** 新增模板。 */
    Long create(FlowSaveReqVO reqVO);

    /** 修改模板。 */
    void update(Long id, FlowSaveReqVO reqVO);

    /** 删除模板（逻辑删除）。 */
    void delete(Long id);

    /** 某模板的节点列表（按 node_order 升序）。 */
    List<SysApprovalNodeDO> nodesOfFlow(Long flowId);

    /** 覆盖式保存某模板的全部节点配置。 */
    void saveNodes(Long flowId, List<NodeSaveReqVO> nodes);
}
