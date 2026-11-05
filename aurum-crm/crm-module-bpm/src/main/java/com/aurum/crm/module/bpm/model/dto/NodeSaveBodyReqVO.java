package com.aurum.crm.module.bpm.model.dto;

import lombok.Data;

import java.util.List;

/** 一次性保存某流程全部节点配置的请求体（覆盖式）。 */
@Data
public class NodeSaveBodyReqVO {

    /** 完整节点列表（按 node_order 排序），保存时整体替换该流程的节点 */
    private List<NodeSaveReqVO> nodes;
}
