package com.aurum.crm.module.system.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 人员导出行（接口文档 3.1 {@code GET /system/user/export}）。
 *
 * <p>{@code @ExcelProperty} 的表头即导出 Excel 的中文列名，顺序即字段声明顺序。
 * 用于 {@code com.aurum.crm.common.excel.ExcelKit} 把行集合写成 xlsx。
 */
@Data
public class UserExportRowVO {

    @ExcelProperty("账号")
    @ColumnWidth(18)
    private String username;

    @ExcelProperty("姓名")
    @ColumnWidth(14)
    private String name;

    @ExcelProperty("部门")
    @ColumnWidth(22)
    private String deptName;

    @ExcelProperty("角色")
    @ColumnWidth(16)
    private String roleName;

    @ExcelProperty("手机")
    @ColumnWidth(16)
    private String phone;

    @ExcelProperty("邮箱")
    @ColumnWidth(24)
    private String email;

    @ExcelProperty("性别")
    @ColumnWidth(8)
    private String gender;

    @ExcelProperty("在职状态")
    @ColumnWidth(10)
    private String workStatus;

    @ExcelProperty("账号状态")
    @ColumnWidth(10)
    private String accountStatus;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;
}
