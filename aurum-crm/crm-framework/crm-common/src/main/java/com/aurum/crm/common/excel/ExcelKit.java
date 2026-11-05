package com.aurum.crm.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * EasyExcel 写字节的极简封装 —— 只负责「一行表头 + 若干行数据」写进内存字节，
 * 供各业务模块的导出 Service 复用，避免每处都重复 {@code EasyExcel.write(...)} 的样板。
 *
 * <p>不碰 OSS、不碰数据库：导出编排（查询 → 映射 → 上传 → 返回 URL）由各模块 Service 自己拼，
 * 本类只管把 {@code List<RowVO>} 变成 {@code byte[]}。
 *
 * <p>行 VO 用 {@code @ExcelProperty("表头中文")} 标注列，表头顺序即字段声明顺序
 * （{@code EasyExcel.DEFAULT_ORDER} 默认按注解发现顺序）。
 */
public final class ExcelKit {

    private ExcelKit() {
    }

    /**
     * 把一组行对象写成单 sheet 的 {@code .xlsx} 字节。
     *
     * @param rows      行数据（可为空列表，会写出只有表头的文件）
     * @param headClass 行对象 class，字段上的 {@code @ExcelProperty} 决定表头
     * @param sheetName sheet 名（表头标题栏）
     * @return xlsx 字节；调用方负责上传 / 落盘
     */
    public static <T> byte[] write(List<T> rows, Class<T> headClass, String sheetName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (ExcelWriter writer = EasyExcel.write(out, headClass).build()) {
                WriteSheet sheet = EasyExcel.writerSheet(sheetName).build();
                writer.write(rows, sheet);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "Excel 生成失败：" + e.getMessage());
        }
    }
}
