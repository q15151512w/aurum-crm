package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.UnitPageReqVO;
import com.aurum.crm.module.system.model.dto.UnitSaveReqVO;
import com.aurum.crm.module.system.model.vo.UnitRespVO;
import com.aurum.crm.module.system.model.vo.UnitSimpleRespVO;

/**
 * 计量单位服务（《05-接口文档》3.6 基础配置，契约见《14-计量单位模块接口文档》）。
 *
 * <p>{@code sys_unit} 是**主数据**：产品模块按名字引用它（{@code crm_product.unit_id}），
 * 所以这里的写操作有三条硬约束：
 * <ol>
 *     <li><b>名称唯一</b>（{@code 1503}）—— 否则产品按名字解析单位的结果不可预测</li>
 *     <li><b>编码唯一</b>（{@code 1502}）—— 这条由 DDL 的 {@code uk_unit_code} 兜底</li>
 *     <li><b>被引用不许删</b>（{@code 1504}）—— 明细的 {@code unit_id} 是引用不是快照</li>
 * </ol>
 */
public interface UnitService {

    /** 分页查询。排序固定 {@code sort ASC, unit_id ASC} */
    PageResult<UnitRespVO> pageQuery(UnitPageReqVO reqVO);

    /** 新增，返回新单位ID */
    Long create(UnitSaveReqVO reqVO);

    /** 修改。未传的字段保持原值（全局 {@code update-strategy: not_null}） */
    void update(Long id, UnitSaveReqVO reqVO);

    /** 逻辑删除。被产品/单据引用时抛 {@code 1504} */
    void delete(Long id);

    /** 选择器：只返回启用（{@code status = 1}）的单位，供产品表单/明细编辑器的单位下拉使用 */
    PageResult<UnitSimpleRespVO> simplePage(UnitPageReqVO reqVO);
}
