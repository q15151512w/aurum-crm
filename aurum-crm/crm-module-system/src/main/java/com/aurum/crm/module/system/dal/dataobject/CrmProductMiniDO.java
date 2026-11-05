package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品只读投影，<b>只读</b> {@code crm_product}，用于「计量单位是否被引用」的计数。
 *
 * <p><b>为什么放在 system 模块里</b>：删除计量单位前必须确认没有产品在用它
 * （否则产品列表「单位」列会变成 {@code --}）。而依赖方向是
 * {@code crm-module-crm} → {@code crm-module-system}，反向依赖不存在，
 * 所以这里照 {@code crm-module-crm} 已有的做法<b>只读投影别的模块的表</b>
 * —— 与 {@code SysUnitMiniDO} / {@code SysUserMiniDO} 是同一个模式，只是方向相反：
 * <b>只读、不写、不引实体、不加载业务对象</b>。
 *
 * <p><b>为什么继承 {@link BaseDO}</b>：{@code crm_product} 有 {@code del_flag}，
 * 继承后自动获得 {@code @TableLogic}（查询自动追加 {@code del_flag = 0}），
 * 避免把已删除产品算成「还在引用」而拒绝删除。
 *
 * <p>只声明计数需要的两列 —— 投影越窄，跟随 crm 表结构变化的风险越小。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_product")
public class CrmProductMiniDO extends BaseDO {

    /** 产品ID */
    @TableId
    private Long productId;

    /** 计量单位ID */
    private Long unitId;
}
