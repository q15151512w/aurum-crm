package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型 DO，对应表 {@code sys_dict_type}。
 *
 * <p>本类是骨架的第一个实体，用于验证持久层基础设施：
 * <ul>
 *     <li>{@code ASSIGN_ID} 雪花主键生成</li>
 *     <li>{@code BaseDO} 审计字段自动填充</li>
 *     <li>{@code @TableLogic} 逻辑删除</li>
 *     <li>{@code @MapperScan("com.aurum.crm.**.dal.mysql")} 路径约定</li>
 * </ul>
 *
 * <p><b>两个容易写错的地方</b>：
 * <ol>
 *     <li>{@code @TableId} <b>不要</b>加 {@code type} 属性 ——
 *         全局配置已设为 {@code ASSIGN_ID}，加 {@code type = IdType.INPUT} 会覆盖成「手动赋值」</li>
 *     <li><b>不要</b>声明 {@code isActive} 字段 ——
 *         {@code is_active} 是数据库生成列（用于逻辑删除下的唯一性），只读，
 *         写进去会报 {@code The value specified for generated column ... is not allowed}</li>
 * </ol>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictTypeDO extends BaseDO {

    /** 字典主键 */
    @TableId
    private Long dictId;

    /** 字典名称 */
    private String dictName;

    /** 字典类型编码（业务唯一键） */
    private String dictType;

    /** 状态：0 停用，1 正常 */
    private Integer status;

    /** 备注 */
    private String remark;
}
