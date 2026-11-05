package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.ProductCategoryMapper;
import com.aurum.crm.module.crm.mapper.ProductMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.ProductCategoryDO;
import com.aurum.crm.module.crm.model.ProductDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.ProductCategoryConvert;
import com.aurum.crm.module.crm.model.dto.ProductCategoryPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductCategorySaveReqVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryExportRowVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryRespVO;
import com.aurum.crm.module.crm.service.ProductCategoryService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品分类 Service 实现。
 *
 * <p><b>树形结构的唯一不变量</b>：任一分类的 {@code ancestors} 必须等于
 * 「其父分类的 ancestors + ',' + 父分类 ID」，顶级分类固定为 {@code "0"}。
 * 本类维护这条不变量，共三处：
 * <ol>
 *     <li>新增 —— {@link #buildAncestors(Long)}</li>
 *     <li>移动父分类 —— {@link #moveDescendants(String, String)} 重算整棵子树</li>
 *     <li>删除 —— 有子分类时直接拒绝，避免留下悬空的孤儿节点</li>
 * </ol>
 *
 * <p>产品列表的「按分类筛选含子分类」也复用这里的 {@link #listSelfAndDescendantIds(Long)}。
 */
@Slf4j
@Service
public class ProductCategoryServiceImpl implements ProductCategoryService {

    /**
     * 顶级分类的 parent_id。
     *
     * <p>刻意声明为**基本类型 long**：写成 {@code Long} 时 {@code a == b} 会退化成
     * 引用比较，只因 {@code Long} 缓存了 -128~127 才「碰巧正确」，
     * 换成其它值或非缓存来源就会莫名判错。基本类型保证始终是值比较。
     */
    private static final long ROOT_PARENT_ID = 0L;

    /** 顶级分类的 ancestors 占位值（与建表脚本的 sys_dept 约定一致） */
    private static final String ROOT_ANCESTORS = "0";

    /** 分类状态：启用 */
    private static final Integer STATUS_ENABLED = 1;

    @Resource
    private ProductCategoryMapper productCategoryMapper;
    @Resource
    private ProductMapper productMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private ProductCategoryConvert productCategoryConvert;
    /** OSS 存储，用于导出 Excel 上传 */
    @Resource
    private OssStorageService ossStorageService;

    // ==================== 查询 ====================

    /**
     * 产品类型分页列表（扁平，不是嵌套树）。
     *
     * <p>为什么是扁平的：前端 {@code useTable} 硬性要求响应形如 {@code {list,total}}，
     * 而且产品类型页与产品表单的类型选择器都是平铺表格，没有层级 UI。
     * 层级数据本身没有丢 —— {@code parent_id}/{@code ancestors} 照旧维护，
     * 产品列表的「按分类含子分类筛选」仍走 {@link #listSelfAndDescendantIds(Long)}。
     */
    @Override
    public PageResult<ProductCategoryRespVO> pageList(ProductCategoryPageReqVO reqVO) {
        LambdaQueryWrapper<ProductCategoryDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            // 「类型编号」当前就是分类ID，所以纯数字关键字同时按 id 精确匹配，
            // 否则选择器里搜「4」会查不到任何东西
            Long idKeyword = parseId(keyword);
            wrapper.and(w -> {
                w.like(ProductCategoryDO::getCategoryName, keyword);
                if (idKeyword != null) {
                    w.or().eq(ProductCategoryDO::getCategoryId, idKeyword);
                }
            });
        }
        wrapper.orderByAsc(ProductCategoryDO::getSort)
                .orderByAsc(ProductCategoryDO::getCategoryId);

        Page<ProductCategoryDO> page = productCategoryMapper.selectPage(reqVO, wrapper);
        Map<Long, String> creatorNames = creatorNameMap(page.getRecords());
        List<ProductCategoryRespVO> list = page.getRecords().stream()
                .map(category -> toRespVO(category, creatorNames))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** DO → VO，并补上派生的 {@code code} 与跨表的 {@code creator} */
    private ProductCategoryRespVO toRespVO(ProductCategoryDO categoryDO, Map<Long, String> creatorNames) {
        ProductCategoryRespVO vo = productCategoryConvert.toRespVO(categoryDO);
        // 临时方案：类型编号 = 分类ID（表里没有编号列，前端表单那一项不落库）
        vo.setCode(String.valueOf(categoryDO.getCategoryId()));
        vo.setCreator(creatorNames.get(categoryDO.getCreateBy()));
        return vo;
    }

    /** 关键字是否是纯数字（用来兼作按类型编号搜索） */
    private Long parseId(String keyword) {
        if (!keyword.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.valueOf(keyword);
        } catch (NumberFormatException e) {
            // 超出 Long 范围的超长数字串：当作普通关键字处理
            return null;
        }
    }

    /** 批量取创建人姓名（前端「创建人」列显示姓名） */
    private Map<Long, String> creatorNameMap(List<ProductCategoryDO> records) {
        List<Long> userIds = records.stream()
                .map(ProductCategoryDO::getCreateBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            // ⚠️ 必须早返回：空集合会让 MP 生成非法的 IN () 语句。
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 是不可变 Map，
            //    它的 get(null) 会抛 NPE；而 crm.sql 初始化的分类 create_by 都是 NULL，
            //    补「创建人」时必然走到 get(null) —— 这正是类型列表 500 的原因。
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, userIds).stream()
                .filter(user -> user.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));
    }

    @Override
    public Set<Long> listSelfAndDescendantIds(Long categoryId) {
        if (categoryId == null) {
            return Set.of();
        }
        ProductCategoryDO category = productCategoryMapper.selectById(categoryId);
        if (category == null) {
            // 分类不存在（或已删除）→ 返回空集合，调用方据此返回空列表，不报错
            return Set.of();
        }
        String prefix = childPrefix(category);
        // ⚠️ 必须用 and(...) 把整组 OR 包起来：@TableLogic 会自动追加 del_flag = 0，
        //    顶层裸露的 OR 会与它连成 "del_flag = 0 AND a OR b"，
        //    因 AND 优先级高于 OR，b 分支就绕过了逻辑删除过滤 —— 会查到已删除的分类。
        List<ProductCategoryDO> list = productCategoryMapper.selectList(
                Wrappers.<ProductCategoryDO>lambdaQuery()
                        .and(w -> w.eq(ProductCategoryDO::getCategoryId, categoryId)
                                // 子孙：ancestors 恰好等于 prefix（直接子级），或以 "prefix," 开头（更深层）
                                .or().eq(ProductCategoryDO::getAncestors, prefix)
                                .or().likeRight(ProductCategoryDO::getAncestors, prefix + ",")));
        return list.stream()
                .map(ProductCategoryDO::getCategoryId)
                .collect(Collectors.toSet());
    }

    @Override
    public String export(ProductCategoryPageReqVO reqVO) {
        List<ProductCategoryExportRowVO> rows = buildExportRows();
        return ExportSupport.toUrl(ossStorageService, rows, ProductCategoryExportRowVO.class);
    }

    @Override
    public String exportXml(ProductCategoryPageReqVO reqVO) {
        List<ProductCategoryExportRowVO> rows = buildExportRows();
        return ExportSupport.toXmlUrl(ossStorageService, rows, ProductCategoryExportRowVO.class);
    }

    /** 导出用：产品分类全量查数并映射为导出行（不翻页） */
    private List<ProductCategoryExportRowVO> buildExportRows() {
        List<ProductCategoryDO> records = productCategoryMapper.selectList(
                Wrappers.<ProductCategoryDO>lambdaQuery()
                        .orderByAsc(ProductCategoryDO::getSort)
                        .orderByAsc(ProductCategoryDO::getCategoryId));
        Map<Long, ProductCategoryDO> byId = records.stream()
                .collect(Collectors.toMap(ProductCategoryDO::getCategoryId, c -> c, (a, b) -> a));
        return records.stream().map(c -> {
            ProductCategoryExportRowVO row = new ProductCategoryExportRowVO();
            row.setCategoryName(c.getCategoryName());
            ProductCategoryDO parent = (c.getParentId() == null || c.getParentId() == 0L)
                    ? null : byId.get(c.getParentId());
            row.setParentName(parent == null ? "" : parent.getCategoryName());
            row.setSort(c.getSort() == null ? "" : String.valueOf(c.getSort()));
            row.setStatus(Integer.valueOf(STATUS_ENABLED).equals(c.getStatus()) ? "启用" : "停用");
            row.setCreateTime(ExportSupport.timeStr(c.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCategorySaveReqVO reqVO) {
        Long parentId = normalizeParentId(reqVO.getParentId());
        String categoryName = trimToNull(reqVO.getName());
        validateNameUnique(parentId, categoryName, null);

        ProductCategoryDO categoryDO = productCategoryConvert.toDO(reqVO);
        categoryDO.setCategoryId(null);
        categoryDO.setParentId(parentId);
        categoryDO.setCategoryName(categoryName);
        categoryDO.setAncestors(buildAncestors(parentId));
        if (categoryDO.getSort() == null) {
            categoryDO.setSort(0);
        }
        if (categoryDO.getStatus() == null) {
            categoryDO.setStatus(STATUS_ENABLED);
        }
        productCategoryMapper.insert(categoryDO);
        return categoryDO.getCategoryId();
    }

    // ==================== 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductCategorySaveReqVO reqVO) {
        ProductCategoryDO existing = getCategoryDO(id);
        String categoryName = trimToNull(reqVO.getName());

        Long newParentId = reqVO.getParentId() == null
                ? existing.getParentId()
                : normalizeParentId(reqVO.getParentId());
        boolean parentChanged = !Objects.equals(newParentId, existing.getParentId());

        ProductCategoryDO update = productCategoryConvert.toDO(reqVO);
        update.setCategoryId(id);
        update.setCategoryName(categoryName);
        // ancestors 不接受前端输入；只有移动父分类时才由服务端重写
        update.setAncestors(null);

        if (!parentChanged) {
            // 只改名称/排序/状态。注意：全局 update-strategy=not_null，
            // 未传的字段不会被置空，所以这里不需要做「保留原值」的额外处理。
            validateNameUnique(existing.getParentId(), categoryName, id);
            productCategoryMapper.updateById(update);
            return;
        }

        String oldPrefix = childPrefix(existing);
        String newAncestors;
        if (Objects.equals(newParentId, ROOT_PARENT_ID)) {
            newAncestors = ROOT_ANCESTORS;
        } else {
            if (Objects.equals(id, newParentId)) {
                throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_PARENT_INVALID);
            }
            ProductCategoryDO newParent = getCategoryDO(newParentId);
            // 新父节点若落在「待移动子树」内，移动后树会成环
            if (isInsideSubtree(newParent.getAncestors(), oldPrefix)) {
                throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_PARENT_INVALID);
            }
            newAncestors = buildAncestors(newParentId);
        }
        validateNameUnique(newParentId, categoryName, id);

        update.setParentId(newParentId);
        update.setAncestors(newAncestors);
        productCategoryMapper.updateById(update);

        // 子孙的 ancestors 都以前缀 oldPrefix 开头，整体替换为新前缀
        moveDescendants(oldPrefix, newAncestors + "," + id);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getCategoryDO(id);

        Long childCount = productCategoryMapper.selectCount(
                Wrappers.<ProductCategoryDO>lambdaQuery()
                        .eq(ProductCategoryDO::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_HAS_CHILD);
        }

        // 逻辑外键的引用检查由应用层负责（建表文档 1.6）：
        // 分类是产品目录的组织方式，被引用时删除会让产品的 category_id 变成悬空值
        Long usedCount = productMapper.selectCount(
                Wrappers.<ProductDO>lambdaQuery()
                        .eq(ProductDO::getCategoryId, id));
        if (usedCount != null && usedCount > 0) {
            throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_IN_USE);
        }

        productCategoryMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    /** 按 ID 取分类，不存在直接抛业务异常 */
    private ProductCategoryDO getCategoryDO(Long id) {
        ProductCategoryDO categoryDO = productCategoryMapper.selectById(id);
        if (categoryDO == null) {
            throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_NOT_EXISTS);
        }
        return categoryDO;
    }

    /** parentId 为空按顶级处理 */
    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_PARENT_ID : parentId;
    }

    /**
     * 计算某个父分类下新建子节点的 {@code ancestors}。
     *
     * <p>父分类的 ancestors + ',' + 父分类 ID；顶级则为 {@code "0"}。
     */
    private String buildAncestors(Long parentId) {
        if (Objects.equals(parentId, ROOT_PARENT_ID)) {
            return ROOT_ANCESTORS;
        }
        ProductCategoryDO parent = getCategoryDO(parentId);
        return parent.getAncestors() + "," + parent.getCategoryId();
    }

    /**
     * 某个分类作为父节点时，其直接子节点的 {@code ancestors}。
     *
     * <p>即「自己的 ancestors + ',' + 自己」—— 也就是子树的前缀。
     */
    private String childPrefix(ProductCategoryDO category) {
        return category.getAncestors() + "," + category.getCategoryId();
    }

    /**
     * 判断一个节点的 {@code ancestors} 是否落在指定子树内（即它是该子树的成员）。
     *
     * <p>用于移动前的成环校验：先生成候选父节点的 {@code ancestors}，
     * 再看它是否已经在待移动子树里。
     */
    private boolean isInsideSubtree(String ancestors, String subtreePrefix) {
        if (!StringUtils.hasText(ancestors)) {
            return false;
        }
        return subtreePrefix.equals(ancestors) || ancestors.startsWith(subtreePrefix + ",");
    }

    /**
     * 重算整棵子树的 {@code ancestors}。
     *
     * <p>子孙的前缀一定是 {@code oldPrefix}，因此把这段前缀整体换成
     * {@code newPrefix} 即可，后缀（更深层的层级）原样保留。
     *
     * @param oldPrefix 移动前子树前缀，形如 {@code "0,1,4"}
     * @param newPrefix 移动后子树前缀，形如 {@code "0,2,4"}
     */
    private void moveDescendants(String oldPrefix, String newPrefix) {
        List<ProductCategoryDO> descendants = productCategoryMapper.selectList(
                Wrappers.<ProductCategoryDO>lambdaQuery()
                        .and(w -> w.eq(ProductCategoryDO::getAncestors, oldPrefix)
                                .or().likeRight(ProductCategoryDO::getAncestors, oldPrefix + ",")));
        for (ProductCategoryDO descendant : descendants) {
            String suffix = descendant.getAncestors().substring(oldPrefix.length());
            ProductCategoryDO update = new ProductCategoryDO();
            update.setCategoryId(descendant.getCategoryId());
            update.setAncestors(newPrefix + suffix);
            productCategoryMapper.updateById(update);
        }
        if (!descendants.isEmpty()) {
            log.info("[产品分类] 移动子分类祖先链：{} -> {}，影响 {} 个子孙节点",
                    oldPrefix, newPrefix, descendants.size());
        }
    }

    /** 同一父分类下不允许重名（否则树里出现无法分辨的重复项） */
    private void validateNameUnique(Long parentId, String categoryName, Long excludeId) {
        if (!StringUtils.hasText(categoryName)) {
            return;
        }
        LambdaQueryWrapper<ProductCategoryDO> wrapper = Wrappers.<ProductCategoryDO>lambdaQuery()
                .eq(ProductCategoryDO::getParentId, parentId)
                .eq(ProductCategoryDO::getCategoryName, categoryName);
        if (excludeId != null) {
            wrapper.ne(ProductCategoryDO::getCategoryId, excludeId);
        }
        Long count = productCategoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_NAME_EXISTS);
        }
    }

    /** 去首尾空格；空串当 null，避免存入空白名称 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
