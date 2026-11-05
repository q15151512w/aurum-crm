package com.aurum.crm.module.bpm.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.bpm.mapper.ApprovalFlowMapper;
import com.aurum.crm.module.bpm.mapper.ApprovalNodeMapper;
import com.aurum.crm.module.bpm.model.SysApprovalFlowDO;
import com.aurum.crm.module.bpm.model.SysApprovalNodeDO;
import com.aurum.crm.module.bpm.model.dto.FlowSaveReqVO;
import com.aurum.crm.module.bpm.model.dto.NodeSaveReqVO;
import com.aurum.crm.module.bpm.model.dto.NodeSaveBodyReqVO;
import com.aurum.crm.module.bpm.service.ApprovalFlowService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 审批流模板与节点配置服务实现。
 *
 * <p>{@code sys_approval_node} 用「整批删重插」维护节点配置：节点表没有唯一键，
 * 且节点顺序可变，覆盖式保存最干净。删除时先删节点再删模板（节点无软删被引用问题，
 * 但保持一致先把节点清掉）。
 */
@Service
public class ApprovalFlowServiceImpl implements ApprovalFlowService {

    @Resource
    private ApprovalFlowMapper approvalFlowMapper;
    @Resource
    private ApprovalNodeMapper approvalNodeMapper;

    @Override
    public PageResult<SysApprovalFlowDO> pageQuery(Integer pageNo, Integer pageSize) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 500);
        Page<SysApprovalFlowDO> page = approvalFlowMapper.selectPage(new Page<>(no, size),
                Wrappers.<SysApprovalFlowDO>lambdaQuery()
                        .orderByAsc(SysApprovalFlowDO::getFlowId));
        return new PageResult<>(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<SysApprovalFlowDO> listAll() {
        return approvalFlowMapper.selectList(
                Wrappers.<SysApprovalFlowDO>lambdaQuery()
                        .eq(SysApprovalFlowDO::getStatus, 1)
                        .orderByAsc(SysApprovalFlowDO::getFlowId));
    }

    @Override
    public Long create(FlowSaveReqVO reqVO) {
        if (!StringUtils.hasText(reqVO.getFlowCode()) || !StringUtils.hasText(reqVO.getFlowName())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程编码与名称不能为空");
        }
        if (approvalFlowMapper.exists(SysApprovalFlowDO::getFlowCode, reqVO.getFlowCode())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程编码已存在：" + reqVO.getFlowCode());
        }
        SysApprovalFlowDO flow = new SysApprovalFlowDO();
        flow.setFlowId(IdUtil.getSnowflakeNextId());
        flow.setFlowCode(reqVO.getFlowCode());
        flow.setFlowName(reqVO.getFlowName());
        flow.setBizType(reqVO.getBizType());
        flow.setStatus(reqVO.getStatus() == null ? 1 : reqVO.getStatus());
        flow.setRemark(reqVO.getRemark());
        approvalFlowMapper.insert(flow);
        return flow.getFlowId();
    }

    @Override
    public void update(Long id, FlowSaveReqVO reqVO) {
        SysApprovalFlowDO flow = approvalFlowMapper.selectById(id);
        if (flow == null) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS);
        }
        if (StringUtils.hasText(reqVO.getFlowCode())
                && !reqVO.getFlowCode().equals(flow.getFlowCode())
                && approvalFlowMapper.exists(SysApprovalFlowDO::getFlowCode, reqVO.getFlowCode())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "流程编码已存在：" + reqVO.getFlowCode());
        }
        if (StringUtils.hasText(reqVO.getFlowCode())) {
            flow.setFlowCode(reqVO.getFlowCode());
        }
        if (StringUtils.hasText(reqVO.getFlowName())) {
            flow.setFlowName(reqVO.getFlowName());
        }
        if (StringUtils.hasText(reqVO.getBizType())) {
            flow.setBizType(reqVO.getBizType());
        }
        if (reqVO.getStatus() != null) {
            flow.setStatus(reqVO.getStatus());
        }
        if (reqVO.getRemark() != null) {
            flow.setRemark(reqVO.getRemark());
        }
        approvalFlowMapper.updateById(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysApprovalFlowDO flow = approvalFlowMapper.selectById(id);
        if (flow == null) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS);
        }
        approvalNodeMapper.delete(Wrappers.<SysApprovalNodeDO>lambdaQuery()
                .eq(SysApprovalNodeDO::getFlowId, id));
        approvalFlowMapper.deleteById(id);
    }

    @Override
    public List<SysApprovalNodeDO> nodesOfFlow(Long flowId) {
        return approvalNodeMapper.selectList(
                Wrappers.<SysApprovalNodeDO>lambdaQuery()
                        .eq(SysApprovalNodeDO::getFlowId, flowId)
                        .orderByAsc(SysApprovalNodeDO::getNodeOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveNodes(Long flowId, List<NodeSaveReqVO> nodes) {
        SysApprovalFlowDO flow = approvalFlowMapper.selectById(flowId);
        if (flow == null) {
            throw new ServiceException(ErrorCode.APPROVAL_FLOW_NOT_EXISTS);
        }
        approvalNodeMapper.delete(Wrappers.<SysApprovalNodeDO>lambdaQuery()
                .eq(SysApprovalNodeDO::getFlowId, flowId));
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (NodeSaveReqVO n : nodes) {
            SysApprovalNodeDO node = new SysApprovalNodeDO();
            node.setNodeId(IdUtil.getSnowflakeNextId());
            node.setFlowId(flowId);
            node.setNodeOrder(n.getNodeOrder());
            node.setNodeName(n.getNodeName());
            node.setApproveType(n.getApproveType() == null ? 1 : n.getApproveType());
            node.setApproverType(n.getApproverType() == null ? 1 : n.getApproverType());
            node.setApproverValue(n.getApproverValue());
            node.setAllowReject(n.getAllowReject() == null ? 1 : n.getAllowReject());
            node.setRemark(n.getRemark());
            approvalNodeMapper.insert(node);
        }
    }
}
