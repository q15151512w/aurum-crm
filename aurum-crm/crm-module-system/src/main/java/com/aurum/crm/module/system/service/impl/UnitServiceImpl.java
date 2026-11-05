package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.CrmContractItemMiniDO;
import com.aurum.crm.module.system.dal.dataobject.CrmOpportunityItemMiniDO;
import com.aurum.crm.module.system.dal.dataobject.CrmProductMiniDO;
import com.aurum.crm.module.system.dal.dataobject.CrmQuotationItemMiniDO;
import com.aurum.crm.module.system.dal.dataobject.SysUnitDO;
import com.aurum.crm.module.system.dal.dataobject.SysUserDO;
import com.aurum.crm.module.system.dal.mysql.CrmContractItemMiniMapper;
import com.aurum.crm.module.system.dal.mysql.CrmOpportunityItemMiniMapper;
import com.aurum.crm.module.system.dal.mysql.CrmProductMiniMapper;
import com.aurum.crm.module.system.dal.mysql.CrmQuotationItemMiniMapper;
import com.aurum.crm.module.system.dal.mysql.SysUnitMapper;
import com.aurum.crm.module.system.dal.mysql.SysUserMapper;
import com.aurum.crm.module.system.model.dto.UnitPageReqVO;
import com.aurum.crm.module.system.model.dto.UnitSaveReqVO;
import com.aurum.crm.module.system.model.vo.UnitRespVO;
import com.aurum.crm.module.system.model.vo.UnitSimpleRespVO;
import com.aurum.crm.module.system.service.UnitService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 计量单位实现（《05-接口文档》3.6 / 《14-计量单位模块接口文档》）。
 *
 * <p><b>为什么 4 张 crm 表出现在这里</b>：删除单位前必须确认没有产品 / 明细在引用它。
 * 依赖方向是 {@code crm-module-crm} → {@code crm-module-system}（反向依赖不存在），
 * 所以这里用 4 个<b>只读</b>精简投影直接计数，不写、也不加载业务对象 ——
 * 与 {@code crm-module-crm} 里只读投影 {@code sys_unit} / {@code sys_user} 的
 * {@code *MiniDO} 是同一个模式，只是方向相反。
 *
 * <p><b>金额无关</b>：单位只决定「显示什么单位」和「数量允许几位小数」，
 * 不参与任何金额公式，所以本类的写操作不会影响任何已保存的金额。
 */
@Slf4j
@Service
public class UnitServiceImpl implements UnitService {

    /**
     * 服务端自动生成单位编码时的前缀。
     *
     * <p>与 {@code ProductServiceImpl.PRODUCT_NO_PREFIX}（{@code CP} + 雪花ID）同一做法：
     * <b>常量前缀 + 雪花ID</b>，不做「查最大值 +1」那种并发下会撞唯一索引的方案。
     * ⚠️ {@code sys_numbering_rule} 里没有 {@code UNIT} 规则，不要为了这个字段去动编号规则引擎。
     */
    private static final String UNIT_CODE_PREFIX = "U";

    /** 新建时的列默认值，与 DDL 的 NOT NULL DEFAULT 保持一致 */
    private static final Integer DEFAULT_DECIMAL_PLACES = 0;
    private static final Integer DEFAULT_SORT = 0;
    private static final Integer STATUS_ENABLED = 1;

    @Resource
    private SysUnitMapper sysUnitMapper;
    @Resource
    private SysUserMapper sysUserMapper;

    // 只读投影：删除守卫用（只查不写）
    @Resource
    private CrmProductMiniMapper crmProductMiniMapper;
    @Resource
    private CrmQuotationItemMiniMapper crmQuotationItemMiniMapper;
    @Resource
    private CrmContractItemMiniMapper crmContractItemMiniMapper;
    @Resource
    private CrmOpportunityItemMiniMapper crmOpportunityItemMiniMapper;

    @Override
    public PageResult<UnitRespVO> pageQuery(UnitPageReqVO reqVO) {
        Page<SysUnitDO> page = sysUnitMapper.selectPage(reqVO, queryWrapper(reqVO, null));

        Map<Long, String> creators = creatorNames(page.getRecords());
        List<UnitRespVO> list = page.getRecords().stream()
                // ⚠️ 必须先判空再 get：create_by 允许为 NULL（sql/crm.sql 里的种子单位就是 NULL），
                //    而 creatorNames 在没人可查时返回的是**不可变空 Map**，
                //    Map.of().get(null) 会抛 NPE（ImmutableCollections$MapN.get 里有个 requireNonNull），
                //    整个列表接口会变成 500。空 Map 的实现细节不该漏到这里，所以两处都防。
                .map(unit -> toRespVO(unit, unit.getCreateBy() == null ? null : creators.get(unit.getCreateBy())))
                .collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageResult<UnitSimpleRespVO> simplePage(UnitPageReqVO reqVO) {
        // 强制只出启用单位：停用单位不该被新业务选中；而「已选中的还能不能用」由产品侧解析决定
        Page<SysUnitDO> page = sysUnitMapper.selectPage(reqVO, queryWrapper(reqVO, STATUS_ENABLED));

        List<UnitSimpleRespVO> list = page.getRecords().stream().map(unit -> {
            UnitSimpleRespVO vo = new UnitSimpleRespVO();
            vo.setId(unit.getUnitId());
            vo.setName(unit.getUnitName());
            vo.setCode(unit.getUnitCode());
            vo.setDecimalPlaces(unit.getDecimalPlaces());
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UnitSaveReqVO reqVO) {
        String name = reqVO.getName().trim();
        // 名称查重：走 ORM，@TableLogic 自动带 del_flag = 0，
        // 与唯一索引 uk_unit_code「只约束存活行」的语义保持一致（已删除的单位不占名字）
        if (sysUnitMapper.exists(SysUnitDO::getUnitName, name)) {
            throw new ServiceException(ErrorCode.UNIT_NAME_EXISTS);
        }

        SysUnitDO unit = new SysUnitDO();
        unit.setUnitName(name);
        unit.setUnitCode(resolveCode(reqVO.getCode(), null));
        unit.setDecimalPlaces(reqVO.getDecimalPlaces() == null ? DEFAULT_DECIMAL_PLACES : reqVO.getDecimalPlaces());
        unit.setSort(reqVO.getSort() == null ? DEFAULT_SORT : reqVO.getSort());
        unit.setStatus(reqVO.getStatus() == null ? STATUS_ENABLED : reqVO.getStatus());
        // ⚠️ reqVO.getRemark() 故意不落库：sys_unit 没有 remark 列（《14》1.2 有意偏差）

        sysUnitMapper.insert(unit);
        log.info("[计量单位] 新增 unitId={} 名称={} 编码={}", unit.getUnitId(), name, unit.getUnitCode());
        return unit.getUnitId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UnitSaveReqVO reqVO) {
        if (sysUnitMapper.selectById(id) == null) {
            throw new ServiceException(ErrorCode.UNIT_NOT_EXISTS);
        }

        String name = reqVO.getName().trim();
        // 查重时排除自己：把名字改成「自己原来的名字」不算冲突
        if (unitNameExistsExcluding(name, id)) {
            throw new ServiceException(ErrorCode.UNIT_NAME_EXISTS);
        }

        SysUnitDO update = new SysUnitDO();
        update.setUnitId(id);
        update.setUnitName(name);
        // ⚠️ code 传了空串要当作「没传」：全局 update-strategy=not_null 只跳过 null，
        //    空串会被真的写进 unit_code，单位编码就变成空的了。
        update.setUnitCode(StringUtils.hasText(reqVO.getCode()) ? resolveCode(reqVO.getCode(), id) : null);
        update.setDecimalPlaces(reqVO.getDecimalPlaces());
        update.setSort(reqVO.getSort());
        update.setStatus(reqVO.getStatus());

        sysUnitMapper.updateById(update);
        log.info("[计量单位] 修改 unitId={} 名称={}", id, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUnitDO unit = sysUnitMapper.selectById(id);
        if (unit == null) {
            throw new ServiceException(ErrorCode.UNIT_NOT_EXISTS);
        }

        String usage = describeUsage(id);
        if (usage != null) {
            throw new ServiceException(ErrorCode.UNIT_IN_USE, "该计量单位已被引用，无法删除：" + usage);
        }

        // 逻辑删除（BaseDO 的 @TableLogic 会把 DELETE 转成 UPDATE del_flag = 1）
        int affected = sysUnitMapper.deleteById(id);
        if (affected == 0) {
            // 并发：上面的 exists 检查通过之后、删除之前被别人删掉了
            throw new ServiceException(ErrorCode.UNIT_NOT_EXISTS);
        }
        log.info("[计量单位] 删除 unitId={} 名称={}", id, unit.getUnitName());
    }

    // ------------------------------------------------------------------
    //  内部方法
    // ------------------------------------------------------------------

    /**
     * 列表查询条件。
     *
     * @param forcedStatus 非 null 时强制按该状态筛选（选择器只出启用单位用）
     */
    private LambdaQueryWrapper<SysUnitDO> queryWrapper(UnitPageReqVO reqVO, Integer forcedStatus) {
        LambdaQueryWrapper<SysUnitDO> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            // 名称或编码命中即可。用 like 的参数绑定，不拼 SQL 字符串 ——
            // 单位名里出现 % / _ 时不会退化成通配符。
            wrapper.and(w -> w.like(SysUnitDO::getUnitName, keyword)
                    .or()
                    .like(SysUnitDO::getUnitCode, keyword));
        }

        Integer status = forcedStatus != null ? forcedStatus : reqVO.getStatus();
        if (status != null) {
            wrapper.eq(SysUnitDO::getStatus, status);
        }

        // 固定二级排序：保证分页结果稳定，翻页时不会串行
        wrapper.orderByAsc(SysUnitDO::getSort).orderByAsc(SysUnitDO::getUnitId);
        return wrapper;
    }

    /**
     * 单位编码：显式传了就用传入值（查重后返回），否则按 {@code U + 雪花ID} 生成。
     *
     * @param excludeId 修改场景传自己的 id，把「改回自己原来的编码」排除在查重之外
     */
    private String resolveCode(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            // 前端表单没有「单位编码」输入框，所以这条是常规路径而不是兜底
            return UNIT_CODE_PREFIX + IdUtil.getSnowflakeNextIdStr();
        }

        String trimmed = code.trim();
        LambdaQueryWrapper<SysUnitDO> wrapper = Wrappers.<SysUnitDO>lambdaQuery()
                .eq(SysUnitDO::getUnitCode, trimmed);
        if (excludeId != null) {
            wrapper.ne(SysUnitDO::getUnitId, excludeId);
        }
        if (sysUnitMapper.selectCount(wrapper) > 0) {
            throw new ServiceException(ErrorCode.UNIT_CODE_EXISTS);
        }
        return trimmed;
    }

    /** 名称是否已被「除自己以外」的单位占用 */
    private boolean unitNameExistsExcluding(String name, Long excludeId) {
        return sysUnitMapper.selectCount(Wrappers.<SysUnitDO>lambdaQuery()
                .eq(SysUnitDO::getUnitName, name)
                .ne(SysUnitDO::getUnitId, excludeId)) > 0;
    }

    /**
     * 该单位被哪些表引用了。
     *
     * <p>四张表的 {@code unit_id} 都是<b>引用</b>而不是快照（明细行只有
     * {@code product_name} / {@code unit_price} 是快照），所以删掉单位会让历史单据的
     * 「单位」列变成 {@code --} —— 这是必须挡住的数据完整性问题，不能只提示。
     *
     * @return {@code null} 表示没人引用、可以删；否则返回可读的明细文案
     */
    private String describeUsage(Long unitId) {
        long products = countRef(crmProductMiniMapper.selectCount(
                Wrappers.<CrmProductMiniDO>lambdaQuery().eq(CrmProductMiniDO::getUnitId, unitId)));
        long quotationItems = countRef(crmQuotationItemMiniMapper.selectCount(
                Wrappers.<CrmQuotationItemMiniDO>lambdaQuery().eq(CrmQuotationItemMiniDO::getUnitId, unitId)));
        long contractItems = countRef(crmContractItemMiniMapper.selectCount(
                Wrappers.<CrmContractItemMiniDO>lambdaQuery().eq(CrmContractItemMiniDO::getUnitId, unitId)));
        long opportunityItems = countRef(crmOpportunityItemMiniMapper.selectCount(
                Wrappers.<CrmOpportunityItemMiniDO>lambdaQuery().eq(CrmOpportunityItemMiniDO::getUnitId, unitId)));

        long total = products + quotationItems + contractItems + opportunityItems;
        if (total <= 0) {
            return null;
        }

        List<String> parts = new ArrayList<>(4);
        if (products > 0) {
            parts.add("产品 " + products);
        }
        if (quotationItems > 0) {
            parts.add("报价单明细 " + quotationItems);
        }
        if (contractItems > 0) {
            parts.add("合同明细 " + contractItems);
        }
        if (opportunityItems > 0) {
            parts.add("商机明细 " + opportunityItems);
        }
        // message 会直接弹给用户，所以要写成「删不掉 + 去哪解引用」都能看懂的样子
        return "共 " + total + " 条（" + String.join("、", parts) + "）";
    }

    /** {@code selectCount} 在某些 MyBatis-Plus 版本上可能返回 {@code null} */
    private long countRef(Long count) {
        return count == null ? 0L : count;
    }

    /** 批量查创建人姓名：一次查完，避免每行查一次用户表 */
    private Map<Long, String> creatorNames(List<SysUnitDO> units) {
        List<Long> ids = units.stream()
                .map(SysUnitDO::getCreateBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            // ⚠️ 这里**不能**写 Map.of()：它返回不可变空 Map，而 get(null) 会抛 NPE
            //    （种子数据的 create_by 就是 NULL）。Collections.emptyMap() 对任意 key 都返回 null。
            return Collections.emptyMap();
        }
        return sysUserMapper.selectList(SysUserDO::getUserId, ids).stream()
                .filter(user -> user.getUserId() != null && user.getRealName() != null)
                .collect(Collectors.toMap(SysUserDO::getUserId, SysUserDO::getRealName, (a, b) -> a));
    }

    private UnitRespVO toRespVO(SysUnitDO unit, String creator) {
        UnitRespVO vo = new UnitRespVO();
        vo.setId(unit.getUnitId());
        vo.setName(unit.getUnitName());
        vo.setCode(unit.getUnitCode());
        vo.setDecimalPlaces(unit.getDecimalPlaces());
        vo.setSort(unit.getSort());
        vo.setStatus(unit.getStatus());
        vo.setCreator(creator);
        vo.setCreateTime(unit.getCreateTime());
        vo.setUpdateTime(unit.getUpdateTime());
        return vo;
    }
}
