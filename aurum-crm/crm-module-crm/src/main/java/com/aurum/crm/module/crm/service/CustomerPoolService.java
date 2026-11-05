package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerPoolAssignReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;

/**
 * 公海池 Service 接口：公海列表 / 领取 / 分配 / 删除。
 *
 * <p>公海定义为 {@code owner_user_id IS NULL} 的客户（见《02-建表文档》）。
 * 领取采用「UPDATE ... WHERE owner IS NULL」的原子 CAS 保证并发安全——
 * 两个用户同一时刻抢同一条，只有一个 update 受影响行数为 1，另一个抛 4902。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.CustomerPoolServiceImpl}。
 */
public interface CustomerPoolService {

    /**
     * 公海分页列表（恒 owner 为空，无需数据权限，公海对全员可见）。
     */
    PageResult<CustomerRespVO> poolPage(CustomerPageReqVO reqVO);

    /**
     * 领取公海客户（并发安全：原子 UPDATE 成功才算抢到）。
     */
    void claim(Long id);

    /**
     * 分配公海客户给指定负责人（管理员改派）。
     */
    void assign(Long id, CustomerPoolAssignReqVO reqVO);

    /**
     * 删除公海中的客户（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 导出公海客户（Excel → OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(CustomerPageReqVO reqVO);

    /**
     * 导出公海客户（XML/SpreadsheetML → OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(CustomerPageReqVO reqVO);
}
