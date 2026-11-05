package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ProductCategoryPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductCategorySaveReqVO;
import com.aurum.crm.module.crm.model.vo.ProductCategoryRespVO;

import java.util.Set;

/**
 * 产品类型（分类）Service。
 *
 * <p><b>关于接口路径</b>：路由是 {@code GET /crm/product-category/tree}
 * （《05-接口文档》5.3 也这么写），但**返回的是扁平分页列表**而不是嵌套树 ——
 * 前端 {@code useTable} 硬性要求响应是 {@code {list,total}}，且产品类型页没有层级 UI。
 * 路径名保留是为了不动前端；数据库里的层级（{@code parent_id}/{@code ancestors}）
 * 仍然维护，产品列表的「按分类含子分类筛选」也继续依赖它。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.ProductCategoryServiceImpl}。
 */
public interface ProductCategoryService {

    /**
     * 产品类型分页列表（扁平分页，供产品类型页与产品表单的选择器共用）。
     *
     * @param reqVO 分页参数 + {@code keyword}（匹配类型名称；纯数字时同时匹配类型编号）
     */
    PageResult<ProductCategoryRespVO> pageList(ProductCategoryPageReqVO reqVO);

    /**
     * 新增类型。
     *
     * <p>{@code ancestors} 由服务端根据父分类推导，前端不需要（也不能）指定。
     *
     * @return 新增类型的 ID
     */
    Long create(ProductCategorySaveReqVO reqVO);

    /**
     * 修改类型。
     *
     * <p>父分类变化时连带重算整棵子树的 {@code ancestors}；父分类不能是自己或自己的子孙。
     */
    void update(Long id, ProductCategorySaveReqVO reqVO);

    /**
     * 删除类型（逻辑删除）。
     *
     * <p>有子分类、或已被产品引用时拒绝删除。
     */
    void delete(Long id);

    /**
     * 取「该分类 + 其所有子孙分类」的 ID 集合，供产品列表按分类过滤复用。
     *
     * @param categoryId 分类ID
     * @return ID 集合；分类不存在或已删除时返回空集合（调用方据此返回空列表）
     */
    Set<Long> listSelfAndDescendantIds(Long categoryId);

    /**
     * 导出产品分类为 Excel，上传 OSS 后返回下载 URL。
     */
    String export(ProductCategoryPageReqVO reqVO);

    /**
     * 导出产品分类为 XML/SpreadsheetML，上传 OSS 后返回下载 URL。
     */
    String exportXml(ProductCategoryPageReqVO reqVO);
}
