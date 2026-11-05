package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.core.LoginUser;
import com.aurum.crm.common.core.LoginUserHolder;
import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;
import com.aurum.crm.module.crm.mapper.CustomerMapper;
import com.aurum.crm.module.crm.mapper.OwnerChangeLogMapper;
import com.aurum.crm.module.crm.mapper.SysUserMiniMapper;
import com.aurum.crm.module.crm.model.CustomerDO;
import com.aurum.crm.module.crm.model.DictLabels;
import com.aurum.crm.module.crm.model.OwnerChangeLogDO;
import com.aurum.crm.module.crm.model.SysUserMiniDO;
import com.aurum.crm.module.crm.model.convert.CustomerConvert;
import com.aurum.crm.module.crm.model.dto.CustomerPageReqVO;
import com.aurum.crm.module.crm.model.dto.CustomerPoolAssignReqVO;
import com.aurum.crm.module.crm.model.vo.CustomerPoolExportRowVO;
import com.aurum.crm.module.crm.model.vo.CustomerRespVO;
import com.aurum.crm.module.crm.service.CustomerPoolService;
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

import static com.aurum.crm.module.crm.service.impl.ExportSupport.timeStr;

/**
 * 公海池 Service 实现：公海列表 / 领取 / 分配 / 删除。
 *
 * <p>公海定义为 {@code owner_user_id IS NULL} 的客户（见《02-建表文档》）。
 * 领取采用「UPDATE ... WHERE owner IS NULL」的原子 CAS 保证并发安全——
 * 两个用户同一时刻抢同一条，只有一个 update 受影响行数为 1，另一个抛 4902。
 */
@Slf4j
@Service
public class CustomerPoolServiceImpl implements CustomerPoolService {

    @Resource
    private CustomerMapper customerMapper;
    @Resource
    private OwnerChangeLogMapper ownerChangeLogMapper;
    @Resource
    private SysUserMiniMapper sysUserMiniMapper;
    @Resource
    private CustomerConvert customerConvert;
    @Resource
    private OssStorageService ossStorageService;
    @Resource
    private PoolClaimLimiter poolClaimLimiter;

    @Override
    public String export(CustomerPageReqVO reqVO) {
        return ExportSupport.toUrl(ossStorageService, buildExportRows(reqVO), CustomerPoolExportRowVO.class);
    }

    @Override
    public String exportXml(CustomerPageReqVO reqVO) {
        return ExportSupport.toXmlUrl(ossStorageService, buildExportRows(reqVO), CustomerPoolExportRowVO.class);
    }

    private List<CustomerPoolExportRowVO> buildExportRows(CustomerPageReqVO reqVO) {
        LambdaQueryWrapper<CustomerDO> wrapper = Wrappers.lambdaQuery();
        wrapper.isNull(CustomerDO::getOwnerUserId);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getKeyword());
        }
        if (StringUtils.hasText(reqVO.getCustomerName())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getCustomerName());
        }
        if (reqVO.getIndustry() != null) {
            wrapper.eq(CustomerDO::getIndustry, reqVO.getIndustry());
        }
        if (reqVO.getSource() != null) {
            wrapper.eq(CustomerDO::getSource, reqVO.getSource());
        }
        if (reqVO.getLevel() != null) {
            wrapper.eq(CustomerDO::getLevel, reqVO.getLevel());
        }
        wrapper.orderByDesc(CustomerDO::getEnterPoolTime);

        List<CustomerDO> records = customerMapper.selectList(wrapper);
        Map<Long, String> prevOwnerNames = records.stream().map(CustomerDO::getPrevOwnerId)
                .filter(Objects::nonNull).distinct().toList().isEmpty()
                // ⚠️ 必须用 Collections.emptyMap() 而不是 Map.of()：Map.of() 返回的是不可变
                //    MapN，size 为 0 时 get() 会先 requireNonNull(key) —— 下面的 get(prevOwnerId)
                //    在 prevOwnerId 为 null 时（导入/历史数据）直接 NPE，整个导出接口 500。
                ? Collections.emptyMap()
                : sysUserMiniMapper.selectList(SysUserMiniDO::getUserId,
                        records.stream().map(CustomerDO::getPrevOwnerId)
                                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(SysUserMiniDO::getUserId, SysUserMiniDO::getRealName, (a, b) -> a));

        return records.stream().map(c -> {
            CustomerPoolExportRowVO row = new CustomerPoolExportRowVO();
            row.setCustomerNo(c.getCustomerNo());
            row.setCustomerName(c.getCustomerName());
            row.setIndustry(DictLabels.industry(c.getIndustry()));
            row.setLevel(DictLabels.level(c.getLevel()));
            row.setDealStatus(DictLabels.dealStatus(c.getDealStatus()));
            row.setSource(DictLabels.source(c.getSource()));
            row.setPhone(c.getPhone());
            row.setProvince(c.getProvince());
            row.setCity(c.getCity());
            row.setDistrict(c.getDistrict());
            row.setAddress(c.getAddress());
            row.setEnterPoolTime(timeStr(c.getEnterPoolTime()));
            row.setEnterPoolReason(DictLabels.poolReason(c.getEnterPoolReasonCode()));
            row.setEnterPoolRemark(c.getEnterPoolRemark());
            row.setPrevOwnerName(prevOwnerNames.get(c.getPrevOwnerId()));
            return row;
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<CustomerRespVO> poolPage(CustomerPageReqVO reqVO) {
        LambdaQueryWrapper<CustomerDO> wrapper = Wrappers.lambdaQuery();
        wrapper.isNull(CustomerDO::getOwnerUserId);
        if (StringUtils.hasText(reqVO.getKeyword())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getKeyword());
        }
        if (StringUtils.hasText(reqVO.getCustomerName())) {
            wrapper.like(CustomerDO::getCustomerName, reqVO.getCustomerName());
        }
        if (reqVO.getIndustry() != null) {
            wrapper.eq(CustomerDO::getIndustry, reqVO.getIndustry());
        }
        if (reqVO.getSource() != null) {
            wrapper.eq(CustomerDO::getSource, reqVO.getSource());
        }
        if (reqVO.getLevel() != null) {
            wrapper.eq(CustomerDO::getLevel, reqVO.getLevel());
        }
        // 入池时间倒序（原型的「入池时间」列）
        wrapper.orderByDesc(CustomerDO::getEnterPoolTime);

        Page<CustomerDO> page = customerMapper.selectPage(reqVO, wrapper);
        List<CustomerDO> records = page.getRecords();
        List<CustomerRespVO> list = records.stream()
                .map(customerConvert::toRespVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id) {
        LoginUser loginUser = LoginUserHolder.getRequired();
        poolClaimLimiter.checkBeforeClaim(2, 2, loginUser.getUserId());
        CustomerDO customerDO = customerMapper.selectById(id);
        if (customerDO == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
        if (customerDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_OWNED);
        }
        // 原子 CAS：owner 为空的才更新为当前用户，防止两人同时抢到
        CustomerDO update = new CustomerDO();
        update.setOwnerUserId(loginUser.getUserId());
        update.setDeptId(loginUser.getDeptId());
        int affected = customerMapper.update(update,
                Wrappers.<CustomerDO>lambdaUpdate()
                        .eq(CustomerDO::getCustomerId, id)
                        .isNull(CustomerDO::getOwnerUserId));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_OWNED);
        }
        recordLog(customerDO, 2, customerDO.getCustomerName(),
                null, loginUser.getUserId(), null, "领取公海客户");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, CustomerPoolAssignReqVO reqVO) {
        if (reqVO == null || reqVO.getOwnerUserId() == null) {
            throw new ServiceException(ErrorCode.BAD_REQUEST);
        }
        CustomerDO customerDO = customerMapper.selectById(id);
        if (customerDO == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
        if (customerDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_OWNED);
        }
        // 部门跟随【目标负责人】，不能取操作人的部门（操作人只是管理员，不是归属人）
        SysUserMiniDO target = sysUserMiniMapper.selectById(reqVO.getOwnerUserId());
        if (target == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_TARGET_USER_NOT_EXISTS);
        }
        CustomerDO update = new CustomerDO();
        update.setOwnerUserId(reqVO.getOwnerUserId());
        update.setDeptId(target.getDeptId());
        int affected = customerMapper.update(update,
                Wrappers.<CustomerDO>lambdaUpdate()
                        .eq(CustomerDO::getCustomerId, id)
                        .isNull(CustomerDO::getOwnerUserId));
        if (affected != 1) {
            throw new ServiceException(ErrorCode.CUSTOMER_ALREADY_OWNED);
        }
        // 公海分配动作码是 1（分配），5 是「已有负责人的管理员改派」
        recordLog(customerDO, 1, customerDO.getCustomerName(),
                null, reqVO.getOwnerUserId(), null, reqVO.getReasonRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerDO customerDO = customerMapper.selectById(id);
        if (customerDO == null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_EXISTS);
        }
        if (customerDO.getOwnerUserId() != null) {
            throw new ServiceException(ErrorCode.CUSTOMER_NOT_IN_POOL);
        }
        customerMapper.deleteById(id);
    }

    /** 写入归属流转记录 */
    private void recordLog(CustomerDO customer, Integer action, String bizName,
                           Long fromOwner, Long toOwner, Integer reasonCode, String reasonRemark) {
        OwnerChangeLogDO logDO = new OwnerChangeLogDO();
        logDO.setBizType(2);
        logDO.setBizId(customer.getCustomerId());
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
