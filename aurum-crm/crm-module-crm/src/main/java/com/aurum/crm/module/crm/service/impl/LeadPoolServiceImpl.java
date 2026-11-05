package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.LeadMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.LeadDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.LeadConvert;
import com.aurum.crm.module.crm.model.dto.LeadPageReqDTO;
import com.aurum.crm.module.crm.model.dto.LeadPoolAssignReqDTO;
import com.aurum.crm.module.crm.model.vo.LeadPoolExportRowVO;
import com.aurum.crm.module.crm.model.vo.LeadRespVO;
import com.aurum.crm.module.crm.service.LeadPoolService;
import com.aurum.crm.module.crm.service.PoolClaimLimiter;
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
import java.util.stream.Collectors;

/**
 * 线索池 Service 实现：线索池列表 / 领取 / 分配 / 删除。
 *
 * <p>线索池定义为 {@code owner_user_id IS NULL} 的线索（见《02-建表文档》）。
 * 领取采用「UPDATE ... WHERE owner IS NULL」的原子 CAS 保证并发安全——
 * 两个用户同一时刻抢同一条，只有一个 update 受影响行数为 1，另一个抛 4902。
 */
@Slf4j
@Service
public class LeadPoolServiceImpl implements LeadPoolService {

    @Resource
    private LeadMapper leadMapper;
    @Resource
    private OwnerChangeLogMapper ownerChangeLogMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private LeadConvert leadConvert;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private PoolClaimLimiter poolClaimLimiter;

    @Override
    public PageResult<LeadRespVO> poolPage(LeadPageReqDTO reqDTO) {
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.lambdaQuery();
        // 线索池 = owner_user_id IS NULL。此页有意不处理 scope：池内每行 owner 都是 NULL，
        // mine / subordinate 必然筛空；且前端池页未挂筛选行（FILTERS = []）与关注列
        // （:starrable="false"），所以 scope 与 starred 都无处可用。
        // 两者都在线索列表里处理，见 LeadServiceImpl#applyScope 与 #toPageResult。
        wrapper.isNull(LeadDO::getOwnerUserId);
        if (StringUtils.hasText(reqDTO.getKeyword())) {
            String keyword = reqDTO.getKeyword();
            wrapper.and(w -> w.like(LeadDO::getLeadName, keyword)
                    .or().like(LeadDO::getContactName, keyword)
                    .or().like(LeadDO::getPhone, keyword));
        }
        if (StringUtils.hasText(reqDTO.getLeadName())) {
            wrapper.like(LeadDO::getLeadName, reqDTO.getLeadName());
        }
        if (reqDTO.getSource() != null) {
            wrapper.eq(LeadDO::getSource, reqDTO.getSource());
        }
        if (reqDTO.getIndustry() != null) {
            wrapper.eq(LeadDO::getIndustry, reqDTO.getIndustry());
        }
        // 入池时间倒序（原型的「入池时间」列）
        wrapper.orderByDesc(LeadDO::getEnterPoolTime);

        Page<LeadDO> page = leadMapper.selectPage(reqDTO, wrapper);
        List<LeadDO> records = page.getRecords();
        // 「放入人」= 进池前的负责人。DO 里只有 prev_owner_id，姓名批量补一次，
        // 不要在 Convert 里逐行查库（N+1）—— 同 buildExportRows 的做法。
        Map<Long, String> prevOwnerNames = userIdNameMap(records.stream()
                .map(LeadDO::getPrevOwnerId)
                .toList());
        List<LeadRespVO> list = records.stream()
                .map(l -> {
                    LeadRespVO vo = leadConvert.toRespVO(l);
                    vo.setPrevOwnerName(prevOwnerNames.get(l.getPrevOwnerId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        poolClaimLimiter.checkBeforeClaim(1, 1, loginUser.getUserId());
        LeadDO leadDO = leadMapper.selectById(id);
        if (leadDO == null) {
            throw new ServiceException(ErrorCode.LEAD_NOT_EXISTS);
        }
        if (leadDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.LEAD_ALREADY_OWNED);
        }
        // 原子 CAS：owner 为空的才更新为当前用户，防止两人同时抢到
        LeadDO update = new LeadDO();
        update.setOwnerUserId(loginUser.getUserId());
        update.setDeptId(loginUser.getDeptId());
        int affected = leadMapper.update(update,
                Wrappers.<LeadDO>lambdaUpdate()
                        .eq(LeadDO::getLeadId, id)
                        .isNull(LeadDO::getOwnerUserId));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.LEAD_ALREADY_OWNED);
        }
        recordLog(leadDO, 2, leadDO.getLeadName(),
                null, loginUser.getUserId(), null, "领取线索池线索");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, LeadPoolAssignReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getOwnerUserId() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        LoginUser operator = LoginUserHolder.getRequired();
        LeadDO leadDO = leadMapper.selectById(id);
        if (leadDO == null) {
            throw new ServiceException(ErrorCode.LEAD_NOT_EXISTS);
        }
        if (leadDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.LEAD_ALREADY_OWNED);
        }
        LeadDO update = new LeadDO();
        update.setOwnerUserId(reqDTO.getOwnerUserId());
        update.setDeptId(operator.getDeptId());
        int affected = leadMapper.update(update,
                Wrappers.<LeadDO>lambdaUpdate()
                        .eq(LeadDO::getLeadId, id)
                        .isNull(LeadDO::getOwnerUserId));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.LEAD_ALREADY_OWNED);
        }
        recordLog(leadDO, 5, leadDO.getLeadName(),
                null, reqDTO.getOwnerUserId(), null, reqDTO.getReasonRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LeadDO leadDO = leadMapper.selectById(id);
        if (leadDO == null) {
            throw new ServiceException(ErrorCode.LEAD_NOT_EXISTS);
        }
        if (leadDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.LEAD_NOT_IN_POOL);
        }
        leadMapper.deleteById(id);
    }

    @Override
    public String export(LeadPageReqDTO reqDTO) {
        List<LeadPoolExportRowVO> rows = buildExportRows(reqDTO);
        return ExportSupport.toUrl(ossStorageService, rows, LeadPoolExportRowVO.class);
    }

    @Override
    public String exportXml(LeadPageReqDTO reqDTO) {
        List<LeadPoolExportRowVO> rows = buildExportRows(reqDTO);
        return ExportSupport.toXmlUrl(ossStorageService, rows, LeadPoolExportRowVO.class);
    }

    /** 导出用：复用线索池查询条件查数并映射为导出行 */
    private List<LeadPoolExportRowVO> buildExportRows(LeadPageReqDTO reqDTO) {
        List<LeadDO> records = poolList(reqDTO);
        Map<Long, String> prevOwnerNames = userIdNameMap(records.stream()
                .map(LeadDO::getPrevOwnerId).toList());

        return records.stream().map(l -> {
            LeadPoolExportRowVO row = new LeadPoolExportRowVO();
            row.setLeadNo(l.getLeadNo());
            row.setLeadName(l.getLeadName());
            row.setContactName(l.getContactName());
            row.setPosition(l.getPosition());
            row.setPhone(l.getPhone());
            row.setIndustry(DictLabels.industry(l.getIndustry()));
            row.setSource(DictLabels.source(l.getSource()));
            row.setProvince(l.getProvince());
            row.setCity(l.getCity());
            row.setDistrict(l.getDistrict());
            row.setAddress(l.getAddress());
            row.setEnterPoolTime(ExportSupport.timeStr(l.getEnterPoolTime()));
            row.setEnterPoolReason(DictLabels.poolReason(l.getEnterPoolReasonCode()));
            row.setEnterPoolRemark(l.getEnterPoolRemark());
            row.setPrevOwnerName(prevOwnerNames.get(l.getPrevOwnerId()));
            row.setCreateTime(ExportSupport.timeStr(l.getCreateTime()));
            return row;
        }).collect(Collectors.toList());
    }

    /** 线索池全量查询（不翻页），与 poolPage 同一套过滤 */
    private List<LeadDO> poolList(LeadPageReqDTO reqDTO) {
        LambdaQueryWrapper<LeadDO> wrapper = Wrappers.lambdaQuery();
        wrapper.isNull(LeadDO::getOwnerUserId);
        if (StringUtils.hasText(reqDTO.getKeyword())) {
            String keyword = reqDTO.getKeyword();
            wrapper.and(w -> w.like(LeadDO::getLeadName, keyword)
                    .or().like(LeadDO::getContactName, keyword)
                    .or().like(LeadDO::getPhone, keyword));
        }
        if (StringUtils.hasText(reqDTO.getLeadName())) {
            wrapper.like(LeadDO::getLeadName, reqDTO.getLeadName());
        }
        if (reqDTO.getSource() != null) {
            wrapper.eq(LeadDO::getSource, reqDTO.getSource());
        }
        if (reqDTO.getIndustry() != null) {
            wrapper.eq(LeadDO::getIndustry, reqDTO.getIndustry());
        }
        wrapper.orderByDesc(LeadDO::getEnterPoolTime);
        return leadMapper.selectList(wrapper);
    }

    /** 批量查用户姓名（前负责人用） */
    private Map<Long, String> userIdNameMap(List<Long> userIds) {
        List<Long> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMiniMapper.selectList(SysUserMiniDO::getUserId, distinct).stream()
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName,
                        (a, b) -> a));
    }

    /** 写入归属流转记录 */
    private void recordLog(LeadDO lead, Integer action, String bizName,
                           Long fromOwner, Long toOwner, Integer reasonCode, String reasonRemark) {
        OwnerChangeLogDO logDO = new OwnerChangeLogDO();
        logDO.setBizType(1);
        logDO.setBizId(lead.getLeadId());
        logDO.setBizName(bizName);
        logDO.setAction(action);
        logDO.setFromOwnerId(fromOwner);
        logDO.setToOwnerId(toOwner);
        logDO.setReasonCode(reasonCode);
        logDO.setReasonRemark(reasonRemark);
        logDO.setOperatorId(LoginUserHolder.getUserId());
        ownerChangeLogMapper.insert(logDO);
    }
}
