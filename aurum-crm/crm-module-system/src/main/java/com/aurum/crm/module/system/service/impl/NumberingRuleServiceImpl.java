package com.aurum.crm.module.system.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.system.dal.dataobject.NumberingRuleCounterDO;
import com.aurum.crm.module.system.dal.dataobject.NumberingRuleDO;
import com.aurum.crm.module.system.dal.dataobject.NumberingRuleItemDO;
import com.aurum.crm.module.system.dal.mysql.NumberingRuleCounterMapper;
import com.aurum.crm.module.system.dal.mysql.NumberingRuleItemMapper;
import com.aurum.crm.module.system.dal.mysql.NumberingRuleMapper;
import com.aurum.crm.module.system.model.dto.NumberingRuleItemSaveReqVO;
import com.aurum.crm.module.system.model.dto.NumberingRulePageReqVO;
import com.aurum.crm.module.system.model.dto.NumberingRuleSaveReqVO;
import com.aurum.crm.module.system.model.vo.NumberingRuleItemRespVO;
import com.aurum.crm.module.system.model.vo.NumberingRuleRespVO;
import com.aurum.crm.module.system.service.NumberingRuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 编号规则配置与生成引擎。编号项保存后下一次生成立即读取最新配置。 */
@Service
public class NumberingRuleServiceImpl implements NumberingRuleService {
    private record FixedPrefix(String prefix, int length) {}

    private static final Map<String, FixedPrefix> FIXED_PREFIXES = Map.of(
            "LEAD", new FixedPrefix("XS", 4), "CUSTOMER", new FixedPrefix("KH", 4),
            "OPP", new FixedPrefix("SJ", 4), "QUOTE", new FixedPrefix("BJ", 4),
            "CONTRACT", new FixedPrefix("HT", 6), "PAYMENT", new FixedPrefix("HK", 6),
            "INVOICE", new FixedPrefix("FP", 6));
    private static final List<String> ITEM_TYPES = List.of("FIXED_PREFIX", "FIXED_VALUE", "YEAR",
            "YEAR_SHORT", "MONTH", "DAY", "DATE", "CUSTOMER", "DEPARTMENT", "SERIAL");

    @Resource private NumberingRuleMapper mapper;
    @Resource private NumberingRuleItemMapper itemMapper;
    @Resource private NumberingRuleCounterMapper counterMapper;

    @Override
    public PageResult<NumberingRuleRespVO> page(NumberingRulePageReqVO req) {
        var w = Wrappers.<NumberingRuleDO>lambdaQuery();
        if (StringUtils.hasText(req.getKeyword())) {
            w.and(x -> x.like(NumberingRuleDO::getRuleName, req.getKeyword().trim())
                    .or().like(NumberingRuleDO::getRuleCode, req.getKeyword().trim()));
        }
        Page<NumberingRuleDO> page = mapper.selectPage(req, w.orderByAsc(NumberingRuleDO::getRuleId));
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(NumberingRuleSaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "编号名称和编号编码不能为空");
        }
        String code = req.getCode().trim().toUpperCase(Locale.ROOT);
        FixedPrefix fixed = FIXED_PREFIXES.get(code);
        if (fixed == null) throw new ServiceException(ErrorCode.BAD_REQUEST, "不支持的编号编码：" + code);
        if (mapper.exists(NumberingRuleDO::getRuleCode, code)) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "编号编码已存在");
        }
        NumberingRuleDO rule = new NumberingRuleDO();
        rule.setRuleId(IdUtil.getSnowflakeNextId());
        rule.setRuleCode(code);
        rule.setRuleName(req.getName().trim());
        rule.setPrefix(fixed.prefix());
        rule.setDateFormat(StringUtils.hasText(req.getDateFormat()) ? req.getDateFormat() : "yyyyMMdd");
        rule.setSeqLength(req.getSeqLength() == null ? fixed.length() : req.getSeqLength());
        rule.setResetCycle(resetValue(req.getResetCycle()));
        rule.setRemark(req.getRemark());
        rule.setCurrentSeq(0L);
        rule.setEnabled(req.getEnabled() == null || req.getEnabled() ? 1 : 0);
        mapper.insert(rule);
        replaceItems(rule, req.getItems());
        return rule.getRuleId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NumberingRuleSaveReqVO req) {
        NumberingRuleDO current = mapper.selectById(id);
        if (current == null) throw new ServiceException(ErrorCode.NOT_FOUND, "编号规则不存在");
        if (StringUtils.hasText(req.getCode()) && !req.getCode().trim().equalsIgnoreCase(current.getRuleCode())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "编号编码由系统固定，不允许修改");
        }
        FixedPrefix fixed = FIXED_PREFIXES.get(current.getRuleCode());
        if (StringUtils.hasText(req.getPrefix()) && fixed != null && !fixed.prefix().equals(req.getPrefix().trim())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "系统业务前缀固定为 " + fixed.prefix());
        }
        NumberingRuleDO update = new NumberingRuleDO();
        update.setRuleId(id);
        if (req.getEnabled() != null) update.setEnabled(req.getEnabled() ? 1 : 0);
        if (StringUtils.hasText(req.getName())) update.setRuleName(req.getName().trim());
        if (StringUtils.hasText(req.getDateFormat())) update.setDateFormat(req.getDateFormat().trim());
        if (req.getSeqLength() != null) update.setSeqLength(req.getSeqLength());
        if (req.getResetCycle() != null) update.setResetCycle(resetValue(req.getResetCycle()));
        if (req.getRemark() != null) update.setRemark(req.getRemark());
        if (fixed != null) update.setPrefix(fixed.prefix());
        mapper.updateById(update);
        if (req.getItems() != null) replaceItems(current, req.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.selectById(id) == null) throw new ServiceException(ErrorCode.NOT_FOUND, "编号规则不存在");
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String next(String ruleCode) { return next(ruleCode, Map.of()); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String next(String ruleCode, Map<String, Object> context) {
        NumberingRuleDO rule = mapper.selectOne(NumberingRuleDO::getRuleCode, ruleCode);
        if (rule == null || !Integer.valueOf(1).equals(rule.getEnabled())) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "编号规则未启用：" + ruleCode);
        }
        List<NumberingRuleItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<NumberingRuleItemDO>()
                .eq(NumberingRuleItemDO::getRuleId, rule.getRuleId())
                .orderByAsc(NumberingRuleItemDO::getSort).orderByAsc(NumberingRuleItemDO::getItemId));
        if (items.isEmpty()) items = legacyItems(rule);
        NumberingRuleItemDO serial = items.stream().filter(i -> "SERIAL".equalsIgnoreCase(i.getItemType()))
                .findFirst().orElseThrow(() -> new ServiceException(ErrorCode.BAD_REQUEST, "编号规则必须包含流水号节点"));
        String basisKey = basisKey(items, context);
        int length = serial.getItemLength() == null ? (rule.getSeqLength() == null ? 4 : rule.getSeqLength()) : serial.getItemLength();
        long sequence = nextCounter(rule.getRuleId(), basisKey);
        if (sequence > (long) Math.pow(10, length) - 1) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "当前编号规则已超过" + length + "位流水号上限：" + ruleCode);
        }
        String serialText = String.format("%0" + length + "d", sequence);
        StringBuilder result = new StringBuilder();
        for (NumberingRuleItemDO item : items) {
            result.append("SERIAL".equalsIgnoreCase(item.getItemType()) ? serialText : render(item, context));
        }
        return result.toString();
    }

    private long nextCounter(Long ruleId, String basisKey) {
        for (int attempt = 0; attempt < 8; attempt++) {
            NumberingRuleCounterDO counter = counterMapper.selectOne(new LambdaQueryWrapper<NumberingRuleCounterDO>()
                    .eq(NumberingRuleCounterDO::getRuleId, ruleId).eq(NumberingRuleCounterDO::getBasisKey, basisKey));
            if (counter == null) {
                NumberingRuleCounterDO created = new NumberingRuleCounterDO();
                created.setCounterId(IdUtil.getSnowflakeNextId());
                created.setRuleId(ruleId); created.setBasisKey(basisKey); created.setCurrentSeq(1L);
                try { counterMapper.insert(created); return 1L; }
                catch (DuplicateKeyException ignored) { continue; }
            }
            long current = counter.getCurrentSeq() == null ? 0L : counter.getCurrentSeq();
            long next = current + 1L;
            LambdaUpdateWrapper<NumberingRuleCounterDO> update = Wrappers.lambdaUpdate(NumberingRuleCounterDO.class)
                    .eq(NumberingRuleCounterDO::getCounterId, counter.getCounterId())
                    .eq(NumberingRuleCounterDO::getCurrentSeq, current)
                    .set(NumberingRuleCounterDO::getCurrentSeq, next);
            if (counterMapper.update(null, update) == 1) return next;
        }
        throw new ServiceException(ErrorCode.CONCURRENT_OPERATION, "编号生成冲突，请稍后重试");
    }

    private String basisKey(List<NumberingRuleItemDO> items, Map<String, Object> context) {
        List<String> values = new ArrayList<>();
        for (NumberingRuleItemDO item : items) {
            if (!Integer.valueOf(1).equals(item.getSequenceBasis()) || "SERIAL".equalsIgnoreCase(item.getItemType())) continue;
            values.add(render(item, context));
        }
        return values.isEmpty() ? "ALL" : String.join("|", values);
    }

    private String render(NumberingRuleItemDO item, Map<String, Object> context) {
        String type = item.getItemType() == null ? "FIXED_VALUE" : item.getItemType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "FIXED_PREFIX", "FIXED_VALUE" -> Objects.toString(item.getItemValue(), "");
            case "YEAR" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
            case "YEAR_SHORT" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yy"));
            case "MONTH" -> LocalDate.now().format(DateTimeFormatter.ofPattern("MM"));
            case "DAY" -> LocalDate.now().format(DateTimeFormatter.ofPattern("dd"));
            case "DATE" -> LocalDate.now().format(DateTimeFormatter.ofPattern(StringUtils.hasText(item.getItemValue()) ? item.getItemValue() : "yyyyMMdd"));
            case "CUSTOMER" -> contextValue(context, "customerId", "customerNo");
            case "DEPARTMENT" -> contextValue(context, "departmentId", "deptId");
            case "SERIAL" -> "";
            default -> throw new ServiceException(ErrorCode.BAD_REQUEST, "不支持的编号项类型：" + type);
        };
    }

    private String contextValue(Map<String, Object> context, String primary, String secondary) {
        Object value = context == null ? null : context.get(primary);
        if (value == null && context != null) value = context.get(secondary);
        if (value == null && "deptId".equals(secondary)) value = LoginUserHolder.getDeptId();
        if (value == null) throw new ServiceException(ErrorCode.BAD_REQUEST, "编号规则需要业务上下文：" + primary);
        return String.valueOf(value);
    }

    private List<NumberingRuleItemDO> legacyItems(NumberingRuleDO rule) {
        List<NumberingRuleItemDO> items = new ArrayList<>();
        addItem(items, "FIXED_PREFIX", rule.getPrefix(), null, false);
        if (StringUtils.hasText(rule.getDateFormat())) addItem(items, "DATE", rule.getDateFormat(), null, rule.getResetCycle() != null && rule.getResetCycle() > 0);
        addItem(items, "SERIAL", null, rule.getSeqLength(), true);
        return items;
    }

    private void addItem(List<NumberingRuleItemDO> items, String type, String value, Integer length, boolean basis) {
        NumberingRuleItemDO item = new NumberingRuleItemDO(); item.setItemType(type); item.setItemValue(value);
        item.setItemLength(length); item.setSort(items.size()); item.setSequenceBasis(basis ? 1 : 0); items.add(item);
    }

    private void replaceItems(NumberingRuleDO rule, List<NumberingRuleItemSaveReqVO> requested) {
        List<NumberingRuleItemSaveReqVO> normalized = new ArrayList<>(requested == null || requested.isEmpty() ? defaultItems(rule) : requested);
        FixedPrefix fixed = FIXED_PREFIXES.get(rule.getRuleCode());
        long serialCount = normalized.stream().filter(i -> "SERIAL".equalsIgnoreCase(i.getType())).count();
        if (serialCount != 1) {
            throw new ServiceException(ErrorCode.BAD_REQUEST, "编号规则必须且只能保留一个流水号节点");
        }
        for (NumberingRuleItemSaveReqVO req : normalized) {
            String type = req.getType() == null ? "FIXED_VALUE" : req.getType().trim().toUpperCase(Locale.ROOT);
            if (!ITEM_TYPES.contains(type)) throw new ServiceException(ErrorCode.BAD_REQUEST, "不支持的编号项类型：" + type);
            if ("DATE".equals(type)) {
                try {
                    DateTimeFormatter.ofPattern(StringUtils.hasText(req.getValue()) ? req.getValue() : "yyyyMMdd");
                } catch (IllegalArgumentException ex) {
                    throw new ServiceException(ErrorCode.BAD_REQUEST, "日期编号项格式不正确：" + req.getValue());
                }
            }
            req.setType(type); req.setSequenceBasis(Boolean.TRUE.equals(req.getSequenceBasis()));
            if ("SERIAL".equals(type) && (req.getLength() == null || req.getLength() < 1)) req.setLength(rule.getSeqLength() == null ? 4 : rule.getSeqLength());
        }
        if (fixed != null) {
            long prefixCount = normalized.stream().filter(i -> "FIXED_PREFIX".equalsIgnoreCase(i.getType())).count();
            if (prefixCount > 1) throw new ServiceException(ErrorCode.BAD_REQUEST, "系统固定前缀只能有一个");
            NumberingRuleItemSaveReqVO prefix = normalized.stream().filter(i -> "FIXED_PREFIX".equalsIgnoreCase(i.getType())).findFirst().orElse(null);
            if (prefix == null) { prefix = new NumberingRuleItemSaveReqVO(); prefix.setType("FIXED_PREFIX"); normalized.add(0, prefix); }
            prefix.setValue(fixed.prefix()); prefix.setSequenceBasis(false);
        }
        itemMapper.delete(new LambdaQueryWrapper<NumberingRuleItemDO>().eq(NumberingRuleItemDO::getRuleId, rule.getRuleId()));
        for (int i = 0; i < normalized.size(); i++) {
            NumberingRuleItemSaveReqVO req = normalized.get(i); NumberingRuleItemDO item = new NumberingRuleItemDO();
            item.setItemId(IdUtil.getSnowflakeNextId()); item.setRuleId(rule.getRuleId()); item.setItemType(req.getType());
            item.setItemValue(req.getValue()); item.setItemLength(req.getLength()); item.setSort(i);
            item.setSequenceBasis(Boolean.TRUE.equals(req.getSequenceBasis()) ? 1 : 0); itemMapper.insert(item);
        }
    }

    private List<NumberingRuleItemSaveReqVO> defaultItems(NumberingRuleDO rule) {
        List<NumberingRuleItemSaveReqVO> items = new ArrayList<>();
        addReq(items, "FIXED_PREFIX", rule.getPrefix(), null, false);
        addReq(items, "YEAR", null, null, rule.getResetCycle() != null && rule.getResetCycle() >= 3);
        addReq(items, "MONTH", null, null, rule.getResetCycle() != null && rule.getResetCycle() == 2);
        addReq(items, "DAY", null, null, rule.getResetCycle() != null && rule.getResetCycle() == 1);
        addReq(items, "SERIAL", null, rule.getSeqLength(), true);
        return items;
    }

    private void addReq(List<NumberingRuleItemSaveReqVO> items, String type, String value, Integer length, boolean basis) {
        NumberingRuleItemSaveReqVO req = new NumberingRuleItemSaveReqVO(); req.setType(type); req.setValue(value);
        req.setLength(length); req.setSequenceBasis(basis); items.add(req);
    }

    private int resetValue(String value) {
        if (value == null) return 0;
        return switch (value) { case "按日" -> 1; case "按月" -> 2; case "按年" -> 3; default -> 0; };
    }

    private NumberingRuleRespVO toVO(NumberingRuleDO rule) {
        NumberingRuleRespVO vo = new NumberingRuleRespVO(); vo.setId(rule.getRuleId()); vo.setName(rule.getRuleName());
        vo.setCode(rule.getRuleCode()); vo.setPrefix(rule.getPrefix()); vo.setDateFormat(rule.getDateFormat()); vo.setSeqLength(rule.getSeqLength());
        vo.setResetCycle(switch (rule.getResetCycle() == null ? 0 : rule.getResetCycle()) { case 1 -> "按日"; case 2 -> "按月"; case 3 -> "按年"; default -> "不重置"; });
        vo.setEnabled(Integer.valueOf(1).equals(rule.getEnabled()));
        List<NumberingRuleItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<NumberingRuleItemDO>().eq(NumberingRuleItemDO::getRuleId, rule.getRuleId()).orderByAsc(NumberingRuleItemDO::getSort));
        if (items.isEmpty()) items = legacyItems(rule);
        vo.setItems(items.stream().map(this::toItemVO).toList()); vo.setExample(example(items)); return vo;
    }

    private NumberingRuleItemRespVO toItemVO(NumberingRuleItemDO item) {
        NumberingRuleItemRespVO vo = new NumberingRuleItemRespVO(); vo.setId(item.getItemId()); vo.setType(item.getItemType());
        vo.setValue(item.getItemValue()); vo.setLength(item.getItemLength()); vo.setSort(item.getSort()); vo.setSequenceBasis(Integer.valueOf(1).equals(item.getSequenceBasis())); return vo;
    }

    private String example(List<NumberingRuleItemDO> items) {
        StringBuilder result = new StringBuilder();
        for (NumberingRuleItemDO item : items) {
            if ("SERIAL".equalsIgnoreCase(item.getItemType())) {
                int length = item.getItemLength() == null ? 4 : item.getItemLength(); result.append(String.format("%0" + length + "d", 1));
            } else {
                try { result.append(render(item, Map.of())); } catch (ServiceException ignored) { result.append("{业务值}"); }
            }
        }
        return result.toString();
    }
}
