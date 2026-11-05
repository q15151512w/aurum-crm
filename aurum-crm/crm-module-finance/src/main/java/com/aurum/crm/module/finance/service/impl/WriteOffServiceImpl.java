package com.aurum.crm.module.finance.service.impl;

import com.aurum.crm.common.core.PageResult;
import com.aurum.crm.module.finance.mapper.WriteOffMapper;
import com.aurum.crm.module.finance.model.dto.WriteOffPageReqVO;
import com.aurum.crm.module.finance.model.vo.WriteOffRespVO;
import com.aurum.crm.module.finance.service.WriteOffService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 核销记录服务实现。
 *
 * <p>「核销」的数据事实就是 {@code fin_invoice_payment} 明细（{@code write_off_amount}），
 * 由保存发票时落库。本服务只提供<b>平铺查询</b>，把明细 join 发票与回款返回，
 * 供管理端「核销记录」列表一次性查看，无需逐张发票点进详情。
 */
@Service
public class WriteOffServiceImpl implements WriteOffService {

    @Resource
    private WriteOffMapper writeOffMapper;

    @Override
    public PageResult<WriteOffRespVO> pageQuery(WriteOffPageReqVO reqVO) {
        Page<?> page = reqVO.toPage();
        IPage<WriteOffRespVO> result = writeOffMapper.selectWriteOffPage(page, reqVO);
        return new PageResult<>(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }
}
