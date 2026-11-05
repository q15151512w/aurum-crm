package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.DataScope;
import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.*;
import com.aurum.crm.module.crm.model.*;
import com.aurum.crm.module.crm.model.convert.QuotationConvert;
import com.aurum.crm.module.crm.model.dto.ContractItemReqVO;
import com.aurum.crm.module.crm.model.dto.ContractSaveReqVO;
import com.aurum.crm.module.crm.model.dto.DocumentItemSaveReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationPageReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSaveReqVO;
import com.aurum.crm.module.crm.model.dto.QuotationSimplePageReqVO;
import com.aurum.crm.module.crm.model.vo.DocumentItemRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationExportRowVO;
import com.aurum.crm.module.crm.model.vo.QuotationRespVO;
import com.aurum.crm.module.crm.model.vo.QuotationSimpleRespVO;
import com.aurum.crm.module.crm.service.ContractService;
import com.aurum.crm.module.crm.service.QuotationService;
import com.aurum.crm.module.crm.service.BusinessNumberService;
import com.aurum.crm.module.system.service.OssStorageService;
import com.aurum.crm.module.crm.service.StarService;
import com.aurum.crm.module.crm.support.ItemSupport;
import com.aurum.crm.module.crm.support.MoneyCalculator;
import com.aurum.crm.module.crm.support.SubordinateUserIds;
import com.aurum.crm.mybatis.core.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static com.aurum.crm.module.crm.service.impl.ExportSupport.timeStr;

/**
 * 报价单 Service 实现（含产品明细）。
 *
 * <p><b>对外字段名对齐前端</b>（{@code crm-ui}）：方法签名与 VO 用
 * {@code name/status/quoteDate/totalAmount/finalAmount/profit/products}，
 * 与数据库列的对应关系集中在 {@link QuotationConvert}。对照表见
 * {@code docs/11-报价单模块字段契约.md}。
 *
 * <p><b>金额是服务端唯一权威</b>：三个单据金额与每一行的 {@code amount} 都由
 * {@link MoneyCalculator}（也就是《02-建表文档》3.6）从明细重算并覆盖，
 * 客户端提交的金额只用来在值对不上时记一条 debug 日志。
 *
 * <p><b>整单利润</b>：读取时按 {@code crm_product.cost_price} 现算、不落库；
 * 有任意一行算不出成本就整单返回 {@code null}（前端显示 {@code --}）。
 *
 * <p><b>数据权限不在这里写</b> —— 由 {@code crm-mybatis} 的
 * {@code DataPermissionInnerInterceptor} 统一改写 SQL（{@code crm_quotation} 在它的白名单里），
 * 规则见《04-权限设计文档》第三节。{@code crm_quotation.owner_user_id} 是 {@code NOT NULL}
 * （报价单没有公海），所以拦截器生成的 {@code owner_user_id IS NULL} 分支恒为 false，
 * 条件自动退化成纯 data_scope。
 *
 * <p><b>写操作的防越权同样靠拦截器</b>：{@link #update} / {@link #delete} 都先调
 * {@link #getQuotationDO}（{@code selectById}，拦载器会改写主键查询），越权时直接查不到、
 * 抛「报价单不存在」，根本走不到写那一步；而且拦截器对 UPDATE / DELETE 也生效 ——
 * 越权的写会因 WHERE 不匹配而影响 0 行。
 *
 * <p>⚠️ 早期版本这里手写过一份 {@code applyDataScope}，装上拦截器后**必须删掉**，
 * 否则两个条件会 AND 在一起变成双重过滤（客户模块的 {@code CustomerServiceImpl} 也留了同样的说明）。
 */
@Slf4j
@Service
public class QuotationServiceImpl implements QuotationService {

    /** 版本号起点（《02-建表文档》：一个商机可出多版报价单） */
    private static final int DEFAULT_VERSION = 1;

    /**
     * 转合同时的合同类型：1 销售合同（字典 {@code crm_contract_type}）。
     *
     * <p>报价转出来的天然是销售合同。要出框架合同 / 补充协议，走合同页自己新增，
     * 不由「转合同」这个动作决定 —— 那需要一个额外的选择弹窗，而《05-接口文档》
     * 的 {@code POST /crm/quotation/{id}/convert-contract} 没有请求体。
     */
    private static final int CONTRACT_TYPE_SALES = 1;

    /** 筛选胶囊：我负责的 */
    private static final String SCOPE_MINE = "mine";

    /** 筛选胶囊：下属负责的（负责人 ∈ 沿 superior_id 递归取到的全部下属，不含自己） */
    private static final String SCOPE_SUBORDINATE = "subordinate";

    /** 筛选胶囊：我关注的（依赖的关注表尚未建表，本轮不额外筛选） */
    private static final String SCOPE_STARRED = "starred";

    @Resource
    private QuotationMapper quotationMapper;
    @Resource
    private QuotationItemMapper quotationItemMapper;
    @Resource
    private ProductMiniMapper productMiniMapper;
    @Resource
    private CustomerMiniMapper customerMiniMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private SysUnitMiniMapper sysUnitMiniMapper;
    @Resource
    private OpportunityMapper opportunityMapper;
    @Resource
    private QuotationConvert quotationConvert;
    /** 明细行的共用机制（与商机明细共用一份，见 ItemSupport 的类注释） */
    @Resource
    private ItemSupport itemSupport;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private BusinessNumberService businessNumberService;

    @Resource
    private StarService starService;
    /** 「我的全部下属」—— {@code scope=subordinate} 胶囊用，口径见 SubordinateUserIds 的类注释 */
    @Resource
    private SubordinateUserIds subordinateUserIds;

    /**
     * 合同 Service —— **只用于 {@link #convertToContract} 里那个「已交付的合同创建接口」**。
     *
     * <p>报价转合同不复刻一遍合同落库逻辑（编号规则、明细金额重算、草稿态），
     * 直接调 {@code ContractService#create}。合同模块因此一行都不用改，
     * 也不存在两套金额口径。{@code ContractServiceImpl} 只依赖 {@code QuotationMapper}
     * （不是本接口），所以这里没有循环依赖。
     */
    @Resource
    private ContractService contractService;

    /**
     * 合同 Mapper —— **只读**，用于补 {@code contractId / contractNo / contractCount} 三个派生字段
     * （反查 {@code crm_contract.quotation_id}，见 {@link #loadContractsByQuotation}）。
     *
     * <p>不写合同表：报价单模块对合同的写入只走 {@link #contractService} 那一个入口。
     */
    @Resource
    private ContractMapper contractMapper;

    // ==================== 查询 ====================

    @Override
    public PageResult<QuotationRespVO> pageQuery(QuotationPageReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        String scope = normalizeScope(reqVO.getScope());

        // 「下属负责的报价」：数据范围只有「仅本人」的用户不放行，明确返回空集。
        // 即使真的被别人配了下属也仍然空集 —— 与商机模块同一守卫（fail-closed）。
        // 不能退化成「按本部门查」—— 那会把同部门同事的报价单暴露给销售专员（越权）。
        if (SCOPE_SUBORDINATE.equals(scope) && isSelfOnlyScope(loginUser)) {
            return emptyResult(reqVO);
        }

        LambdaQueryWrapper<QuotationDO> wrapper = Wrappers.lambdaQuery();
        applyKeyword(wrapper, reqVO.getKeyword());
        if (StringUtils.hasText(reqVO.getStatus())) {
            // ⚠️ 用 IN 而不是 =：筛选「报价中」要同时命中库里的 0 草稿与 1 已发出
            wrapper.in(QuotationDO::getStatus, resolveStatus(reqVO.getStatus()).getCodes());
        }
        if (reqVO.getCustomerId() != null) {
            wrapper.eq(QuotationDO::getCustomerId, reqVO.getCustomerId());
        }
        if (reqVO.getOpportunityId() != null) {
            wrapper.eq(QuotationDO::getOpportunityId, reqVO.getOpportunityId());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(QuotationDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(QuotationDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(QuotationDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        // 数据权限不在这里拼 —— 由 crm-mybatis 的 DataPermissionInnerInterceptor
        // 统一改写 SQL（crm_quotation 在它的白名单里）。早期这里手写过一份 applyDataScope，
        // 装上拦截器后必须删掉，否则两个条件会 AND 在一起变成双重过滤。
        applyScopeCapsule(wrapper, scope, loginUser);
        wrapper.orderByDesc(QuotationDO::getCreateTime)
                .orderByDesc(QuotationDO::getQuotationId);

        Page<QuotationDO> page = quotationMapper.selectPage(reqVO, wrapper);
        return toPageResult(page);
    }

    @Override
    public QuotationRespVO get(Long id) {
        QuotationDO quotationDO = getQuotationDO(id);
        Map<Long, List<QuotationItemDO>> itemsByQuotation = loadItems(List.of(id));
        List<QuotationItemDO> items = itemsByQuotation.getOrDefault(id, Collections.emptyList());
        return toRespVO(quotationDO, items, buildSupport(List.of(quotationDO), itemsByQuotation));
    }

    @Override
    public PageResult<QuotationSimpleRespVO> simpleList(QuotationSimplePageReqVO reqVO) {
        LambdaQueryWrapper<QuotationDO> wrapper = Wrappers.lambdaQuery();
        // 只取选择器真正要读的几列：省流量，也顺带避免把备注这类自由文本读出来
        // （产品明细不在主表上，走下面那次按 quotation_id 的批量查询）
        // ⚠️ opportunityId 必须一起 select：选择器响应要带出它（合同表单靠它把商机对齐），
        //    漏了的话自动映射拿到的永远是 null，症状是「选了报价单，商机那格还是空的」
        wrapper.select(QuotationDO::getQuotationId,
                QuotationDO::getQuotationName,
                QuotationDO::getQuotationNo,
                QuotationDO::getOpportunityId,
                QuotationDO::getCustomerId);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(QuotationDO::getQuotationName, reqVO.getKeyword().trim());
        }
        // 收窄条件（都可空）：合同表单在选了商机时按商机过滤，否则按客户过滤，
        // 避免用户选中别家客户 / 别的商机的报价单。不传即不过滤，行为与改动前一致。
        wrapper.eq(reqVO.getOpportunityId() != null, QuotationDO::getOpportunityId, reqVO.getOpportunityId());
        wrapper.eq(reqVO.getCustomerId() != null, QuotationDO::getCustomerId, reqVO.getCustomerId());
        // 选择器同样受数据权限约束（由拦截器改写 SQL）：看不到的报价单不该出现在选择器里
        wrapper.orderByDesc(QuotationDO::getCreateTime)
                .orderByDesc(QuotationDO::getQuotationId);

        Page<QuotationDO> page = quotationMapper.selectPage(reqVO, wrapper);
        List<QuotationDO> records = page.getRecords();
        Map<Long, String> customerNames = customerNameMap(records);
        // 商机名与客户名一样是跨表派生字段，一次批量查（同一个方法列表页也在用）
        Map<Long, OpportunityDO> opportunities = loadOpportunities(records);

        // 明细随选择器一起下发：合同表单选中一张报价单后，要把它的产品行直接填进合同明细
        // （views/crm/contract/form-config.js 的 quotationNo 字段 onSelect）。
        // 与详情接口同一套取数：按这一页的报价单 id 批量查明细 + 批量查单位名，不做 N+1。
        // 单位名必须补：前端明细的「单位」是下拉，回显缺了名字那一格就是空的。
        Map<Long, List<QuotationItemDO>> itemsByQuotation = loadItems(quotationIdsOf(records));
        List<QuotationItemDO> allItems = itemsByQuotation.values().stream()
                .flatMap(List::stream)
                .toList();
        Map<Long, String> unitNames = itemSupport.loadUnitNames(unitIdsOf(allItems));

        List<QuotationSimpleRespVO> list = records.stream().map(quotation -> {
            QuotationSimpleRespVO vo = quotationConvert.toSimpleRespVO(quotation);
            vo.setCustomerName(customerNames.get(quotation.getCustomerId()));
            OpportunityDO opportunity = opportunities.get(quotation.getOpportunityId());
            vo.setOpportunityName(opportunity == null ? null : opportunity.getOpportunityName());
            vo.setProducts(itemRespVOs(
                    itemsByQuotation.getOrDefault(quotation.getQuotationId(), Collections.emptyList()),
                    unitNames));
            return vo;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public String export(QuotationPageReqVO reqVO) {
        return ExportSupport.toUrl(ossStorageService, buildExportRows(reqVO), QuotationExportRowVO.class);
    }

    @Override
    public String exportXml(QuotationPageReqVO reqVO) {
        return ExportSupport.toXmlUrl(ossStorageService, buildExportRows(reqVO), QuotationExportRowVO.class);
    }

    /** 复用 pageQuery 的过滤条件（含 scope 胶囊与数据权限），但不翻页 */
    private List<QuotationExportRowVO> buildExportRows(QuotationPageReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        String scope = normalizeScope(reqVO.getScope());
        if (SCOPE_SUBORDINATE.equals(scope) && isSelfOnlyScope(loginUser)) {
            return List.of();
        }

        LambdaQueryWrapper<QuotationDO> wrapper = Wrappers.lambdaQuery();
        applyKeyword(wrapper, reqVO.getKeyword());
        if (StringUtils.hasText(reqVO.getStatus())) {
            wrapper.in(QuotationDO::getStatus, resolveStatus(reqVO.getStatus()).getCodes());
        }
        if (reqVO.getCustomerId() != null) {
            wrapper.eq(QuotationDO::getCustomerId, reqVO.getCustomerId());
        }
        if (reqVO.getOpportunityId() != null) {
            wrapper.eq(QuotationDO::getOpportunityId, reqVO.getOpportunityId());
        }
        if (reqVO.getOwnerUserId() != null) {
            wrapper.eq(QuotationDO::getOwnerUserId, reqVO.getOwnerUserId());
        }
        if (reqVO.getCreateTimeStart() != null) {
            wrapper.ge(QuotationDO::getCreateTime, reqVO.getCreateTimeStart());
        }
        if (reqVO.getCreateTimeEnd() != null) {
            wrapper.le(QuotationDO::getCreateTime, reqVO.getCreateTimeEnd());
        }
        applyScopeCapsule(wrapper, scope, loginUser);
        wrapper.orderByDesc(QuotationDO::getCreateTime)
                .orderByDesc(QuotationDO::getQuotationId);

        List<QuotationDO> records = quotationMapper.selectList(wrapper);
        Map<Long, String> customerNames = customerNameMap(records);
        Map<Long, String> ownerNames = userNameMap(userIdsOf(records));

        return records.stream().map(q -> {
            QuotationExportRowVO row = new QuotationExportRowVO();
            row.setQuotationNo(q.getQuotationNo());
            row.setQuotationName(q.getQuotationName());
            row.setCustomerName(customerNames.get(q.getCustomerId()));
            row.setVersion(q.getVersion() == null ? "" : String.valueOf(q.getVersion()));
            row.setTotalWithTax(q.getTotalWithTax() == null ? "" : q.getTotalWithTax().toPlainString());
            row.setTotalAmount(q.getTotalAmount() == null ? "" : q.getTotalAmount().toPlainString());
            row.setTaxAmount(q.getTaxAmount() == null ? "" : q.getTaxAmount().toPlainString());
            row.setValidDate(q.getValidDate() == null ? "" : q.getValidDate().toString());
            row.setStatus(QuotationStatus.labelOf(q.getStatus()));
            row.setOwnerName(ownerNames.get(q.getOwnerUserId()));
            row.setCreateTime(timeStr(q.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuotationSaveReqVO reqVO) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        requireCustomerExists(reqVO.getCustomerId());
        // 商机必须存在，且与报价单是同一个客户（不一致 → 2253）
        requireOpportunityOfCustomer(reqVO.getOpportunityId(), reqVO.getCustomerId());

        // 负责人缺省为当前登录人（与客户 / 商机模块同一口径）
        Long ownerUserId = reqVO.getOwnerUserId() != null ? reqVO.getOwnerUserId() : loginUser.getUserId();
        SysUserMiniDO owner = requireUserExists(ownerUserId);

        // products 缺省视为空数组：允许先建一张只有表头的报价单，三个金额都是 0
        List<QuotationItemDO> items = buildItems(
                reqVO.getProducts() == null ? Collections.emptyList() : reqVO.getProducts());
        MoneyCalculator.DocumentTotals totals = totalsOf(items);

        QuotationDO quotationDO = quotationConvert.toDO(reqVO);
        quotationDO.setQuotationId(null);
        quotationDO.setQuotationNo(buildQuotationNo(reqVO.getCustomerId(), owner.getDeptId()));
        quotationDO.setOwnerUserId(ownerUserId);
        // 归属部门跟着【负责人】走，不跟创建人 —— 数据权限是按 owner / dept 过滤的，
        // 部门若跟创建人走，主管替下属建一张报价单，这条报价单主管自己反而看不到
        quotationDO.setDeptId(owner == null ? loginUser.getDeptId() : owner.getDeptId());
        quotationDO.setVersion(resolveNextVersion(reqVO.getOpportunityId()));
        quotationDO.setStatus(resolveStatusOrDefault(reqVO.getStatus()).getWriteCode());
        quotationDO.setValidDate(parseDateLeniently(reqVO.getValidDate(), "有效期至"));
        applyTotals(quotationDO, totals);
        logClientAmountMismatch(reqVO, totals);

        quotationMapper.insert(quotationDO);
        insertItems(quotationDO.getQuotationId(), items);
        log.info("[报价单] 新增成功：id={}, no={}, 明细{}行, 价税合计={}, 不含税={}",
                quotationDO.getQuotationId(), quotationDO.getQuotationNo(), items.size(),
                totals.totalWithTax(), totals.totalAmount());
        return quotationDO.getQuotationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QuotationSaveReqVO reqVO) {
        QuotationDO existing = getQuotationDO(id);
        requireCustomerExists(reqVO.getCustomerId());
        // 与 create 同一处校验（少了它，「编辑」就成了绕过一致性检查的入口）
        requireOpportunityOfCustomer(reqVO.getOpportunityId(), reqVO.getCustomerId());

        QuotationDO update = quotationConvert.toDO(reqVO);
        update.setQuotationId(id);

        // 负责人：传了才改（且不能清空 —— crm_quotation.owner_user_id 是 NOT NULL）；
        // 改了要同步 dept_id（理由同 create）
        if (reqVO.getOwnerUserId() != null) {
            SysUserMiniDO owner = requireUserExists(reqVO.getOwnerUserId());
            update.setOwnerUserId(reqVO.getOwnerUserId());
            update.setDeptId(owner == null ? null : owner.getDeptId());
        }

        // 商机关联：**可清空**。前端的商机选择器有清空按钮，清空后提交的就是 null，
        // 而 update-strategy=not_null 会把 null 当成「不改这一列」，所以清空必须走显式 SET。
        boolean clearOpportunity = reqVO.getOpportunityId() == null;

        // 状态：只在「标签真的变了」时才写（见 resolveStatusUpdate 的防漂移说明）
        resolveStatusUpdate(reqVO.getStatus(), existing.getStatus()).ifPresent(update::setStatus);

        // 有效期：null=不改，""=清空，日期=设置
        LocalDate validDate = parseDateLeniently(reqVO.getValidDate(), "有效期至");
        update.setValidDate(validDate);

        // 明细：null=不动（金额也不重算）；数组（含 []）=整份覆盖并按新明细重算金额
        boolean replaceItems = reqVO.getProducts() != null;
        List<QuotationItemDO> items = replaceItems
                ? buildItems(reqVO.getProducts())
                : Collections.emptyList();
        if (replaceItems) {
            MoneyCalculator.DocumentTotals totals = totalsOf(items);
            applyTotals(update, totals);
            logClientAmountMismatch(reqVO, totals);
        }

        quotationMapper.updateById(update);

        if (clearOpportunity || (reqVO.getValidDate() != null && validDate == null)) {
            quotationMapper.update(null, Wrappers.<QuotationDO>lambdaUpdate()
                    .eq(QuotationDO::getQuotationId, id)
                    .set(clearOpportunity, QuotationDO::getOpportunityId, null)
                    .set(reqVO.getValidDate() != null && validDate == null, QuotationDO::getValidDate, null));
        }

        if (replaceItems) {
            replaceItems(id, items);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getQuotationDO(id);
        // 逻辑删除。明细不跟着删：主单查不到了，明细自然不会被读到，保留以便追溯
        quotationMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long id) {
        // 先做存在性校验：不存在的报价单不能关注 → 2251。
        // 这一步同时继承了数据权限保护（selectById 被拦截器改写），越权关注也是 2251
        getQuotationDO(id);
        starService.star(StarBizType.QUOTATION, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unstar(Long id) {
        getQuotationDO(id); // 与 delete / star 一致，先做存在性校验
        starService.unstar(StarBizType.QUOTATION, id);
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertToContract(Long id) {
        // ① 存在性 + 数据权限：越权与不存在都抛 2251，走不到后面任何写操作
        QuotationDO quotation = getQuotationDO(id);

        // ② 状态守卫：已失效（3 已拒绝 / 4 已作废）不许转合同。
        //    这一步必须【在】合同落库之前 —— 否则会先造出一份合同再回滚，
        //    徒增一次无意义的写和一把行锁（更要紧的是：校验与创建之间有窗口就迟早出错）
        if (!QuotationStatus.canConvert(quotation.getStatus())) {
            throw new ServiceException(ErrorCode.QUOTATION_STATUS_ERROR,
                    "已失效的报价单不能生成合同");
        }

        // ③ 明细：合同明细不允许为空（ContractSaveReqVO.items 是 @NotEmpty），
        //    所以一张只有表头的报价单不能转合同 —— 与其让合同侧报一个字段名为 items 的校验错误，
        //    不如在这里说清楚是什么问题
        List<QuotationItemDO> items = loadItems(List.of(id)).getOrDefault(id, Collections.emptyList());
        if (items.isEmpty()) {
            throw new ServiceException(ErrorCode.QUOTATION_STATUS_ERROR,
                    "报价单没有产品明细，无法生成合同");
        }

        // ④ 交给已交付的合同创建接口：编号规则、明细行金额按 3.6 重算、amount = Σ 明细、
        //    落到草稿待提审态，全部由它负责。报价单侧只凑请求体，不重复实现金额口径。
        //    注意这里**不进 Controller**，因此不会触发 @Valid —— 上面两步已经保证了
        //    items 非空，contractName / customerId 又都取自 DDL 的 NOT NULL 列。
        Long contractId = contractService.create(buildContractReq(quotation, items));

        // ⑤ 状态推进：条件更新（WHERE status IN 可转集合）既是状态守卫，也是并发守卫。
        //
        //    ⚠️ 它挡的是「并发作废」而不是「并发重复转合同」：可转集合包含 2，
        //       所以两个人同时点转合同会生成两份合同 —— 那是**允许**的结果（1:N）；
        //       但若期间有人把报价单作废了（状态变 4，不在集合里），这里的 WHERE 匹配不到，
        //       updated == 0 → 整笔回滚，连同刚建的合同一起撤销，不会留下「挂着合同的失效报价单」。
        int updated = quotationMapper.update(null, Wrappers.<QuotationDO>lambdaUpdate()
                .eq(QuotationDO::getQuotationId, id)
                .in(QuotationDO::getStatus, QuotationStatus.convertibleCodes())
                .set(QuotationDO::getStatus, QuotationStatus.CONTRACTED.getWriteCode()));
        if (updated == 0) {
            throw new ServiceException(ErrorCode.QUOTATION_STATUS_ERROR,
                    "报价单已被他人变更，请刷新后重试");
        }

        log.info("[报价单] 转合同成功：quotationId={}, contractId={}, 明细{}行",
                id, contractId, items.size());
        return contractId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidQuotation(Long id) {
        QuotationDO quotation = getQuotationDO(id);
        // 只有「报价中」（库值 0/1）能作废：
        //  · 已生成合同的单子已有下游合同（1:N），作废它会出现「报价单已失效、合同仍有效」的矛盾
        //  · 已失效是终态，重复作废没有意义
        if (!QuotationStatus.canVoid(quotation.getStatus())) {
            throw new ServiceException(ErrorCode.QUOTATION_STATUS_ERROR,
                    "只有「报价中」的报价单可以作废；已生成合同的请先处理关联合同");
        }
        int updated = quotationMapper.update(null, Wrappers.<QuotationDO>lambdaUpdate()
                .eq(QuotationDO::getQuotationId, id)
                .in(QuotationDO::getStatus, QuotationStatus.voidableCodes())
                .set(QuotationDO::getStatus, QuotationStatus.EXPIRED.getWriteCode()));
        if (updated == 0) {
            throw new ServiceException(ErrorCode.QUOTATION_STATUS_ERROR,
                    "报价单已被他人变更，请刷新后重试");
        }
        log.info("[报价单] 作废成功：quotationId={}", id);
    }

    /**
     * 报价单 → 合同创建请求。
     *
     * <p><b>归属人传报价单的负责人，不传当前登录人</b>：主管替下属转合同时，
     * 合同应当还是下属的（与 {@link #create} 里「归属部门跟负责人走，不跟创建人」同一口径）。
     * 不传的话合同侧的缺省值是登录人，合同会静默变成主管的。
     *
     * <p><b>⚠️ 只搬「金额的输入」，不搬金额本身</b>：{@code ContractItemReqVO} 里根本没有
     * {@code amount} 字段 —— 合同侧按《02-建表文档》3.6 从
     * {@code quantity × unitPrice × (1 − discountRate/100)} 重算行金额。
     * 报价单的行金额是同一个公式、同一次最终四舍五入算出来的，所以正常数据下两边逐行相等；
     * 即便历史数据的 {@code amount} 被外部改过，合同也以公式为准 ——
     * 这恰好保证合同自己的硬规则「{@code amount == Σ 明细}」恒成立
     * （合同提交审核时会二次校验它）。
     *
     * <p>签订日期 / 客户签约人 / 公司签约人留空：报价单上没有这些信息，
     * 与「合同页新增一张合同」的初始状态一致，由用户在合同页补。
     */
    private ContractSaveReqVO buildContractReq(QuotationDO quotation, List<QuotationItemDO> items) {
        ContractSaveReqVO req = new ContractSaveReqVO();
        req.setContractName(quotation.getQuotationName());
        req.setCustomerId(quotation.getCustomerId());
        req.setOpportunityId(quotation.getOpportunityId());
        // 这一列是「关联合同」派生字段的唯一依据，必须带上
        req.setQuotationId(quotation.getQuotationId());
        req.setContractType(CONTRACT_TYPE_SALES);
        req.setOwnerUserId(quotation.getOwnerUserId());
        req.setRemark(quotation.getRemark());
        req.setItems(items.stream().map(this::toContractItem).toList());
        return req;
    }

    /**
     * 报价明细 → 合同明细请求（10 个业务字段逐个搬运，字段名两表本来就一致）。
     *
     * <p>刻意不用 MapStruct：这一层要的是「显式、可逐个断言」的搬运，
     * 少搬一个字段（比如 {@code taxRate}）在界面上一眼看不出，但会让合同的税额算错。
     */
    private ContractItemReqVO toContractItem(QuotationItemDO item) {
        ContractItemReqVO req = new ContractItemReqVO();
        req.setProductId(item.getProductId());
        req.setProductName(item.getProductName());
        req.setProductTypeId(item.getProductTypeId());
        req.setUnitId(item.getUnitId());
        req.setQuantity(item.getQuantity());
        req.setUnitPrice(item.getUnitPrice());
        req.setDiscountRate(item.getDiscountRate());
        req.setTaxRate(item.getTaxRate());
        req.setSort(item.getSort());
        req.setRemark(item.getRemark());
        return req;
    }

    // ==================== 内部方法：明细读写 ====================

    /**
     * 请求里的明细行 → DO 列表。
     *
     * <p>具体机制（批量取产品与单位、补分类快照与 {@code unit_id}、按提交顺序写 {@code sort}、
     * 按 3.6 重算行金额并覆盖客户端提交的 {@code amount}）都在
     * {@link ItemSupport#buildItems} 里 —— 商机明细用的是同一套，三张明细表共用一份实现。
     *
     * <p>这里只负责：行数上限校验 + 指定首行 {@code sort}（报价单的明细永远整份覆盖，从 1 开始）。
     */
    private List<QuotationItemDO> buildItems(List<DocumentItemSaveReqVO> itemReqs) {
        List<DocumentItemSaveReqVO> rows = itemReqs == null ? List.of() : itemReqs;
        itemSupport.validateCount(rows.size(), "单张报价单");
        return itemSupport.buildItems(rows, 1, QuotationItemDO::new, "报价单");
    }

    /** 单据合计：{@code total_with_tax} / {@code tax_amount} / {@code total_amount}（不含税） */
    private MoneyCalculator.DocumentTotals totalsOf(List<QuotationItemDO> items) {
        List<MoneyCalculator.LineAmounts> lines = items.stream()
                .map(item -> MoneyCalculator.line(
                        item.getQuantity(), item.getUnitPrice(), item.getDiscountRate(), item.getTaxRate()))
                .toList();
        return MoneyCalculator.document(lines);
    }

    private void applyTotals(QuotationDO quotationDO, MoneyCalculator.DocumentTotals totals) {
        quotationDO.setTotalWithTax(totals.totalWithTax());
        quotationDO.setTaxAmount(totals.taxAmount());
        quotationDO.setTotalAmount(totals.totalAmount());
    }

    /** 批量插入明细（行数有  上限，逐条 insert 足够） */
    private void insertItems(Long quotationId, List<QuotationItemDO> items) {
        for (QuotationItemDO item : items) {
            item.setItemId(null);
            item.setQuotationId(quotationId);
            quotationItemMapper.insert(item);
        }
    }

    /**
     * 整份覆盖明细：先逻辑删除旧行（保留痕迹，便于追溯），再插入新行。
     *
     * <p>用逻辑删除而不是物理删除，是因为 {@code crm_quotation_item} 有 {@code del_flag} 列，
     * 表的语义就是「明细也留痕」。
     */
    private void replaceItems(Long quotationId, List<QuotationItemDO> items) {
        quotationItemMapper.delete(Wrappers.<QuotationItemDO>lambdaQuery()
                .eq(QuotationItemDO::getQuotationId, quotationId));
        insertItems(quotationId, items);
    }

    // ==================== 内部方法：查询条件 ====================

    /**
     * 关键字：同时匹配报价单名称或商机名称（前端搜索框占位就是「报价单名称/商机名称」）。
     *
     * <p>商机名称在 {@code crm_opportunity} 里，先按名称把命中的商机 ID 查出来再 {@code IN}，
     * 避免在报价单表上写子查询。
     */
    private void applyKeyword(LambdaQueryWrapper<QuotationDO> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String kw = keyword.trim();
        List<Long> opportunityIds = opportunityMapper.selectList(
                        Wrappers.<OpportunityDO>lambdaQuery().like(OpportunityDO::getOpportunityName, kw))
                .stream()
                .map(OpportunityDO::getOpportunityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (opportunityIds.isEmpty()) {
            wrapper.like(QuotationDO::getQuotationName, kw);
            return;
        }
        // 括号要一起加：否则 LIKE 的 OR 会与后面的数据权限条件平级展开，
        // 变成「名称命中 或 商机命中 或 部门命中」，等于把数据权限绕过去了
        wrapper.and(w -> w.like(QuotationDO::getQuotationName, kw)
                .or().in(QuotationDO::getOpportunityId, opportunityIds));
    }

    /** 私海收窄：只看自己负责的（供 {@code scope=mine} 用）。 */
    private void applyOwnerScope(LambdaQueryWrapper<QuotationDO> wrapper, LoginUser loginUser) {
        if (loginUser.getUserId() != null) {
            wrapper.eq(QuotationDO::getOwnerUserId, loginUser.getUserId());
        }
    }

    /**
     * 前端筛选胶囊（{@code ProList} 的 {@code scope}，报价单页共 7 个取值）。
     *
     * <p>这里只处理**数据范围以外的 UI 筛选**；行级数据权限由拦截器负责（见类注释）。
     * 胶囊与数据权限是**叠加（AND）**关系：胶囊只会进一步收窄，绝不会放宽 ——
     * 所以即使前端传了越权的 scope，也看不到权限外的数据。
     */
    private void applyScopeCapsule(LambdaQueryWrapper<QuotationDO> wrapper, String scope, LoginUser loginUser) {
        if (scope == null) {
            return;
        }
        switch (scope) {
            case SCOPE_MINE -> applyOwnerScope(wrapper, loginUser);
            case SCOPE_SUBORDINATE -> {
                // 「下属负责的报价」= 负责人落在「我的全部下属」集合里
                //（沿 sys_user.superior_id 逐级向下，**多层且不含自己**，口径见《05-接口文档》4.1）。
                //
                // ⚠️ 与商机模块同一取舍：刻意【不】按 dept_id / dataScopeDeptIds 过滤。
                //    上级链与部门树并不等价（种子数据里销售总监(2) 与张三/李四(3/4) 同属部门 2），
                //    按部门查会把**同部门同事**的报价单、以及**我自己**的报价单一并带出来 ——
                //    那是决策 1 要拦的事。下属是「我」的下属，不是「部门」的属性。
                List<Long> subordinateIds = subordinateUserIds.of(loginUser.getUserId());
                if (subordinateIds.isEmpty()) {
                    // 没有下级（或 userId 取不到）→ 收窄到空集。不能走 default 的「不筛」，
                    // 也不能退化成「本部门查」；写法同 DataPermissionInnerInterceptor.NEVER_MATCH。
                    // 同时这也是「不给 MyBatis-Plus 传空集合」的守卫：3.5.7 的 in(col, empty)
                    // 会生成非法的 IN ()。
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.in(QuotationDO::getOwnerUserId, subordinateIds);
            }
            case SCOPE_STARRED -> {
                // 我关注的 = 我关注过的报价单（跨归属，所以按 quotation_id 而不是 owner 过滤）
                //
                // ⚠️ 这里曾经是**静默不筛**：注释写着「关注表尚未建表」，但那句话早就过时了 ——
                //    crm_star 表、StarService、StarBizType.QUOTATION 都在，线索与客户模块
                //    一直在正常使用。结果是报价单列表的「我关注的报价单」胶囊点了没反应，
                //    还返回未经收窄的数据（用户会以为筛选生效了）。现已改为真筛选。
                Set<Long> starredIds = starService.starredBizIds(StarBizType.QUOTATION);
                if (starredIds.isEmpty()) {
                    // 一条都没关注 → 收窄到空集，不能走下面 default 的「不筛」策略
                    // （那会把全量数据当成「我关注的」返回）。写法同 DataPermissionInnerInterceptor.NEVER_MATCH
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.in(QuotationDO::getQuotationId, starredIds);
            }
            // all 与未知取值：不额外筛选。
            // 未知取值「不筛」而不是「筛空」—— 前端以后多加一个胶囊值时，
            // 最坏是多显示几条，而不是整个列表变空且没人知道为什么（产品 / 商机模块同一取舍）
            default -> {
                QuotationStatus status = QuotationStatus.ofScope(scope);
                if (status != null) {
                    // 状态胶囊：报价中 → IN (0,1)、已失效 → IN (3,4)，不能只等值匹配
                    wrapper.in(QuotationDO::getStatus, status.getCodes());
                }
            }
        }
    }

    /** 筛选胶囊取值归一化：去掉空白，转小写；空值返回 null */
    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return null;
        }
        // ⚠️ 用 Locale.ROOT 而不是默认 Locale：土耳其语环境下 "I".toLowerCase() 会变成 "ı"，
        //    MINE / STARred 这类取值会莫名匹配不上
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    /** 数据范围是否只有「仅本人」（含取不到的情况，拦截器那边同样按「仅本人」兜底） */
    private boolean isSelfOnlyScope(LoginUser loginUser) {
        Integer dataScope = loginUser.getDataScope();
        return dataScope == null || DataScope.SELF.equals(dataScope);
    }

    // ==================== 内部方法：状态 / 版本 / 日期 ====================

    /** 中文标签 → 状态，非法取值抛 400 并列出可填的值 */
    private QuotationStatus resolveStatus(String label) {
        QuotationStatus status = QuotationStatus.ofLabel(label);
        if (status == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    "报价单状态不合法，请填写：" + QuotationStatus.labelOptions());
        }
        return status;
    }

    /** 空白时取默认状态「报价中」（与前端空表单的默认值一致） */
    private QuotationStatus resolveStatusOrDefault(String label) {
        if (!StringUtils.hasText(label)) {
            return QuotationStatus.QUOTING;
        }
        return resolveStatus(label);
    }

    /**
     * 更新时的状态取值 —— <b>防漂移</b>。
     *
     * <p>读方向是多对一归并（{@code 0|1 → 报价中}、{@code 3|4 → 已失效}），
     * 所以「请求标签 == 当前库值的归并标签」时**不能写库**：
     * 否则一条 {@code status = 0}（草稿）的报价单被编辑一次就变成 {@code 1}（已发出），
     * {@code 3}（已拒绝）会变成 {@code 4}（已作废）—— 都是静默的数据变质。
     *
     * @return 需要写入的新码值；{@code Optional.empty()} 表示「不动 status 列」
     */
    private Optional<Integer> resolveStatusUpdate(String requestedLabel, Integer currentCode) {
        if (!StringUtils.hasText(requestedLabel)) {
            return Optional.empty();
        }
        QuotationStatus requested = resolveStatus(requestedLabel);
        if (requested == QuotationStatus.ofCode(currentCode)) {
            log.debug("[报价单] 状态标签未变化（库值 {} 归并为「{}」），不写 status 列",
                    currentCode, requested.getLabel());
            return Optional.empty();
        }
        return Optional.of(requested.getWriteCode());
    }

    /**
     * 下一个版本号：同商机下现有报价单的最大版本 +1；没挂商机则从 1 开始。
     *
     * <p>对应《02-建表文档》「一个商机可出多版报价单」的 {@code version} 列。
     * 逻辑删除的报价单不计入（{@code @TableLogic} 自动过滤）。
     *
     * <p>前端不可见、不可改 —— 要改成「固定 1」只需删掉这一处调用。
     */
    private Integer resolveNextVersion(Long opportunityId) {
        if (opportunityId == null) {
            return DEFAULT_VERSION;
        }
        return quotationMapper.selectList(Wrappers.<QuotationDO>lambdaQuery()
                        .select(QuotationDO::getVersion)
                        .eq(QuotationDO::getOpportunityId, opportunityId))
                .stream()
                .map(QuotationDO::getVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(DEFAULT_VERSION);
    }

    /**
     * 宽容解析日期。
     *
     * <p>为什么收字符串而不是直接收 {@code LocalDate}：前端日期字段从没选过时提交的是
     * **空字符串**，而 Jackson 会把 {@code ""} 交给 {@code LocalDate.parse} 抛异常
     * → 用户什么都不填就提交直接 400（商机模块的 {@code expectDealTime} 踩过同一个坑）。
     *
     * @return null 表示「空」（配合调用方做「清空」判断）
     */
    private LocalDate parseDateLeniently(String text, String fieldLabel) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException(ErrorCode.BAD_REQUEST,
                    fieldLabel + "格式不正确，应为 yyyy-MM-dd，实际：" + text);
        }
    }

    /**
     * 报价单编号：由编号引擎的 {@code QUOTE} 规则生成 —— {@code BJ} + {@code yyyyMMdd} + 4 位流水，
     * 按日重置（例 {@code BJ202401010001}），见 {@code sys_numbering_rule} 与
     * {@link BusinessNumberService}。
     *
     * <p>⚠️ 这四个要素（前缀 / 日期格式 / 位数 / 重置周期）在引擎里对内置 code 是**硬编码锁定**的，
     * 改 {@code sys_numbering_rule.prefix} 不生效 —— 想换形态要改
     * {@code BusinessNumberServiceImpl.FIXED}（与编号定义页的 {@code enforceFixed} 两处一起改）。
     *
     * <p>⚠️ 编号的 CAS 更新与主单插入在同一个事务（{@link #create} 是 {@code @Transactional}），
     * 落库失败时流水会一起回滚，不会白占号。
     */
    private String buildQuotationNo(Long customerId, Long deptId) {
        Map<String, Object> context = new HashMap<>();
        context.put("customerId", customerId);
        if (deptId != null) context.put("deptId", deptId);
        return businessNumberService.next("QUOTE", context);
    }

    // ==================== 内部方法：转换与跨表补值 ====================

    /**
     * 列表 / 详情共用的跨表补值上下文。
     *
     * <p><b>为什么收成一个 record</b>：{@link #toRespVO} 原先有 8 个参数，其中三个是
     * {@code Map<Long,String>}（客户名 / 负责人名 / 单位名）—— 位置传串了编译器也不会报错，
     * 只会安静地显示错人。加上「关联合同」的两个 Map 之后就是 10 个参数，
     * 传错概率进一步上升。收成一个具名上下文，调用点变成「组装 → 传一个」。
     */
    private record RespSupport(Map<Long, String> customerNames,
                              Map<Long, OpportunityDO> opportunities,
                              Map<Long, String> userNames,
                              Map<Long, ProductMiniDO> products,
                              Map<Long, String> unitNames,
                              Set<Long> starredIds,
                              Map<Long, ContractDO> latestContracts,
                              Map<Long, Integer> contractCounts) {
    }

    /**
     * 组装跨表补值上下文 —— 每次批量查询各一次，不做 N+1。
     *
     * @param records          这一批报价单（列表一整页 / 详情一条）
     * @param itemsByQuotation 这批报价单的明细，按 {@code quotationId} 分组
     */
    private RespSupport buildSupport(List<QuotationDO> records,
                                     Map<Long, List<QuotationItemDO>> itemsByQuotation) {
        List<QuotationItemDO> allItems = itemsByQuotation.values().stream()
                .flatMap(List::stream)
                .toList();
        Map<Long, List<ContractDO>> contractsByQuotation = loadContractsByQuotation(records);
        return new RespSupport(
                customerNameMap(records),
                // 商机这一次查询同时供「报价商机」列与「整单折扣」用（折扣统一取商机的）
                loadOpportunities(records),
                userNameMap(userIdsOf(records)),
                itemSupport.loadProducts(productIdsOf(allItems)),
                itemSupport.loadUnitNames(unitIdsOf(allItems)),
                // 关注态批量查一次，避免逐行查库；空页时 starredBizIds 返回空集。
                // 详情走的是同一个「按 id 集合查」的方法，而不是 isStarred 单条查 ——
                // 少一个分支就少一处将来会忘记同步的地方
                starService.starredBizIds(StarBizType.QUOTATION, quotationIdsOf(records)),
                latestOf(contractsByQuotation),
                countsOf(contractsByQuotation));
    }

    private PageResult<QuotationRespVO> toPageResult(Page<QuotationDO> page) {
        List<QuotationDO> records = page.getRecords();
        Map<Long, List<QuotationItemDO>> itemsByQuotation = loadItems(quotationIdsOf(records));
        RespSupport support = buildSupport(records, itemsByQuotation);

        List<QuotationRespVO> list = records.stream()
                .map(quotation -> toRespVO(quotation,
                        itemsByQuotation.getOrDefault(quotation.getQuotationId(), Collections.emptyList()),
                        support))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 组装单个响应：跨表补值 + 关注态 + 关联合同 + 取关联商机的统一折扣 + 按明细派生「商品总数」「整单利润」 */
    private QuotationRespVO toRespVO(QuotationDO quotationDO, List<QuotationItemDO> items, RespSupport support) {
        QuotationRespVO vo = quotationConvert.toRespVO(quotationDO);
        OpportunityDO opportunity = support.opportunities().get(quotationDO.getOpportunityId());
        vo.setCustomerName(support.customerNames().get(quotationDO.getCustomerId()));
        vo.setOpportunityName(opportunity == null ? null : opportunity.getOpportunityName());
        vo.setOwnerName(support.userNames().get(quotationDO.getOwnerUserId()));
        vo.setCreator(support.userNames().get(quotationDO.getCreateBy()));
        // 列表传进来的是一整页的关注集合，详情传进来的是只含这一条的集合 —— 同一个方法两处复用
        vo.setStarred(support.starredIds().contains(quotationDO.getQuotationId()));

        // 关联合同（派生）：最新一份给前端做跳转入口，份数用于提示「共 N 份」。
        // 没有合同 → contractId/contractNo 为 null（JSON 里消失，前端显示 --），份数给 0 而不是 null ——
        // 「零份」是个明确的事实，不该和「查不出来」混在一起
        ContractDO latestContract = support.latestContracts().get(quotationDO.getQuotationId());
        vo.setContractId(latestContract == null ? null : latestContract.getContractId());
        vo.setContractNo(latestContract == null ? null : latestContract.getContractNo());
        vo.setContractCount(support.contractCounts().getOrDefault(quotationDO.getQuotationId(), 0));

        vo.setProducts(itemRespVOs(items, support.unitNames()));
        vo.setProductCount(productCountOf(items));
        vo.setProfit(profitOf(items, support.products()));

        // 整单折扣：**统一取关联商机的折扣**（业务上折扣只在商机里维护一处，
        // crm_quotation 没有折扣列，表单也没有这一项）。
        // 无关联商机、或商机取不到（已逻辑删除 / 不在数据范围内）→ 0（= 不打折）而不是 null：
        // 前端整单折扣列对 0 显示 `0%`、对 null 显示 `--`，0 更不容易让人以为算错了。
        BigDecimal discount = (opportunity == null || opportunity.getDiscountRate() == null)
                ? BigDecimal.ZERO
                : opportunity.getDiscountRate();
        vo.setDiscount(discount);

        // 整单最终价 = **折后**价税合计 = total_with_tax ×(1 − 折扣/100)。
        // ⚠️ 折后只体现在本字段：totalAmount / taxAmount 仍是落库的「明细汇总」原值（3.6 口径），
        //    所以响应里 不含税合计 + 税额 ≠ 整单最终价，这是刻意的（见 docs/11 第三节）。
        BigDecimal totalWithTax = quotationDO.getTotalWithTax() == null
                ? BigDecimal.ZERO
                : quotationDO.getTotalWithTax();
        BigDecimal rate = BigDecimal.ONE.subtract(discount.movePointLeft(2));
        vo.setFinalAmount(MoneyCalculator.round2(totalWithTax.multiply(rate)));
        return vo;
    }

    /** 报价单主键集合（跨表补值/明细查询都按它批量取数），已去空 */ 
    private List<Long> quotationIdsOf(List<QuotationDO> records) {
        return records.stream()
                .map(QuotationDO::getQuotationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 明细 DO → 响应行（按 {@code unitId} 补单位名）。
     *
     * <p>详情 / 列表（{@link #toRespVO}）与选择器（{@link #simpleList}）共用这一份：
     * 补值逻辑写两遍的话，改了一处、另一处会安静地少一个单位名 ——
     * 而症状只是「编辑时单位下拉是空的」，很难联想到是这里。
     *
     * @param unitNames 批量查到的 {@code unitId → unitName}；查不到时该行 {@code unitName} 为 null
     */
    private List<DocumentItemRespVO> itemRespVOs(List<QuotationItemDO> items, Map<Long, String> unitNames) {
        return items.stream().map(item -> {
            DocumentItemRespVO itemVO = quotationConvert.toItemRespVO(item);
            itemVO.setUnitName(unitNames.get(item.getUnitId()));
            return itemVO;
        }).toList();
    }

    /**
     * 批量取「由这些报价单生成的合同」，一次查询，按 {@code quotation_id} 分组。
     *
     * <p><b>为什么按 {@code crm_contract.quotation_id} 反查而不是在报价单表上存一列</b>：
     * 一个报价单可以出多份合同（1:N，见 {@link #convertToContract}），
     * 报价单表上放单个 {@code contract_id} 表达不了这个关系，放不下的那部分只能靠猜。
     * DDL 里 {@code crm_contract} 本来就有 {@code quotation_id} + {@code idx_quotation_id}，
     * 这个方向才是「事实所在的那一侧」。
     *
     * <p>走 {@code ContractMapper}（带 {@code @TableLogic}）→ **已逻辑删除的合同不参与**，
     * 「关联合同」列会自动回退到上一份、或变成 {@code --}。报价单自己的状态**不因此回退**：
     * 状态是已经发生过的历史事实，不该随下游单据被删而改写。
     */
    private Map<Long, List<ContractDO>> loadContractsByQuotation(List<QuotationDO> records) {
        List<Long> quotationIds = quotationIdsOf(records);
        if (quotationIds.isEmpty()) {
            // ⚠️ 空集合会让 MP 生成非法的 IN ()；空 Map 必须用 Collections.emptyMap()
            return Collections.emptyMap();
        }
        // 只取派生字段真正要用的四列：省流量，也顺手避免把合同备注这类自由文本读进内存
        return contractMapper.selectList(Wrappers.<ContractDO>lambdaQuery()
                        .select(ContractDO::getContractId, ContractDO::getContractNo,
                                ContractDO::getQuotationId, ContractDO::getCreateTime)
                        .in(ContractDO::getQuotationId, quotationIds))
                .stream()
                .filter(contract -> contract.getQuotationId() != null)
                .collect(Collectors.groupingBy(ContractDO::getQuotationId));
    }

    /**
     * 每组合同里取「最新一份」。
     *
     * <p>用 {@link #compareContractRecency} 而不是直接 {@code max(createTime)}：
     * 同一秒建的两份合同（种子数据就是同一批 INSERT）时间完全相同，
     * 只比时间会得到不确定的结果 —— 同一份数据两次请求可能给出不同的合同链接。
     */
    private static Map<Long, ContractDO> latestOf(Map<Long, List<ContractDO>> byQuotation) {
        Map<Long, ContractDO> latest = new HashMap<>();
        byQuotation.forEach((quotationId, contracts) -> contracts.stream()
                .max(QuotationServiceImpl::compareContractRecency)
                .ifPresent(contract -> latest.put(quotationId, contract)));
        return latest;
    }

    /** 「哪一份更新」的判据：先比建单时间，再比主键；两者都可空，null 排在前（不抛 NPE） */
    private static int compareContractRecency(ContractDO a, ContractDO b) {
        int byCreateTime = Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())
                .compare(a.getCreateTime(), b.getCreateTime());
        if (byCreateTime != 0) {
            return byCreateTime;
        }
        return Comparator.nullsFirst(Comparator.<Long>naturalOrder())
                .compare(a.getContractId(), b.getContractId());
    }

    /** 每个报价单已生成的合同份数（1:N，允许重复转换） */
    private static Map<Long, Integer> countsOf(Map<Long, List<ContractDO>> byQuotation) {
        Map<Long, Integer> counts = new HashMap<>();
        byQuotation.forEach((quotationId, contracts) -> counts.put(quotationId, contracts.size()));
        return counts;
    }

    /** 商品总数 = 明细数量合计（可能有小数，如 2.5 米），去掉无意义的尾零再给前端 */
    private BigDecimal productCountOf(List<QuotationItemDO> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationItemDO item : items) {
            if (item.getQuantity() != null) {
                total = total.add(item.getQuantity());
            }
        }
        return MoneyCalculator.stripZeros(total);
    }

    /**
     * 整单利润 = Σ(行不含税 − ROUND(成本价 × 数量, 2))。
     *
     * <p>行不含税从**库里存的 {@code amount}** 按税率拆分得来（{@link MoneyCalculator#split}），
     * 这样「利润」与「{@code total_amount} 不含税合计」出自同一组数字。
     *
     * <p>成本取 {@code crm_product.cost_price}，用**不过滤逻辑删除**的
     * {@link ProductMiniMapper} 查 —— 产品下架/删除不代表这张报价单当初没有成本。
     *
     * @return 毛利；**有任意一行算不出成本时为 {@code null}**（明细没挂 {@code productId}，
     *         或产品在 {@code crm_product} 里查不到）。前端会显示 {@code --}
     */
    private BigDecimal profitOf(List<QuotationItemDO> items, Map<Long, ProductMiniDO> products) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<MoneyCalculator.ProfitLine> lines = new ArrayList<>(items.size());
        for (QuotationItemDO item : items) {
            ProductMiniDO product = item.getProductId() == null ? null : products.get(item.getProductId());
            lines.add(new MoneyCalculator.ProfitLine(
                    item.getQuantity(),
                    MoneyCalculator.split(item.getAmount(), item.getTaxRate()),
                    product == null ? null : product.getCostPrice()));
        }
        return MoneyCalculator.profit(lines);
    }

    /** 批量取明细，按 {@code quotation_id} 分组；组内按 {@code sort} 升序（同序时按主键，保证稳定） */
    private Map<Long, List<QuotationItemDO>> loadItems(Collection<Long> quotationIds) {
        List<Long> ids = quotationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            // ⚠️ 空集合会让 MP 生成非法的 IN ()；空 Map 必须用 Collections.emptyMap()
            return Collections.emptyMap();
        }
        return quotationItemMapper.selectList(Wrappers.<QuotationItemDO>lambdaQuery()
                        .in(QuotationItemDO::getQuotationId, ids)
                        .orderByAsc(QuotationItemDO::getSort)
                        .orderByAsc(QuotationItemDO::getItemId))
                .stream()
                .collect(Collectors.groupingBy(QuotationItemDO::getQuotationId));
    }

    /** 批量取客户名，避免 N+1 */
    private Map<Long, String> customerNameMap(List<QuotationDO> records) {
        List<Long> customerIds = records.stream()
                .map(QuotationDO::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (customerIds.isEmpty()) {
            // ⚠️ Map.of() 的 get(null) 会抛 NPE，这里必须用 Collections.emptyMap()
            return Collections.emptyMap();
        }
        return customerMiniMapper.selectList(CustomerMiniDO::getCustomerId, customerIds).stream()
                .filter(customer -> customer.getCustomerName() != null)
                .collect(Collectors.toMap(CustomerMiniDO::getCustomerId,
                        CustomerMiniDO::getCustomerName, (a, b) -> a));
    }

    /**
     * 批量取关联商机（只 select「ID / 名称 / 整单折扣」三列），一次查询同时供
     * 「报价商机」列与「整单折扣」用。
     *
     * <p><b>为什么折扣取自商机</b>：整单折扣是商务条件，全系统只在商机里维护一处
     *（{@code crm_opportunity.discount_rate}），报价单不再存第二份 —— 免得两边对不上。
     * 报价单的表单里也因此没有折扣字段。
     *
     * <p>走 {@code OpportunityMapper}（带 {@code @TableLogic}）→ **商机被逻辑删除后这里取不到**，
     * 前端的「报价商机」列显示 {@code --}、整单折扣显示 {@code 0%}。这是有意的：
     * 商机删了，报价单还挂着它已经不合理。数据权限同理 —— {@code crm_opportunity} 在拦截器
     * 白名单里，看不到的商机这里也取不到（见 docs/11 第八节）。
     */
    private Map<Long, OpportunityDO> loadOpportunities(List<QuotationDO> records) {
        List<Long> opportunityIds = records.stream()
                .map(QuotationDO::getOpportunityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (opportunityIds.isEmpty()) {
            // ⚠️ Map.of() 的 get(null) 会抛 NPE，必须用 Collections.emptyMap()
            return Collections.emptyMap();
        }
        return opportunityMapper.selectList(Wrappers.<OpportunityDO>lambdaQuery()
                        .select(OpportunityDO::getOpportunityId,
                                OpportunityDO::getOpportunityName,
                                OpportunityDO::getDiscountRate)
                        .in(OpportunityDO::getOpportunityId, opportunityIds))
                .stream()
                .collect(Collectors.toMap(OpportunityDO::getOpportunityId, opportunity -> opportunity, (a, b) -> a));
    }

    /**
     * 批量取用户姓名（负责人 / 创建人共用），避免 N+1。
     *
     * <p>{@code Collectors.toMap} 不接受 null 值，所以先过滤掉没有姓名的用户。
     */
    private Map<Long, String> userNameMap(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, ids).stream()
                .filter(user -> user.getRealName() != null)
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));
    }

    private List<Long> userIdsOf(List<QuotationDO> records) {
        List<Long> ids = new ArrayList<>(records.size() * 2);
        for (QuotationDO record : records) {
            if (record.getOwnerUserId() != null) {
                ids.add(record.getOwnerUserId());
            }
            if (record.getCreateBy() != null) {
                ids.add(record.getCreateBy());
            }
        }
        return ids;
    }

    private List<Long> productIdsOf(List<QuotationItemDO> items) {
        return items.stream().map(QuotationItemDO::getProductId).filter(Objects::nonNull).distinct().toList();
    }

    private List<Long> unitIdsOf(List<QuotationItemDO> items) {
        return items.stream().map(QuotationItemDO::getUnitId).filter(Objects::nonNull).distinct().toList();
    }

    // ==================== 内部方法：校验 ====================

    private QuotationDO getQuotationDO(Long id) {
        QuotationDO quotationDO = quotationMapper.selectById(id);
        if (quotationDO == null) {
            throw new ServiceException(ErrorCode.QUOTATION_NOT_EXISTS);
        }
        return quotationDO;
    }

    /** 逻辑外键校验：客户必须存在（建表文档 1.6，应用层负责引用完整性） */
    private void requireCustomerExists(Long customerId) {
        if (customerId == null) {
            return;
        }
        if (customerMiniMapper.selectById(customerId) == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
    }

    /**
     * 逻辑外键校验：商机必须存在（可空 —— 可以直接给客户报价，不挂商机），
     * **且属于报价单的客户**。
     *
     * <p>客户一致性原先没人管：{@code customer_id} 与 {@code opportunity_id} 各校验各的存在性，
     * 于是能把「客户A + 商机B」存进去 —— 报价单挂在别人的商机上，列表照常显示、
     * 事后只能靠人眼比对。与合同侧的 {@code ContractRelationSupport} 同一取舍：
     * **拒绝而不是静默改写客户**（改客户会连带改变数据权限与统计口径）。
     *
     * <p>前端（{@code views/crm/quotation/index.vue}）选中商机时会把客户对齐成商机的客户
     * 并置为只读，所以正常操作撞不到 {@code 2253}；这一关拦的是非 UI 调用与历史脏数据。
     *
     * <p>⚠️ 复用这里的这一次 {@code selectById} 顺带比对客户，**不多一次查询**。
     */
    private void requireOpportunityOfCustomer(Long opportunityId, Long customerId) {
        if (opportunityId == null) {
            return;
        }
        OpportunityDO opportunity = opportunityMapper.selectById(opportunityId);
        if (opportunity == null) {
            throw new ServiceException(ErrorCode.OPPORTUNITY_NOT_EXISTS,
                    "关联的商机不存在：" + opportunityId);
        }
        if (!Objects.equals(opportunity.getCustomerId(), customerId)) {
            throw new ServiceException(ErrorCode.QUOTATION_RELATION_MISMATCH,
                    "所选商机属于其它客户，请重新选择商机");
        }
    }

    /** 逻辑外键校验：负责人必须是真实存在的用户，并返回该用户（用于取部门） */
    private SysUserMiniDO requireUserExists(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserMiniDO user = sysUserMiniMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException(ErrorCode.USER_NOT_EXISTS, "负责人不存在：" + userId);
        }
        return user;
    }

    /** 过滤条件命中「不可能有结果」的场景（如仅本人范围下查下属）时的空分页结果 */
    private <T> PageResult<T> emptyResult(PageParam reqVO) {
        Page<?> page = reqVO.toPage();
        return PageResult.empty((int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 比对客户端提交的三个金额与服务端重算值。
     *
     * <p><b>只记日志，不拒绝</b>：前端 {@code calcItemsTotal} 用 JS 浮点 + {@code round2}，
     * 服务端用 BigDecimal；在边界值上可能差 0.01，严格校验会把用户的保存直接卡死，
     * 而服务端重算已经保证库里是一定正确的。日志用于发现真正的口径漂移。
     */
    private void logClientAmountMismatch(QuotationSaveReqVO reqVO, MoneyCalculator.DocumentTotals totals) {
        logIfMismatch("合计价格(不含税)", reqVO.getTotalAmount(), totals.totalAmount());
        logIfMismatch("税额", reqVO.getTaxAmount(), totals.taxAmount());
        logIfMismatch("整单最终价(含税)", reqVO.getFinalAmount(), totals.totalWithTax());
    }

    private void logIfMismatch(String field, BigDecimal clientValue, BigDecimal serverValue) {
        if (clientValue == null || clientValue.compareTo(serverValue) == 0) {
            return;
        }
        log.debug("[报价单] 客户端提交的{}与明细重算不一致，已按服务端值落库：client={}, server={}",
                field, clientValue, serverValue);
    }
}
