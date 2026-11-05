package com.aurum.crm.module.system.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 登录日志导出行（对应 {@code GET /system/login-log/export} 与 {@code /export-xml}）。
 *
 * <p>{@code @ExcelProperty} 的表头即导出文件的中文列名，顺序即字段声明顺序，
 * 与前端 {@code views/system/log/login.vue} 的显示列一致（登陆人 / 工号 / 手机 / 登录时间 /
 * IP / 浏览器 / 操作系统 / 登陆时长）。
 *
 * <p>⚠️ 刻意用 {@code realName} / {@code loginAccount} 而不是 {@code userName} / {@code username}：
 * {@code LoginLogRespVO} 里那两个「只差一个字母大小写」的字段会触发 Lombok 的
 * 大小写不敏感判重，只生成一半访问器（详见 {@code LoginLogRespVO#username} 的注释）。
 * 这里的表头是「工号（登陆账号）」，字段名取 {@code loginAccount} 既避开陷阱又更达意。
 */
@Data
public class LoginLogExportRowVO {

    @ExcelProperty("登陆人")
    @ColumnWidth(14)
    private String realName;

    @ExcelProperty("工号（登陆账号）")
    @ColumnWidth(18)
    private String loginAccount;

    @ExcelProperty("手机号码")
    @ColumnWidth(16)
    private String phone;

    @ExcelProperty("登陆时间")
    @ColumnWidth(20)
    private String loginTime;

    @ExcelProperty("IP地址")
    @ColumnWidth(16)
    private String ip;

    @ExcelProperty("浏览器")
    @ColumnWidth(14)
    private String browser;

    @ExcelProperty("操作系统")
    @ColumnWidth(14)
    private String os;

    @ExcelProperty("登陆时长（小时）")
    @ColumnWidth(16)
    private String duration;
}
