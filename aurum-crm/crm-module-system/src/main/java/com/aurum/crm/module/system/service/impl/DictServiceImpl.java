package com.aurum.crm.module.system.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.DictDataDO;
import com.aurum.crm.module.system.dal.dataobject.DictTypeDO;
import com.aurum.crm.module.system.dal.mysql.DictDataMapper;
import com.aurum.crm.module.system.dal.mysql.DictTypeMapper;
import com.aurum.crm.module.system.model.dto.DictDataPageReqVO;
import com.aurum.crm.module.system.model.dto.DictDataSaveReqVO;
import com.aurum.crm.module.system.model.dto.DictTypePageReqVO;
import com.aurum.crm.module.system.model.dto.DictTypeSaveReqVO;
import com.aurum.crm.module.system.model.vo.DictDataRespVO;
import com.aurum.crm.module.system.model.vo.DictTypeRespVO;
import com.aurum.crm.module.system.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典管理实现。
 *
 * <p><b>状态字段为什么要在这里转来转去</b>：本项目对**状态机枚举字段**的约定是
 * 「接口上走中文标签，后端负责转换」（BUG-002 确立的字段口径约定，同
 * {@code ContractStatus} / {@code OpportunityStage}）。而库里 {@code status} 是
 * {@code TINYINT}(0 停用 / 1 正常)，所以进出各转一次，集中在下面两个私有方法里，
 * 不要在别处再写一遍映射。
 */
@Slf4j
@Service
public class DictServiceImpl implements DictService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final String LABEL_ENABLED = "正常";
    private static final String LABEL_DISABLED = "停用";

    private static final int DEFAULT_SORT = 0;
    private static final int NOT_DEFAULT = 0;
    private static final int IS_DEFAULT = 1;

    @Resource
    private DictTypeMapper dictTypeMapper;
    @Resource
    private DictDataMapper dictDataMapper;

    // ==================== 字典类型 ====================

    @Override
    public PageResult<DictTypeRespVO> pageType(DictTypePageReqVO reqVO) {
        LambdaQueryWrapper<DictTypeDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            // 名称或编码命中即可。用 like 的参数绑定，不拼 SQL 字符串 ——
            // 关键字里出现 % / _ 时不会退化成通配符
            wrapper.and(w -> w.like(DictTypeDO::getDictName, keyword)
                    .or()
                    .like(DictTypeDO::getDictType, keyword));
        }
        if (reqVO.getStatus() != null) {
            wrapper.eq(DictTypeDO::getStatus, reqVO.getStatus());
        }
        // 固定二级排序，保证分页稳定、翻页不串行
        wrapper.orderByAsc(DictTypeDO::getDictId);

        Page<DictTypeDO> page = dictTypeMapper.selectPage(reqVO, wrapper);
        List<DictTypeRespVO> list = page.getRecords().stream()
                .map(this::toTypeVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createType(DictTypeSaveReqVO reqVO) {
        String type = reqVO.getType().trim();
        if (dictTypeMapper.exists(DictTypeDO::getDictType, type)) {
            throw new ServiceException(ErrorCode.DICT_TYPE_EXISTS);
        }

        DictTypeDO dictType = new DictTypeDO();
        dictType.setDictName(reqVO.getName().trim());
        dictType.setDictType(type);
        dictType.setStatus(toStatusCode(reqVO.getStatus()));
        dictType.setRemark(reqVO.getRemark());

        dictTypeMapper.insert(dictType);
        log.info("[字典] 新增类型 dictId={} type={}", dictType.getDictId(), type);
        return dictType.getDictId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, DictTypeSaveReqVO reqVO) {
        if (dictTypeMapper.selectById(id) == null) {
            throw new ServiceException(ErrorCode.DICT_TYPE_NOT_EXISTS);
        }

        DictTypeDO update = new DictTypeDO();
        update.setDictId(id);
        update.setDictName(reqVO.getName().trim());
        // ⚠️ 故意不设置 dictType：编码是业务代码取值用的 key，建成后不允许改。
        //    前端表单也把它置灰了，这里再兜一道 —— 光靠前端置灰挡不住直接调接口。
        update.setStatus(toStatusCode(reqVO.getStatus()));
        // 备注允许清空。全局 update-strategy=not_null 只跳过 null，
        // 传空串会被真的写进去（这正是「清空备注」想要的效果），
        // 而前端没传时是 undefined → JSON 里缺字段 → 反序列化成 null → 跳过。语义正好。
        update.setRemark(reqVO.getRemark());

        dictTypeMapper.updateById(update);
        log.info("[字典] 修改类型 dictId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        DictTypeDO dictType = dictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new ServiceException(ErrorCode.DICT_TYPE_NOT_EXISTS);
        }

        // 下面还有字典项就拒绝：逻辑删除类型后，那些项会变成查不到归属的孤儿数据，
        // 而 dict_type 只是字符串关联、没有外键约束，数据库不会替我们挡住。
        long dataCount = countRef(dictDataMapper.selectCount(
                Wrappers.<DictDataDO>lambdaQuery().eq(DictDataDO::getDictType, dictType.getDictType())));
        if (dataCount > 0) {
            throw new ServiceException(ErrorCode.DICT_TYPE_HAS_DATA,
                    "该字典类型下还有 " + dataCount + " 个字典项，无法删除。请先删除它们，或改为「停用」");
        }

        int affected = dictTypeMapper.deleteById(id);
        if (affected == 0) {
            // 并发：上面的检查通过之后、删除之前被别人删掉了
            throw new ServiceException(ErrorCode.DICT_TYPE_NOT_EXISTS);
        }
        log.info("[字典] 删除类型 dictId={} type={}", id, dictType.getDictType());
    }

    // ==================== 字典项 ====================

    @Override
    public PageResult<DictDataRespVO> pageData(DictDataPageReqVO reqVO) {
        LambdaQueryWrapper<DictDataDO> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(DictDataDO::getDictType, reqVO.getDictType());
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(DictDataDO::getDictLabel, keyword)
                    .or()
                    .like(DictDataDO::getDictValue, keyword));
        }
        if (reqVO.getStatus() != null) {
            wrapper.eq(DictDataDO::getStatus, reqVO.getStatus());
        }
        wrapper.orderByAsc(DictDataDO::getDictSort).orderByAsc(DictDataDO::getDictCode);

        Page<DictDataDO> page = dictDataMapper.selectPage(reqVO, wrapper);
        List<DictDataRespVO> list = page.getRecords().stream()
                .map(this::toDataVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createData(DictDataSaveReqVO reqVO) {
        String type = reqVO.getDictType().trim();
        requireTypeExists(type);
        String value = reqVO.getValue().trim();
        requireValueNotDuplicated(type, value, null);

        DictDataDO data = new DictDataDO();
        data.setDictType(type);
        data.setDictLabel(reqVO.getLabel().trim());
        data.setDictValue(value);
        data.setDictSort(reqVO.getSort() == null ? DEFAULT_SORT : reqVO.getSort());
        data.setStatus(toStatusCode(reqVO.getStatus()));
        data.setIsDefault(Boolean.TRUE.equals(reqVO.getIsDefault()) ? IS_DEFAULT : NOT_DEFAULT);
        data.setRemark(reqVO.getRemark());

        dictDataMapper.insert(data);
        log.info("[字典] 新增项 type={} label={} value={}", type, data.getDictLabel(), value);
        return data.getDictCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateData(Long id, DictDataSaveReqVO reqVO) {
        DictDataDO old = dictDataMapper.selectById(id);
        if (old == null) {
            throw new ServiceException(ErrorCode.DICT_DATA_NOT_EXISTS);
        }

        String value = reqVO.getValue().trim();
        // 查重时排除自己：把键值改成「原来的值」不算冲突
        requireValueNotDuplicated(old.getDictType(), value, id);

        DictDataDO update = new DictDataDO();
        update.setDictCode(id);
        update.setDictLabel(reqVO.getLabel().trim());
        update.setDictValue(value);
        update.setDictSort(reqVO.getSort());
        update.setStatus(toStatusCode(reqVO.getStatus()));
        if (reqVO.getIsDefault() != null) {
            update.setIsDefault(Boolean.TRUE.equals(reqVO.getIsDefault()) ? IS_DEFAULT : NOT_DEFAULT);
        }
        update.setRemark(reqVO.getRemark());
        // ⚠️ 故意不设置 dictType：不允许把一项从 A 字典挪到 B 字典。
        //    那等价于「删一条 + 加一条」，但会静默改变已引用该码值的业务数据含义。

        dictDataMapper.updateById(update);
        log.info("[字典] 修改项 dictCode={} type={}", id, old.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteData(Long id) {
        DictDataDO data = dictDataMapper.selectById(id);
        if (data == null) {
            throw new ServiceException(ErrorCode.DICT_DATA_NOT_EXISTS);
        }
        // 这里**不拦**「该码值是否已被业务数据引用」：那要扫遍所有业务表，
        // 成本高、收益低，而且**历史数据本来就该继续显示已删除码值的标签**
        // （字典项是逻辑删除，回显仍能查到）。与 unit 的删除守卫刻意不同。
        int affected = dictDataMapper.deleteById(id);
        if (affected == 0) {
            throw new ServiceException(ErrorCode.DICT_DATA_NOT_EXISTS);
        }
        log.info("[字典] 删除项 dictCode={} type={} value={}", id, data.getDictType(), data.getDictValue());
    }

    // ==================== 批量读取 ====================

    @Override
    public Map<String, List<DictDataRespVO>> dataByTypes(Collection<String> types) {
        if (types == null || types.isEmpty()) {
            // MyBatis-Plus 的 in() 对空集合会生成非法的 IN ()，必须先挡
            return Map.of();
        }
        List<String> distinct = types.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Map.of();
        }

        List<DictDataDO> rows = dictDataMapper.selectList(Wrappers.<DictDataDO>lambdaQuery()
                .in(DictDataDO::getDictType, distinct)
                // 只出启用项：停用的项不属于业务下拉的可选范围
                .eq(DictDataDO::getStatus, STATUS_ENABLED)
                .orderByAsc(DictDataDO::getDictSort)
                .orderByAsc(DictDataDO::getDictCode));

        // 用 LinkedHashMap：保持查询顺序，前端拿到的下拉顺序就是 sort 顺序
        Map<String, List<DictDataRespVO>> grouped = new LinkedHashMap<>();
        for (DictDataDO row : rows) {
            grouped.computeIfAbsent(row.getDictType(), k -> new java.util.ArrayList<>())
                    .add(toDataVO(row));
        }
        return grouped;
    }

    // ==================== 内部方法 ====================

    private void requireTypeExists(String dictType) {
        if (!dictTypeMapper.exists(DictTypeDO::getDictType, dictType)) {
            throw new ServiceException(ErrorCode.DICT_TYPE_NOT_EXISTS);
        }
    }

    /**
     * 同一字典类型下键值不能重复。
     *
     * <p>⚠️ 这条只能靠应用层：{@code sys_dict_data} 没有唯一键（见 {@code DictDataDO} 的类注释），
     * 并发插入仍可能漏过。但绝大多数情况是「管理页手动加了两个一样的」，这条能挡住。
     *
     * @param excludeId 修改场景传自己的 id，把「改回自己原来的键值」排除在外
     */
    private void requireValueNotDuplicated(String dictType, String value, Long excludeId) {
        LambdaQueryWrapper<DictDataDO> wrapper = Wrappers.<DictDataDO>lambdaQuery()
                .eq(DictDataDO::getDictType, dictType)
                .eq(DictDataDO::getDictValue, value);
        if (excludeId != null) {
            wrapper.ne(DictDataDO::getDictCode, excludeId);
        }
        if (dictDataMapper.selectCount(wrapper) > 0) {
            throw new ServiceException(ErrorCode.DICT_DATA_VALUE_EXISTS,
                    "字典「" + dictType + "」下已存在键值 " + value + "，请换一个");
        }
    }

    /** 中文标签 → 码值。未传按「正常」（与 DDL 的 DEFAULT 1 一致） */
    private Integer toStatusCode(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ENABLED;
        }
        return LABEL_DISABLED.equals(status.trim()) ? STATUS_DISABLED : STATUS_ENABLED;
    }

    /** 码值 → 中文标签。未知值（含 null）按「停用」—— 与 DDL 的 DEFAULT 相反，
     *  但这是刻意的：读到一个不认识的码值时不该假装它是「正常」 */
    private String toStatusLabel(Integer status) {
        return status != null && status == STATUS_ENABLED ? LABEL_ENABLED : LABEL_DISABLED;
    }

    /** {@code selectCount} 在某些 MyBatis-Plus 版本上可能返回 {@code null} */
    private long countRef(Long count) {
        return count == null ? 0L : count;
    }

    private DictTypeRespVO toTypeVO(DictTypeDO dictType) {
        DictTypeRespVO vo = new DictTypeRespVO();
        vo.setId(dictType.getDictId());
        vo.setName(dictType.getDictName());
        vo.setType(dictType.getDictType());
        vo.setStatus(toStatusLabel(dictType.getStatus()));
        vo.setRemark(dictType.getRemark());
        vo.setCreateTime(dictType.getCreateTime());
        return vo;
    }

    private DictDataRespVO toDataVO(DictDataDO data) {
        DictDataRespVO vo = new DictDataRespVO();
        vo.setId(data.getDictCode());
        vo.setType(data.getDictType());
        vo.setLabel(data.getDictLabel());
        vo.setValue(data.getDictValue());
        vo.setSort(data.getDictSort());
        vo.setStatus(toStatusLabel(data.getStatus()));
        vo.setIsDefault(data.getIsDefault() != null && data.getIsDefault() == IS_DEFAULT);
        vo.setListClass(data.getListClass());
        vo.setRemark(data.getRemark());
        vo.setCreateTime(data.getCreateTime());
        return vo;
    }
}
