package com.aurum.crm.module.system.dal.dataobject;

import com.aurum.crm.mybatis.core.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户 DO，对应表 {@code sys_user}。
 *
 * <p><b>两个容易写错的地方</b>：
 * <ol>
 *     <li>{@code @TableId} <b>不要</b>加 {@code type} —— 全局配置是 {@code ASSIGN_ID}，
 *         加了 {@code type = IdType.INPUT} 会覆盖成「手动赋值」，插入时主键为 null</li>
 *     <li><b>不要</b>声明 {@code isActive} —— {@code is_active} 是生成列
 *         （{@code IF(del_flag = 0, 1, NULL)}，专为逻辑删除下的唯一索引服务），只读，
 *         写进去会报 {@code The value specified for generated column ... is not allowed}</li>
 * </ol>
 *
 * <p><b>{@code status} 的取值是 1 正常 / 2 禁用 / 3 离职</b>，
 * 注意别和 {@code sys_dept.status}（0 停用 / 1 正常）混了，两者语义不同。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUserDO extends BaseDO {

    /** 用户 ID */
    @TableId
    private Long userId;

    /** 登录账号（存活行内唯一，配合生成列 is_active 构成唯一索引） */
    private String username;

    /** 密码（BCrypt 哈希，不可逆） */
    private String password;

    /** 真实姓名（NOT NULL，注册时无此字段需后端补默认值） */
    private String realName;

    /** 昵称 */
    private String nickname;

    /** 所属部门 ID */
    private Long deptId;

    /** 直属上级用户 ID */
    private Long superiorId;

    /** 手机号（当前为明文存储，见 AuthServiceImpl 的说明） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像地址 */
    private String avatar;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 状态：1 正常，2 禁用，3 离职 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    /** 备注 */
    private String remark;
}
