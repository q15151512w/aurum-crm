package com.aurum.crm.module.crm.model;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 计量单位精简投影，只读 {@code sys_unit}。
 *
 * <p><b>为什么放在 crm 模块里</b>：产品列表要显示「计量单位名称」，
 * 而 {@code /system/unit} 的 CRUD 属于 system 模块、尚未实现，
 * 且 product 模块不应该反向依赖 system 模块。
 * 这与仓库已有的 {@link SysUserMiniDO}（客户列表补负责人姓名时只读 {@code sys_user}）
 * 是同一个处理方式：**只读投影 + 只做展示补名，绝不写 sys_* 表**。
 *
 * <p><b>为什么继承 {@link BaseDO}</b>：{@code sys_unit} 有 {@code del_flag}，
 * 继承后自动获得 {@code @TableLogic} 过滤，避免把已删除的单位名展示出来。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_unit")
public class SysUnitMiniDO extends BaseDO implements Serializable {

    /** 单位ID */
    @TableId
    private Long unitId;

    /** 单位名称（个 / 台 / 套 / 年） */
    private String unitName;

    /** 单位编码 */
    private String unitCode;

    /** 数量小数位 */
    private Integer decimalPlaces;
}
