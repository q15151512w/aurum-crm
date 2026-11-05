package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据 DO，对应表 {@code sys_dict_data}（《02-建表文档》）。
 *
 * <p>与 {@link DictTypeDO} 是主从关系：类型表存「有哪些字典」，本表存「每个字典有哪些项」。
 * 两表的 {@code dict_type} 是字符串关联（不是外键）。
 *
 * <p><b>两个与 DictTypeDO 不同的地方，都是照着 DDL 来的，不要「统一」掉</b>：
 * <ol>
 *     <li><b>本表没有 {@code is_active} 生成列</b>（{@code sys_dict_type} 有）。
 *         所以这里**不能**声明 {@code isActive} 字段 ——
 *         写了会报 {@code Unknown column 'is_active' in 'field list'}。</li>
 *     <li><b>本表没有唯一键</b>，只有普通索引 {@code idx_dict_type}。
 *         也就是说数据库层面**允许**同一类型下出现两个相同 {@code dict_value} ——
 *         这个不变量只能靠 {@code DictServiceImpl} 在业务层拦（见 {@code DICT_DATA_VALUE_EXISTS}）。</li>
 * </ol>
 *
 * <p>{@code @TableId} 不要加 {@code type} 属性：全局配置已是 {@code ASSIGN_ID}（雪花）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class DictDataDO extends BaseDO {

    /** 字典编码 */
    @TableId
    private Long dictCode;

    /** 字典排序（同级内升序） */
    private Integer dictSort;

    /** 字典标签（界面上显示的中文） */
    private String dictLabel;

    /** 字典键值（业务表里存的码值，字符串形式） */
    private String dictValue;

    /** 字典类型编码，对应 {@code sys_dict_type.dict_type} */
    private String dictType;

    /** 样式属性（前端标签的 CSS class，目前未使用） */
    private String cssClass;

    /** 表格回显样式（el-tag 的 type，如 success / danger） */
    private String listClass;

    /** 是否默认：0 否 / 1 是（表单里下拉的默认选中项） */
    private Integer isDefault;

    /** 状态：0 停用 / 1 正常。停用的项不出现在业务下拉里 */
    private Integer status;

    /** 备注 */
    private String remark;
}
