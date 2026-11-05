package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.crm.model.dto.QuotationPageReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSaveReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.QuotationRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationSimpleRespVO;
import com.aurum.crm.module.crm.service.QuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商机报价单 Controller，对应原型「商机报价单」页。
 * 路由见《05-接口文档》5.2 报价单。
 *
 * <p><b>本轮实现的接口</b>：
 * <pre>
 *   GET    /crm/quotation                    分页列表（含商品总数、整单利润、关联合同）
 *   GET    /crm/quotation/simple-list        选择器专用（合同表单在调）
 *   GET    /crm/quotation/{id}               详情（**含产品明细**）
 *   POST   /crm/quotation                    新增（products 内嵌，一起落库）
 *   PUT    /crm/quotation/{id}               修改（products 内嵌，整份覆盖）
 *   DELETE /crm/quotation/{id}               删除（逻辑删除）
 *   POST   /crm/quotation/{id}/star          关注（幂等）
 *   DELETE /crm/quotation/{id}/star          取消关注（幂等）
 *   POST   /crm/quotation/{id}/convert-contract  报价转合同 ⭐（状态转「已生成合同」）
 *   POST   /crm/quotation/{id}/void          作废 ⭐（状态转「已失效」）
 * </pre>
 *
 * <p><b>状态流转只有两个写入口</b>（转合同 / 作废），口径见
 * {@code QuotationStatus} 的类注释与《状态流转图》§1。列表的状态列是**只读**的，
 * 前端表单里的状态下拉也已改成只读展示 —— 状态不能手改，只能由这两个动作驱动。
 *
 * <p><b>转合同复用同事已交付的合同创建接口</b>（{@code ContractService#create}）：
 * 编号规则、明细金额重算、草稿态都由合同模块负责，报价单侧不重复实现，
 * 因此**合同模块一行都不用改**。
 *
 * <p><b>明细没有独立端点</b>：前端把产品明细当成表单里的一个 {@code type: 'items'} 字段，
 * 和主单一起提交；《05-接口文档》5.2 以及 {@code sys_menu} 的权限点里都没有
 * {@code /crm/quotation/{id}/products} 或 {@code crm:quotation:addProduct}。
 * POST/PUT 的语义差别见 {@link QuotationSaveReqVO#getProducts()}。
 *
 * <p><b>本轮刻意未实现</b>（原因见 {@code docs/11-报价单模块字段契约.md}）：
 * {@code GET /crm/quotation/export} —— 项目没有 Excel 依赖，且 {@code sys_menu} 里
 * 没有 {@code crm:quotation:export} 权限点（与产品 / 商机模块同一待办）。
 *
 * <p><b>关于路由顺序</b>：{@code /simple-list} 声明在 {@code /{id}} 之前。
 * 这不是硬约束（{@code id} 是 {@code Long}，{@code "simple-list"} 绑定不上会落到更精确的映射），
 * 只是为了可读性。
 *
 * <p><b>鉴权说明</b>：本模块的 {@code @SaCheckPermission} 已补齐，
 * 权限码取自 {@code sql/crm.sql} 的 {@code sys_menu}：{@code crm:quotation:list|create|update|delete}。
 *
 * <p>⚠️ 本模块的权限点<b>比其他模块少</b>：种子里没有 {@code :export}、也没有
 * {@code :convertContract} / {@code :void}。这些端点一律复用最近的既有码
 * （导出与读操作 → {@code :list}，转合同与作废 → {@code :update}），
 * <b>不要凭空造新权限码</b> —— 不在 {@code sys_menu} 里的码会让所有非超管账号 403。
 */
@Tag(name = "商机报价单")
@RestController
@RequestMapping("/crm/quotation")
@Validated
public class QuotationController {

    @Resource
    private QuotationService quotationService;

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "报价单分页列表")
    @GetMapping
    public CommonResult<PageResult<QuotationRespVO>> page(QuotationPageReqVO reqVO) {
        return CommonResult.success(quotationService.pageQuery(reqVO));
    }

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "报价单选择器")
    @GetMapping("/simple-list")
    public CommonResult<PageResult<QuotationSimpleRespVO>> simpleList(QuotationSimplePageReqVO reqVO) {
        return CommonResult.success(quotationService.simpleList(reqVO));
    }

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "导出报价单（Excel → OSS，返回下载 URL）")
    @GetMapping("/export")
    public CommonResult<String> export(QuotationPageReqVO reqVO) {
        return CommonResult.success(quotationService.export(reqVO));
    }

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "导出报价单（XML/SpreadsheetML → OSS，返回下载 URL）")
    @GetMapping("/export-xml")
    public CommonResult<String> exportXml(QuotationPageReqVO reqVO) {
        return CommonResult.success(quotationService.exportXml(reqVO));
    }

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "报价单详情（含产品明细）")
    @GetMapping("/{id}")
    public CommonResult<QuotationRespVO> get(@PathVariable("id") Long id) {
        return CommonResult.success(quotationService.get(id));
    }

    @SaCheckPermission("crm:quotation:create")
    @Operation(summary = "新增报价单（products 内嵌，一起落库）")
    @PostMapping
    public CommonResult<Long> create(@Valid @RequestBody QuotationSaveReqVO reqVO) {
        return CommonResult.success(quotationService.create(reqVO));
    }

    @SaCheckPermission("crm:quotation:update")
    @Operation(summary = "修改报价单（products 内嵌；传 null 不动明细，传 [] 清空）")
    @PutMapping("/{id}")
    public CommonResult<Boolean> update(@PathVariable("id") Long id,
                                        @Valid @RequestBody QuotationSaveReqVO reqVO) {
        quotationService.update(id, reqVO);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:quotation:delete")
    @Operation(summary = "删除报价单")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable("id") Long id) {
        quotationService.delete(id);
        return CommonResult.success(true);
    }

    /**
     * 关注 / 取消关注。
     *
     * <p>与线索 / 客户 / 商机同一形态（{@code POST /crm/opportunity/{id}/star}）：
     * 刻意做成 POST + DELETE 一对而不是单个 toggle 接口 —— toggle 不幂等，
     * 双击或请求重试会把状态翻回去，让图标和库里相反。写入由通用的
     * {@code StarService} + {@code crm_star}（{@code biz_type = 4}）承担。
     */
    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "关注报价单")
    @PostMapping("/{id}/star")
    public CommonResult<Boolean> star(@PathVariable("id") Long id) {
        quotationService.star(id);
        return CommonResult.success(true);
    }

    @SaCheckPermission("crm:quotation:list")
    @Operation(summary = "取消关注报价单")
    @DeleteMapping("/{id}/star")
    public CommonResult<Boolean> unstar(@PathVariable("id") Long id) {
        quotationService.unstar(id);
        return CommonResult.success(true);
    }

    /**
     * 报价单转合同。
     *
     * <p>按报价明细生成一份**合同草稿**并把报价单置为「已生成合同」，返回新合同 ID。
     * 一个报价单可以出多份合同（1:N），所以「已生成合同」的单子还能再转。
     * 已失效的报价单、以及没有任何明细的报价单会被 {@code 2252} 拒绝，
     * 具体口径见 {@link QuotationService#convertToContract}。
     *
     * <p><b>权限</b>：复用 {@code crm:quotation:update} ——
     * {@code sys_menu} 里没有 {@code crm:quotation:convertContract} 这个权限点，
     * 而<b>不能</b>在这里写一个不存在的权限码（会让所有非超管账号直接 403）。
     * 转合同会生成一份合同草稿，本质是改报价单的状态，用 {@code :update} 语义最贴。
     * 若将来在 {@code sys_menu} 补齐 {@code :convertContract}（并给角色授权），
     * 换成它更精确。
     *
     * <p><b>注意</b>：本端点是在服务端内部调用合同创建逻辑，因此不会经过
     * {@code ContractController} 上的 {@code crm:contract:create} 校验。
     * 即「能操作报价单的人就能转出合同」。业务上这是想要的
     * （转合同是报价单页自身的动作），但权限体系落地时要一并考虑。
     */
    @SaCheckPermission("crm:quotation:update")
    @Operation(summary = "报价单转合同（生成合同草稿，报价单转为「已生成合同」）")
    @PostMapping("/{id}/convert-contract")
    public CommonResult<Long> convertContract(@PathVariable("id") Long id) {
        return CommonResult.success(quotationService.convertToContract(id));
    }

    /**
     * 作废报价单。
     *
     * <p>状态落到「已失效」（库值 4 已作废）。**只有「报价中」的报价单可以作废**：
     * 已生成合同的单子已有下游合同，先去处理合同；已失效是终态。两者都抛 {@code 2252}。
     */
    @SaCheckPermission("crm:quotation:update")
    @Operation(summary = "作废报价单（状态转为「已失效」）")
    @PostMapping("/{id}/void")
    public CommonResult<Boolean> voidQuotation(@PathVariable("id") Long id) {
        quotationService.voidQuotation(id);
        return CommonResult.success(true);
    }
}
