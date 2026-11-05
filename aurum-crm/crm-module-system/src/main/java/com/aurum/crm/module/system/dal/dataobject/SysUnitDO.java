package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计量单位 DO，对应表 {@code sys_unit}（《02-建表文档》4 节，对应原型「基础设置 → 计量单位」）。
 *
 * <p><b>本类是 {@code sys_unit} 的唯一写入口</b>。{@code crm-module-crm} 里的
 * {@code SysUnitMiniDO} 只是只读投影（产品列表补「单位名称」用），
 * 它自己也标注了「不写 sys_unit，单位维护属于 system 模块」。
 *
 * <p><b>两个容易写错的地方</b>（与 {@link DictTypeDO} 同款坑）：
 * <ol>
 *     <li>{@code @TableId} <b>不要</b>加 {@code type} 属性 —— 全局配置已是 {@code ASSIGN_ID}（雪花），
 *         加 {@code type = IdType.INPUT} 会被覆盖成「手动赋值」，插入时报 id 为空</li>
 *     <li><b>不要</b>声明 {@code isActive} 字段 —— {@code is_active} 是数据库生成列
 *         （{@code IF(del_flag = 0, 1, NULL)}，用于让「逻辑删除后编码可复用」），只读，
 *         写进去会报 {@code The value specified for generated column ... is not allowed}</li>
 * </ol>
 *
 * <p><b>没有 {@code remark} 字段</b>：{@code sys_unit} 表里没有这一列。
 * 接口层会接受并<b>静默忽略</b>前端传来的 {@code remark}（详见《14-计量单位模块接口文档》1.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_unit")
public class SysUnitDO extends BaseDO {

    /** 单位ID */
    @TableId
    private Long unitId;

    /** 单位名称（个 / 台 / 套 / 年 / 次），全局唯一（应用级校验） */
    private String unitName;

    /** 单位编码（PCS / SET / KIT …），唯一索引 uk_unit_code(unit_code, is_active) */
    private String unitCode;

    /** 数量小数位（0–4），声明「这个单位允许几位小数」 */
    private Integer decimalPlaces;

    /** 排序（列表固定按 sort ASC, unit_id ASC） */
    private Integer sort;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;
}
