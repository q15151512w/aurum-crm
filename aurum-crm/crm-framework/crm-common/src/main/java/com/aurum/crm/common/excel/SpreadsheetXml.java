package com.aurum.crm.common.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.aurum.crm.common.exception.ErrorCode;
import com.aurum.crm.common.exception.ServiceException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 把行集合写成 Excel 兼容的 SpreadsheetML（.xml）字节。
 *
 * <p>与 {@link ExcelKit} 的区别：本类产出的是 Excel 2003 XML 电子表格纯文本格式
 * （根元素 {@code urn:schemas-microsoft-com:office:spreadsheet} 命名空间 + 顶部
 * {@code ?mso-application?} 处理指令），Excel 能直接打开；而 {@link ExcelKit} 产出
 * 二进制 {@code .xlsx}（OOXML zip 包）。
 *
 * <p><b>行 VO 契约与 {@link ExcelKit} 完全一致</b>：字段上的 {@code @ExcelProperty("中文表头")}
 * 决定列名，字段声明顺序即列序，且**不做 {@code index} 排序**（现有 VO 均无该属性）。
 *
 * <p>字段值做类型感知的字符串化：{@code null}→空串、{@code String}→原值、
 * {@code BigDecimal}→{@code toPlainString()}（避免科学计数法）、
 * {@code LocalDate}/{@code LocalDateTime}→{@code toString()}；其余 {@code String.valueOf}。
 */
public final class SpreadsheetXml {

    private static final String SS_NS = "urn:schemas-microsoft-com:office:spreadsheet";

    private SpreadsheetXml() {
    }

    /**
     * 把一组行对象写成单 sheet 的 SpreadsheetML {@code .xml} 字节。
     *
     * @param rows      行数据（可为空列表，仍写出表头行）
     * @param headClass 行对象 class，字段上的 {@code @ExcelProperty} 决定表头
     * @param sheetName sheet 名
     * @return SpreadsheetML 字节；调用方负责上传 / 落盘
     */
    public static <T> byte[] write(List<T> rows, Class<T> headClass, String sheetName) {
        try {
            String[][] headers = resolveHeaders(headClass);
            StringBuilder xml = new StringBuilder(1024);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
            xml.append("<Workbook xmlns=\"").append(SS_NS).append("\" ")
                    .append("xmlns:ss=\"").append(SS_NS).append("\">\n");
            xml.append(" <Worksheet ss:Name=\"").append(escape(sheetName)).append("\">\n");
            xml.append("  <Table>\n");

            xml.append("   <Row>\n");
            for (String header : headers[0]) {
                appendCell(xml, header, "String");
            }
            xml.append("   </Row>\n");

            for (T row : rows) {
                xml.append("   <Row>\n");
                for (int i = 0; i < headers[0].length; i++) {
                    Object value = readField(headers[1][i], row);
                    String text = stringify(value);
                    // 数字列用 Number 类型，便于 Excel 求和等操作；其余一律 String
                    String type = (value instanceof BigDecimal) ? "Number" : "String";
                    appendCell(xml, text, type);
                }
                xml.append("   </Row>\n");
            }

            xml.append("  </Table>\n");
            xml.append(" </Worksheet>\n");
            xml.append("</Workbook>\n");
            return xml.toString().getBytes(StandardCharsets.UTF_8);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "Excel(XML) 生成失败：" + e.getMessage());
        }
    }

    /**
     * 解析表头：返回 {@code [0]=中文表头数组, [1]=表头对应的字段名数组}（顺序一致）。
     */
    private static <T> String[][] resolveHeaders(Class<T> headClass) {
        Field[] fields = headClass.getDeclaredFields();
        String[] titles = new String[fields.length];
        String[] names = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            ExcelProperty ep = field.getAnnotation(ExcelProperty.class);
            titles[i] = ep != null && ep.value().length > 0 ? ep.value()[0] : field.getName();
            names[i] = field.getName();
        }
        return new String[][]{titles, names};
    }

    private static Object readField(String fieldName, Object row) throws Exception {
        Field field = row.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(row);
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof BigDecimal b) {
            return b.toPlainString();
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private static void appendCell(StringBuilder xml, String text, String type) {
        xml.append("    <Cell><Data ss:Type=\"").append(type).append("\">")
                .append(escape(text)).append("</Data></Cell>\n");
    }

    /** XML 文本转义：& < > " ' */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
