package com.aurum.crm.mybatis.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务实体（DO）的基类，对应建表规范里的五个审计字段。
 *
 * <p>用法：业务 DO 继承本类即可自动获得审计能力。
 * <pre>{@code
 * @TableName("crm_customer")
 * public class CustomerDO extends BaseDO {
 *     @TableId
 *     private Long customerId;
 *     ...
 * }
 * }</pre>
 *
 * <p><b>三个设计要点</b>：
 * <ol>
 *     <li>{@code createTime}/{@code updateTime} 由 {@link DefaultDBFieldHandler} 自动填充，
 *         业务代码不要手动赋值。数据库层不声明 {@code ON UPDATE CURRENT_TIMESTAMP}
 *         （MySQL 在「值未变化时」不会更新，与业务预期不符）</li>
 *     <li>{@code delFlag} 标了 {@code @TableLogic}，MyBatis-Plus 会自动把
 *         {@code DELETE} 转成 {@code UPDATE ... SET del_flag = 1}，
 *         所有 {@code SELECT} 自动追加 {@code del_flag = 0}</li>
 *     <li><b>不要</b>把 {@code is_active} 加进来 —— 它是数据库生成的列，
 *         只读，由唯一索引用于解决「逻辑删除后编号仍被占用」的问题</li>
 * </ol>
 */
@Data
public abstract class BaseDO implements Serializable {

    /** 创建人 ID，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 ID，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 删除标志：0 存在，1 删除。@TableLogic 让 MyBatis-Plus 自动处理逻辑删除 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
