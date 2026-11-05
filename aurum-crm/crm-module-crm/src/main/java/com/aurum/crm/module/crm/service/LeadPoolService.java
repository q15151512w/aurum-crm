package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadPoolAssignReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;

/**
 * 线索池 Service 接口：线索池列表 / 领取 / 分配 / 删除。
 *
 * <p>线索池定义为 {@code owner_user_id IS NULL} 的线索（见《02-建表文档》）。
 * 领取采用「UPDATE ... WHERE owner IS NULL」的原子 CAS 保证并发安全——
 * 两个用户同一时刻抢同一条，只有一个 update 受影响行数为 1，另一个抛 4902。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.LeadPoolServiceImpl}。
 */
public interface LeadPoolService {

    /**
     * 线索池分页列表（恒 owner 为空，无需数据权限，线索池对全员可见）。
     */
    PageResult<LeadRespVO> poolPage(LeadPageReqDTO reqDTO);

    /**
     * 领取线索池线索（并发安全：原子 UPDATE 成功才算抢到）。
     */
    void claim(Long id);

    /**
     * 分配线索池线索给指定负责人（管理员改派）。
     */
    void assign(Long id, LeadPoolAssignReqDTO reqDTO);

    /**
     * 删除线索池中的线索（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 导出线索池线索（Excel → OSS → 返回下载 URL），含当前过滤条件。
     */
    String export(LeadPageReqDTO reqDTO);

    /**
     * 导出线索池线索（XML/SpreadsheetML → OSS → 返回下载 URL），含当前过滤条件。
     */
    String exportXml(LeadPageReqDTO reqDTO);
}
