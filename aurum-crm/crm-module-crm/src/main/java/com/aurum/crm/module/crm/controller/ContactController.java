package com.aurum.crm.module.crm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aurum.crm.common.core.CommonResult;
import com.aurum.crm.module.crm.mapper.ContactMapper;
import com.aurum.crm.module.crm.model.ContactDO;
import com.aurum.crm.module.crm.model.vo.ContactRespVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/crm/contact")
@Validated
public class ContactController {
    @Resource private ContactMapper mapper;

    @SaCheckPermission(value = {"crm:customer:list", "crm:contract:create", "crm:contract:update"}, mode = SaMode.OR)
    @GetMapping
    public CommonResult<List<ContactRespVO>> list(@RequestParam @NotNull Long customerId) {
        List<ContactDO> contacts = mapper.selectList(Wrappers.<ContactDO>lambdaQuery()
                .eq(ContactDO::getCustomerId, customerId)
                .orderByDesc(ContactDO::getIsPrimary)
                .orderByAsc(ContactDO::getContactId));
        return CommonResult.success(contacts.stream().map(this::toVO).toList());
    }

    private ContactRespVO toVO(ContactDO contact) {
        ContactRespVO vo = new ContactRespVO();
        vo.setId(contact.getContactId());
        vo.setCustomerId(contact.getCustomerId());
        vo.setName(contact.getContactName());
        vo.setTitle(contact.getPosition());
        vo.setPhone(contact.getPhone());
        vo.setTelephone(contact.getTelephone());
        vo.setEmail(contact.getEmail());
        vo.setWechat(contact.getWechat());
        vo.setGender(switch (contact.getGender() == null ? 0 : contact.getGender()) {
            case 1 -> "男"; case 2 -> "女"; default -> "未知";
        });
        vo.setPrimary(Integer.valueOf(1).equals(contact.getIsPrimary()));
        vo.setRemark(contact.getRemark());
        return vo;
    }
}
