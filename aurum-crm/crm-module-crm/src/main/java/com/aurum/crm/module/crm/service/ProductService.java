package com.aurum.crm.module.crm.service;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.ProductPageReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSaveReqVO;
import com.aurum.crm.module.crm.model.dto.ProductSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.ProductImageRespVO;
import com.aurum.crm.module.crm.model.vo.ProductRespVO;
import com.aurum.crm.module.crm.model.vo.ProductSimpleRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 产品 Service：列表 / 详情 / 新增 / 修改 / 删除 / 上下架 / 选择器。
 *
 * <p>路由见《05-接口文档》5.3 产品与分类。产品是全局目录，不做数据权限过滤。
 *
 * <p>实现类：{@link com.aurum.crm.module.crm.service.impl.ProductServiceImpl}。
 */
public interface ProductService {

    /**
     * 产品分页列表。
     *
     * <p>{@code categoryId} 过滤是**含子分类**的：选中父分类能看到其下所有产品。
     */
    PageResult<ProductRespVO> pageQuery(ProductPageReqVO reqVO);

    /**
     * 产品详情。
     */
    ProductRespVO get(Long id);

    /**
     * 新增产品。编号由服务端生成。
     *
     * @return 新增产品的 ID
     */
    Long create(ProductSaveReqVO reqVO);

    /**
     * 修改产品。产品编号不可变更。
     */
    void update(Long id, ProductSaveReqVO reqVO);

    /**
     * 删除产品（逻辑删除）。
     *
     * <p>历史单据明细靠 product_name / unit_price 等快照字段独立，因此不做引用检查；
     * 逻辑删除后 {@code is_active} 生成列变 NULL，同一产品编号可被重新使用。
     */
    void delete(Long id);

    /**
     * 上架（{@code status = 1}）。幂等：已上架时重复调用仍成功。
     */
    void onShelf(Long id);

    /**
     * 下架（{@code status = 0}）。幂等。
     */
    void offShelf(Long id);

    /**
     * 产品选择器（通用选择器接口约定，见《05-接口文档》第十节）。
     *
     * <p>默认只返回上架产品 —— 下架产品不应被商机 / 合同引用。
     */
    PageResult<ProductSimpleRespVO> simpleList(ProductSimplePageReqVO reqVO);

    /**
     * 上传产品图片到 OSS，返回可直接渲染的 URL。
     *
     * <p>调用方拿到 {@code url} 后填进产品表单的 {@code image} 字段再保存产品
     * （"先传图、再建产品"，上传时还没有 productId）。
     *
     * <p>限制为图片类型且不超过 5MB；<b>不写 {@code sys_attachment}</b> ——
     * 产品图片的真相就是 {@code crm_product.image} 这个字段，不需要附件列表。
     */
    ProductImageRespVO uploadImage(MultipartFile file);

    /**
     * 导出产品为 Excel，上传 OSS 后返回下载 URL。
     *
     * <p>复用列表查询条件但取全部结果（不分页）；产品是全局目录，无数据权限过滤。
     */
    String export(ProductPageReqVO reqVO);

    /**
     * 导出产品为 XML/SpreadsheetML，上传 OSS 后返回下载 URL。
     *
     * <p>复用列表查询条件但取全部结果（不分页）；产品是全局目录，无数据权限过滤。
     */
    String exportXml(ProductPageReqVO reqVO);
}
