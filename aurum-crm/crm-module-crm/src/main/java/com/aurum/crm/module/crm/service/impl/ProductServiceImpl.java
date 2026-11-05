package com.aurum.crm.module.crm.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.ProductCategoryMapper;
import com.aurum.crm.module.crm.mapper.ProductMapper;
import com.aurum.crm.module.crm.mapper.SysUnitMiniMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.ProductCategoryDO;
import com.aurum.crm.module.crm.model.ProductDO;
import com.aurum.crm.module.crm.model.SysUnitMiniDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.ProductConvert;
import com.aurum.crm.module.crm.model.dto.ProductPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSaveReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.ProductExportRowVO;
import com.aurum.crm.module.crm.model.vo.ProductImageRespVO;
import com.aurum.crm.module.crm.model.vo.ProductRespVO;
import com.aurum.crm.module.crm.model.vo.ProductSimpleRespVO;
import com.aurum.crm.module.crm.service.ProductCategoryService;
import com.aurum.crm.module.crm.service.ProductService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品 Service 实现。
 *
 * <p><b>对外字段名对齐前端</b>（{@code crm-ui}）：方法签名与 VO 用
 * {@code name/code/salePrice/onShelf/imageUrl}，与数据库列的对应关系集中在
 * {@link ProductConvert}，本类里不再出现 DO 的原始字段名。对照表见
 * {@code docs/07-产品模块字段契约.md}。
 *
 * <p><b>为什么不做数据权限过滤</b>：{@code crm_product} 没有
 * {@code owner_user_id} / {@code dept_id}（见 {@code sql/crm.sql}），产品是全局目录；
 * 《04-权限设计文档》3.4 的数据权限参与表清单也不含产品表。
 *
 * <p><b>一个刻意的取舍</b>：产品名称搜索用 {@code LIKE}，而不是建表文档 5.1 推荐的
 * {@code MATCH ... AGAINST} 全文索引 —— ngram 分词器 {@code ngram_token_size} 默认是 2，
 * 单字关键词在 BOOLEAN MODE 下查不到结果，而产品是配置级数据量。
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    /** 产品编码前缀，与初始化数据的 {@code CP20260001} 保持同一前缀 */
    private static final String PRODUCT_NO_PREFIX = "CP";

    /** 下架 */
    private static final Integer STATUS_OFF_SHELF = 0;

    /** 上架 */
    private static final Integer STATUS_ON_SHELF = 1;

    /** 前端筛选胶囊「已上架」 */
    private static final String SCOPE_ON_SHELF = "onshelf";

    /** 前端筛选胶囊「未上架」 */
    private static final String SCOPE_OFF_SHELF = "offshelf";

    /** 产品图片在 OSS 上的业务目录（前端不能传，避免图片写到别的业务目录） */
    private static final String IMAGE_BIZ_TYPE = "product";

    /**
     * 产品图片允许的扩展名（带点，便于直接做后缀匹配）。
     *
     * <p>刻意**不含 svg**：SVG 可内嵌脚本，而 bucket 是公共读，等于送出一个存储型 XSS 落地页。
     */
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    /** 产品图片大小上限（MB），比 OSS 全局上限（10MB）更紧 */
    private static final int MAX_IMAGE_SIZE_MB = 5;

    @Resource
    private ProductMapper productMapper;
    @Resource
    private ProductCategoryMapper productCategoryMapper;
    @Resource
    private SysUnitMiniMapper sysUnitMiniMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private ProductCategoryService productCategoryService;
    /**
     * OSS 存储能力，来自 crm-module-system。
     *
     * <p>只注入 {@code OssStorageService}（纯存储，不写库），**不用** {@code FileService}：
     * 后者要求 {@code bizType} 与 {@code bizId} 同时提供，而产品图片是"先传图、再建产品"。
     */
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private ProductConvert productConvert;

    // ==================== 查询 ====================

    @Override
    public PageResult<ProductRespVO> pageQuery(ProductPageReqVO reqVO) {
        LambdaQueryWrapper<ProductDO> wrapper = Wrappers.lambdaQuery();
        // 前端搜索框占位是「产品名称/产品编码」，所以关键字同时匹配这两列
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(ProductDO::getProductName, keyword)
                    .or().like(ProductDO::getProductNo, keyword));
        }
        // 前端筛选胶囊：all / onShelf / offShelf
        applyScope(wrapper, reqVO.getScope());
        if (reqVO.getUnitId() != null) {
            wrapper.eq(ProductDO::getUnitId, reqVO.getUnitId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(ProductDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(ProductDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        if (reqVO.getCategoryId() != null) {
            Set<Long> categoryIds = productCategoryService.listSelfAndDescendantIds(reqVO.getCategoryId());
            if (categoryIds.isEmpty()) {
                // 分类不存在 / 已删除：返回空列表而不是报错（筛选条件失效不该中断查询）
                return emptyResult(reqVO);
            }
            wrapper.in(ProductDO::getCategoryId, categoryIds);
        }
        // 默认列表排序：最近创建在前（走 idx_create_time），带主键保证分页稳定
        wrapper.orderByDesc(ProductDO::getCreateTime)
                .orderByDesc(ProductDO::getProductId);

        Page<ProductDO> page = productMapper.selectPage(reqVO, wrapper);
        return toPageResult(page);
    }

    @Override
    public ProductRespVO get(Long id) {
        ProductDO productDO = getProductDO(id);
        ProductRespVO vo = productConvert.toRespVO(productDO);
        vo.setCategoryName(loadCategoryName(productDO.getCategoryId()));
        SysUnitMiniDO unit = loadUnit(productDO.getUnitId());
        if (unit != null) {
            vo.setUnitName(unit.getUnitName());
        }
        vo.setCreator(loadCreatorName(productDO.getCreateBy()));
        maskCostPriceIfNeeded(vo);
        return vo;
    }

    @Override
    public PageResult<ProductSimpleRespVO> simpleList(ProductSimplePageReqVO reqVO) {
        LambdaQueryWrapper<ProductDO> wrapper = Wrappers.lambdaQuery();
        // 选择器固定只给上架产品：下架产品不应该被商机 / 合同引用
        wrapper.eq(ProductDO::getStatus, STATUS_ON_SHELF);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(ProductDO::getProductName, keyword)
                    .or().like(ProductDO::getProductNo, keyword));
        }
        wrapper.orderByDesc(ProductDO::getCreateTime)
                .orderByDesc(ProductDO::getProductId);

        Page<ProductDO> page = productMapper.selectPage(reqVO, wrapper);
        return toSimplePageResult(page);
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductSaveReqVO reqVO) {
        validateCategoryExists(reqVO.getCategoryId());

        ProductDO productDO = productConvert.toDO(reqVO);
        productDO.setProductId(null);
        productDO.setProductNo(resolveProductNo(reqVO.getCode()));
        productDO.setUnitId(resolveUnitId(reqVO));
        applyDefaults(productDO);
        try {
            productMapper.insert(productDO);
        } catch (DuplicateKeyException e) {
            // uk_product_no(product_no, is_active) 兜底：前端传的编码重复
            throw new ServiceException(ErrorCode.PRODUCT_NO_EXISTS);
        }
        return productDO.getProductId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductSaveReqVO reqVO) {
        getProductDO(id);
        validateCategoryExists(reqVO.getCategoryId());

        ProductDO update = productConvert.toDO(reqVO);
        update.setProductId(id);
        update.setUnitId(resolveUnitId(reqVO));
        // 编码：前端编辑表单也会带 code，传了就以传的为准（唯一冲突会被 DB 挡下 → 2402）
        // 未传时为 null，配合 update-strategy=not_null 表示"不改这一列"
        try {
            productMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(ErrorCode.PRODUCT_NO_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getProductDO(id);
        // 历史单据（报价单 / 合同明细）靠 product_name、unit_price 等快照字段独立，
        // 因此逻辑删除产品不影响既有单据，也无需引用检查（建表文档 1.6）
        productMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShelf(Long id) {
        changeShelfStatus(id, STATUS_ON_SHELF);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Long id) {
        changeShelfStatus(id, STATUS_OFF_SHELF);
    }

    // ==================== 导出 ====================

    @Override
    public String export(ProductPageReqVO reqVO) {
        List<ProductExportRowVO> rows = buildExportRows(reqVO);
        return ExportSupport.toUrl(ossStorageService, rows, ProductExportRowVO.class);
    }

    @Override
    public String exportXml(ProductPageReqVO reqVO) {
        List<ProductExportRowVO> rows = buildExportRows(reqVO);
        return ExportSupport.toXmlUrl(ossStorageService, rows, ProductExportRowVO.class);
    }

    /** 导出用：复用列表查询条件查数并映射为导出行 */
    private List<ProductExportRowVO> buildExportRows(ProductPageReqVO reqVO) {
        List<ProductDO> records = listForExport(reqVO);
        Map<Long, String> categoryNames = categoryNameMap(records);
        Map<Long, SysUnitMiniDO> units = unitMap(records);
        return records.stream().map(p -> {
            ProductExportRowVO row = new ProductExportRowVO();
            row.setProductNo(p.getProductNo());
            row.setProductName(p.getProductName());
            row.setCategoryName(categoryNames.get(p.getCategoryId()));
            SysUnitMiniDO unit = units.get(p.getUnitId());
            row.setUnitName(unit == null ? null : unit.getUnitName());
            row.setPrice(p.getPrice() == null ? "" : p.getPrice().toPlainString());
            row.setCostPrice(p.getCostPrice() == null ? "" : p.getCostPrice().toPlainString());
            row.setTaxRate(p.getTaxRate() == null ? "" : p.getTaxRate().toPlainString());
            row.setStatus(Integer.valueOf(STATUS_ON_SHELF).equals(p.getStatus()) ? "上架" : "下架");
            row.setCreateTime(ExportSupport.timeStr(p.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    /** 导出用：复用列表查询条件取全部记录（不分页）；产品无数据权限过滤 */
    private List<ProductDO> listForExport(ProductPageReqVO reqVO) {
        LambdaQueryWrapper<ProductDO> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(reqVO.getKeyword())) {
            String keyword = reqVO.getKeyword().trim();
            wrapper.and(w -> w.like(ProductDO::getProductName, keyword)
                    .or().like(ProductDO::getProductNo, keyword));
        }
        applyScope(wrapper, reqVO.getScope());
        if (reqVO.getUnitId() != null) {
            wrapper.eq(ProductDO::getUnitId, reqVO.getUnitId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(ProductDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(ProductDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        if (reqVO.getCategoryId() != null) {
            Set<Long> categoryIds = productCategoryService.listSelfAndDescendantIds(reqVO.getCategoryId());
            if (categoryIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(ProductDO::getCategoryId, categoryIds);
        }
        wrapper.orderByDesc(ProductDO::getCreateTime)
                .orderByDesc(ProductDO::getProductId);
        return productMapper.selectList(wrapper);
    }

    // ==================== 图片上传 ====================

    /**
     * 上传产品图片到 OSS。
     *
     * <p><b>不需要失败补偿</b>：本方法没有任何数据库写入，不存在"对象传到 OSS、库里没记录"
     * 的孤儿问题；落库交给后续的产品保存（{@code imageUrl} 只是一个 URL 字符串）。
     *
     * <p><b>不写 {@code sys_attachment}</b>：产品图片的真相就是 {@code crm_product.image}；
     * 上传时产品还没保存、没有 productId，而 {@code FileService} 要求 bizType 与 bizId 成对出现。
     *
     * <p><b>不删除旧图片对象</b>：更新 / 删除产品只改数据库，OSS 对象保留 ——
     * 同一个 URL 可能被多个产品甚至外部页面引用，删错不可恢复，残留对象留待对账清理。
     */
    @Override
    public ProductImageRespVO uploadImage(MultipartFile file) {
        validateImage(file);

        // 业务类型固定 product → 对象落在 crm/product/{yyyy}/{MM}/{uuid}.{ext}
        String objectKey = ossStorageService.upload(file, IMAGE_BIZ_TYPE);

        ProductImageRespVO respVO = new ProductImageRespVO();
        respVO.setObjectKey(objectKey);
        respVO.setUrl(ossStorageService.buildUrl(objectKey));
        log.info("[产品图片] 上传成功：objectKey={}, size={}B", objectKey, file.getSize());
        return respVO;
    }

    // ==================== 内部方法 ====================

    /**
     * 上架筛选：{@code all} / {@code onShelf} / {@code offShelf}（大小写不敏感）。
     *
     * <p>未知取值**不筛**而不是筛空：前端以后多加一个胶囊值时，
     * 最坏情况是多显示几条，而不是整个列表变空且没人知道为什么。
     */
    private void applyScope(LambdaQueryWrapper<ProductDO> wrapper, String scope) {
        if (!StringUtils.hasText(scope)) {
            return;
        }
        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        if (SCOPE_ON_SHELF.equals(normalized)) {
            wrapper.eq(ProductDO::getStatus, STATUS_ON_SHELF);
        } else if (SCOPE_OFF_SHELF.equals(normalized)) {
            wrapper.eq(ProductDO::getStatus, STATUS_OFF_SHELF);
        }
    }

    /**
     * 产品编码：前端传了就用，没传才生成。
     *
     * <p>前端表单里编码是可填项（并注明"编号规则引擎就绪后改由后端生成"），
     * 唯一性由 {@code uk_product_no(product_no, is_active)} 兜底。
     */
    private String resolveProductNo(String code) {
        if (!StringUtils.hasText(code)) {
            // TODO 编号规则引擎：引擎落地后改为按 rule_code=PRODUCT 生成，只需改这一处
            return PRODUCT_NO_PREFIX + IdUtil.getSnowflakeNextIdStr();
        }
        return code.trim();
    }

    /**
     * 计量单位：优先用 {@code unitId}，否则按 {@code unitName} 解析。
     *
     * <p>前端的单位下拉是 {@code type:'select'} + 一组**单位名称**（台/个/箱…），
     * 提交体里只有名字，所以必须在这里解析成 {@code unit_id}。
     *
     * <p>⚠️ {@code sys_unit.unit_name} 没有唯一索引，所以用 {@code selectList} 取第一条，
     * <b>不能</b>用 {@code selectOne}（多条会抛 {@code TooManyResultsException}）。
     */
    private Long resolveUnitId(ProductSaveReqVO reqVO) {
        if (reqVO.getUnitId() != null) {
            if (sysUnitMiniMapper.selectById(reqVO.getUnitId()) == null) {
                throw new ServiceException(ErrorCode.PRODUCT_UNIT_NOT_EXISTS);
            }
            return reqVO.getUnitId();
        }
        if (!StringUtils.hasText(reqVO.getUnitName())) {
            return null;
        }
        String unitName = reqVO.getUnitName().trim();
        List<SysUnitMiniDO> matched = sysUnitMiniMapper.selectList(
                Wrappers.<SysUnitMiniDO>lambdaQuery()
                        .eq(SysUnitMiniDO::getUnitName, unitName)
                        .orderByAsc(SysUnitMiniDO::getUnitId));
        if (matched.isEmpty()) {
            throw new ServiceException(ErrorCode.PRODUCT_UNIT_NOT_EXISTS,
                    "计量单位不存在：" + unitName + "，可用单位：" + availableUnitNames());
        }
        return matched.get(0).getUnitId();
    }

    /** 库里现有的单位名，用于拼「可用单位：个/台/套」这类可操作的提示 */
    private String availableUnitNames() {
        List<SysUnitMiniDO> units = sysUnitMiniMapper.selectList(
                Wrappers.<SysUnitMiniDO>lambdaQuery().orderByAsc(SysUnitMiniDO::getUnitId));
        return units.stream()
                .map(SysUnitMiniDO::getUnitName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
    }

    /** 新建时的列默认值：与 DDL 的 NOT NULL DEFAULT 保持一致 */
    private void applyDefaults(ProductDO productDO) {
        if (productDO.getPrice() == null) {
            productDO.setPrice(BigDecimal.ZERO);
        }
        if (productDO.getCostPrice() == null) {
            productDO.setCostPrice(BigDecimal.ZERO);
        }
        if (productDO.getTaxRate() == null) {
            productDO.setTaxRate(BigDecimal.ZERO);
        }
        if (productDO.getStatus() == null) {
            // 前端 emptyForm 的 onShelf 默认 false，与 DDL 默认 0（下架）一致
            productDO.setStatus(STATUS_OFF_SHELF);
        }
    }

    /** 上下架：幂等，已处于目标状态时重复调用仍返回成功 */
    private void changeShelfStatus(Long id, Integer status) {
        getProductDO(id);
        ProductDO update = new ProductDO();
        update.setProductId(id);
        update.setStatus(status);
        productMapper.updateById(update);
    }

    /**
     * 成本价字段级权限的接入点。
     *
     * <p><b>TODO 字段级权限</b>：《04-权限设计文档》4.1 要求
     * {@code crm_product.cost_price} 仅 {@code admin} / {@code sales_director} 可见，
     * 其余角色返回 {@code null}（前端表格列已做 {@code --} 兜底）。
     *
     * <p>为什么现在不做：登录鉴权接口尚未实现，{@code LoginUser.roleKeys} 恒为 null，
     * 此时按角色过滤会让**所有人**都看不到成本价，反而给联调添乱。
     */
    private void maskCostPriceIfNeeded(ProductRespVO vo) {
        // 预留钩子：字段级权限落地后在此处按角色置空 costPrice
    }

    /** 产品图片校验：非空、大小、扩展名白名单 */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "产品图片不能为空");
        }
        long maxBytes = MAX_IMAGE_SIZE_MB * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, String.format(
                    "产品图片不能超过 %dMB，当前 %.2fMB",
                    MAX_IMAGE_SIZE_MB, file.getSize() / 1024.0 / 1024.0));
        }
        String filename = file.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        boolean allowed = IMAGE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!allowed) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "产品图片仅支持 " + String.join(" / ", IMAGE_EXTENSIONS.stream().sorted().toList()));
        }
    }

    /** 分页结果转 VO，并跨表补分类名 / 单位名 / 创建人名 */
    private PageResult<ProductRespVO> toPageResult(Page<ProductDO> page) {
        List<ProductDO> records = page.getRecords();
        Map<Long, String> categoryNames = categoryNameMap(records);
        Map<Long, SysUnitMiniDO> units = unitMap(records);
        Map<Long, String> creatorNames = creatorNameMap(records);
        List<ProductRespVO> list = records.stream().map(product -> {
            ProductRespVO vo = productConvert.toRespVO(product);
            vo.setCategoryName(categoryNames.get(product.getCategoryId()));
            SysUnitMiniDO unit = units.get(product.getUnitId());
            if (unit != null) {
                vo.setUnitName(unit.getUnitName());
            }
            vo.setCreator(creatorNames.get(product.getCreateBy()));
            maskCostPriceIfNeeded(vo);
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 选择器分页结果转 VO */
    private PageResult<ProductSimpleRespVO> toSimplePageResult(Page<ProductDO> page) {
        List<ProductDO> records = page.getRecords();
        Map<Long, String> categoryNames = categoryNameMap(records);
        Map<Long, SysUnitMiniDO> units = unitMap(records);
        List<ProductSimpleRespVO> list = records.stream().map(product -> {
            ProductSimpleRespVO vo = productConvert.toSimpleRespVO(product);
            vo.setCategoryName(categoryNames.get(product.getCategoryId()));
            SysUnitMiniDO unit = units.get(product.getUnitId());
            if (unit != null) {
                vo.setUnitName(unit.getUnitName());
            }
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 批量取分类名，避免 N+1 */
    private Map<Long, String> categoryNameMap(List<ProductDO> records) {
        List<Long> categoryIds = records.stream()
                .map(ProductDO::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            // ⚠️ 必须早返回：空集合会让 MP 生成非法的 IN () 语句。
            // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 返回的是
            //    ImmutableCollections，它的 get(null) 会抛 NPE；而 category_id 为 NULL
            //    的产品一定会走到 get(null) —— 这类行在库里真实存在（产品类型可以不选）。
            return Collections.emptyMap();
        }
        return productCategoryMapper.selectList(ProductCategoryDO::getCategoryId, categoryIds).stream()
                .filter(category -> category.getCategoryName() != null)
                .collect(Collectors.toMap(ProductCategoryDO::getCategoryId,
                        ProductCategoryDO::getCategoryName, (a, b) -> a));
    }

    /** 批量取计量单位，避免 N+1 */
    private Map<Long, SysUnitMiniDO> unitMap(List<ProductDO> records) {
        List<Long> unitIds = records.stream()
                .map(ProductDO::getUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (unitIds.isEmpty()) {
            // 同 categoryNameMap：空 Map 必须用 Collections.emptyMap()，否则 get(null) 抛 NPE
            return Collections.emptyMap();
        }
        return sysUnitMiniMapper.selectList(SysUnitMiniDO::getUnitId, unitIds).stream()
                .collect(Collectors.toMap(SysUnitMiniDO::getUnitId, unit -> unit, (a, b) -> a));
    }

    /**
     * 批量取创建人姓名（前端「创建人」列显示的是姓名，不是 id）。
     *
     * <p>{@code Collectors.toMap} 不接受 null 值，所以先过滤掉没有姓名的用户。
     */
    private Map<Long, String> creatorNameMap(List<ProductDO> records) {
        List<Long> userIds = records.stream()
                .map(ProductDO::getCreateBy)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            // ⚠️ crm.sql 的初始化数据没有写 create_by，所以「结果集里全是这类行」是常态，
            //    空 Map 用 Map.of() 的话，get(null) 会让整个列表接口 500
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, userIds).stream()
                .filter(user -> user.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));
    }

    private String loadCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ProductCategoryDO category = productCategoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryName();
    }

    private SysUnitMiniDO loadUnit(Long unitId) {
        return unitId == null ? null : sysUnitMiniMapper.selectById(unitId);
    }

    private String loadCreatorName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    /** 过滤条件命中「不存在的分类」时的空分页结果 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    private ProductDO getProductDO(Long id) {
        ProductDO productDO = productMapper.selectById(id);
        if (productDO == null) {
            throw new ServiceException(ErrorCode.PRODUCT_NOT_EXISTS);
        }
        return productDO;
    }

    /** 逻辑外键校验：分类必须存在（建表文档 1.6，应用层负责引用完整性） */
    private void validateCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (productCategoryMapper.selectById(categoryId) == null) {
            throw new ServiceException(ErrorCode.PRODUCT_CATEGORY_NOT_EXISTS);
        }
    }
}
