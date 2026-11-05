package com.aurum.crm.module.system.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.system.model.dto.DictDataPageReqVO;
import com.aurum.crm.module.system.model.dto.DictDataSaveReqVO;
import com.aurum.crm.module.system.model.dto.DictTypePageReqVO;
import com.aurum.crm.module.system.model.dto.DictTypeSaveReqVO;
import com.aurum.crm.module.system.model.vo.DictDataRespVO;
import com.aurum.crm.module.system.model.vo.DictTypeRespVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 字典管理（《05-接口文档》3.5）。
 *
 * <p><b>本模块管的是「字典能不能改」，不负责「改了生不生效」。</b>
 * 页面上能增删改字典项，但真正决定下拉框显示什么的是前端
 * {@code constants/options.js} 与后端 {@code DictLabels.java} 里的**硬编码** ——
 * 它们尚未改为读本服务（属于后续工作）。不要在页面上暗示「改完立刻生效」。
 */
public interface DictService {

    // ==================== 字典类型 ====================

    PageResult<DictTypeRespVO> pageType(DictTypePageReqVO reqVO);

    Long createType(DictTypeSaveReqVO reqVO);

    /** 修改。⚠️ {@code type}（编码）**不更新** —— 业务代码按它取值，改了等于换了个字典 */
    void updateType(Long id, DictTypeSaveReqVO reqVO);

    /** 删除（逻辑删除）。下面还有字典项时拒绝，避免留下孤儿数据 */
    void deleteType(Long id);

    // ==================== 字典项 ====================

    PageResult<DictDataRespVO> pageData(DictDataPageReqVO reqVO);

    Long createData(DictDataSaveReqVO reqVO);

    /** 修改。⚠️ {@code dictType} **不更新** —— 不允许把一项从 A 字典挪到 B 字典 */
    void updateData(Long id, DictDataSaveReqVO reqVO);

    void deleteData(Long id);

    // ==================== 批量读取 ====================

    /**
     * 按类型批量取**启用**的字典项，供前端一次性缓存（下拉选项的数据源）。
     *
     * <p>只返回启用项：停用的项不该出现在业务下拉里。管理页要看全部状态请走
     * {@link #pageData}（那是管理视角，两边的口径本就不同）。
     *
     * @return {@code { 字典类型编码: [字典项…] }}，按 {@code type} 分组、组内按 sort 升序。
     *         传入空集合返回空 Map，不查库。
     */
    Map<String, List<DictDataRespVO>> dataByTypes(Collection<String> types);
}
