package com.aurum.crm.module.crm.service.impl;

import com.aurum.crm.common.excel.ExcelKit;
import com.aurum.crm.common.excel.SpreadsheetXml;
import com.aurum.crm.module.system.service.OssStorageService;

import java.time.LocalDateTime;

/**
 * 导出编排的公共碎片：把「模块内到处一样」的 Excel 生成 + OSS 上传收在一处，
 * 各 Service 只负责查数、映射成行 VO。
 *
 * <p>对象统一落在 {@code crm/export/{yyyy}/{MM}/{uuid}.xlsx}；内容类型固定为 xlsx。
 * 返回公网下载 URL，由 Controller 直接透出给前端。
 */
final class ExportSupport {

    /** 导出文件在 OSS 上的业务目录（对象会落到 crm/export/...） */
    static final String BIZ_TYPE = "export";

    /** 导出对象的 Content-Type（xlsx） */
    static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 sheet 名 */
    static final String SHEET_CRM = "数据";

    /** SpreadsheetML 的 Content-Type（Excel 2003 XML 电子表格的标准 MIME） */
    static final String CONTENT_TYPE_XML = "application/vnd.ms-excel";

    private ExportSupport() {
    }

    /** 行 → Excel 字节 → OSS 上传 → 返回 URL */
    static <T> String toUrl(OssStorageService ossStorageService,
                            java.util.List<T> rows, Class<T> headClass) {
        byte[] bytes = ExcelKit.write(rows, headClass, SHEET_CRM);
        return upload(ossStorageService, bytes);
    }

    /** 行 → SpreadsheetML(XML) 字节 → OSS 上传 → 返回 URL */
    static <T> String toXmlUrl(OssStorageService ossStorageService,
                               java.util.List<T> rows, Class<T> headClass) {
        byte[] bytes = SpreadsheetXml.write(rows, headClass, SHEET_CRM);
        String objectKey = ossStorageService.upload(bytes, BIZ_TYPE, CONTENT_TYPE_XML, "xml");
        return ossStorageService.buildUrl(objectKey);
    }

    /** 已生成的 Excel 字节 → OSS 上传 → 返回 URL */
    static String upload(OssStorageService ossStorageService, byte[] bytes) {
        String objectKey = ossStorageService.upload(bytes, BIZ_TYPE, CONTENT_TYPE, "xlsx");
        return ossStorageService.buildUrl(objectKey);
    }

    /** {@link LocalDateTime} → 字符串，null 安全 */
    static String timeStr(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
