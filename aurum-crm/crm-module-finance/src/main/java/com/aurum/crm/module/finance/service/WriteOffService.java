package com.aurum.crm.module.finance.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.model.dto.WriteOffPageReqVO;
import com.aurum.crm.module.finance.model.vo.WriteOffRespVO;

/** 核销记录服务：发票-回款明细的平铺查询。 */
public interface WriteOffService {

    /** 分页查询核销记录（接口文档 7.3 GET /finance/write-off）。 */
    PageResult<WriteOffRespVO> pageQuery(WriteOffPageReqVO reqVO);
}
