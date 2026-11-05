package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.dal.dataobject.SysOperationLogDO;
import com.aurum.crm.module.system.model.dto.OperationLogPageReqVO;
import com.aurum.crm.module.system.model.vo.OperationLogRespVO;

/**
 * 操作日志服务。
 *
 * <p>与 {@code LoginLogService} 一样分两组职责：
 * <ol>
 *     <li><b>查询</b> —— {@link #pageQuery}，给前端「操作日志」页用</li>
 *     <li><b>写入</b> —— {@link #record}，由 {@code OperationLogAspect} 调用。
 *         注意没有任何接口能改或删日志</li>
 * </ol>
 */
public interface OperationLogService {

    /**
     * 分页查询。排序固定 {@code oper_time DESC, log_id DESC}。
     *
     * <p>{@code keyword} 同时匹配 操作人（oper_name）与 模块（title）。
     */
    PageResult<OperationLogRespVO> pageQuery(OperationLogPageReqVO reqVO);

    /**
     * 落一条操作日志。
     *
     * <p><b>内部自行消化异常</b>：调用方是切面的 finally 块，
     * 日志写失败绝不能让用户的业务请求失败。
     */
    void record(SysOperationLogDO logDO);
}
