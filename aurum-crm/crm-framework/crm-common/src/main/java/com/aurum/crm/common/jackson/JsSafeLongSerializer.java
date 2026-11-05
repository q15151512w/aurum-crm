package com.aurum.crm.common.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 雪花ID 的"JS 精度安全"序列化器：**只有超出 JS 安全整数范围的数字才写成字符串**。
 *
 * <h3>为什么需要它</h3>
 * JSON 默认把 {@code Long} 写成数字，而 JavaScript 的 {@code Number} 是 IEEE-754 双精度浮点，
 * 安全整数上限只有 {@code 2^53 - 1 = 9007199254740991}（约 9e15）。
 * 本项目的雪花ID 是 19 位（约 2e18），**在这个量级上双精度能表示的相邻值间隔是 256**，
 * 所以 {@code 2099028143988920321} 到前端就变成了 {@code 2099028143988920300}。
 *
 * <p>后端拿这个被舍入的 ID 去查库自然查不到 —— 表现为「编辑/删除时提示 xx 不存在」。
 * 雪花ID 本身没有任何问题，问题只出在"用 JSON 数字承载 19 位整数"这一步。
 *
 * <h3>为什么不是所有 Long 都写成字符串</h3>
 * 若依（RuoYi）那类项目是"所有 Long 都转字符串"，简单一致，但会把
 * 分页 {@code total}、{@code fileSize}、{@code expiresIn} 这些小数字也变成字符串，
 * 前端分页组件、筛选条件都得跟着处理类型。这里只在**确实会丢精度**时才转字符串：
 * <ul>
 *     <li>{@code id = 2099028143988920321} → {@code "2099028143988920321"}（字符串，前端原样回传）</li>
 *     <li>{@code total = 6} → {@code 6}（仍是数字，前端无感）</li>
 * </ul>
 *
 * <h3>反序列化方向不用管</h3>
 * JSON 字符串 → {@code Long} 是 Jackson 的默认能力，所以前端把收到的字符串 ID 原样提交回来即可。
 *
 * <p>注册见 {@link JacksonConfig}；金额是 {@code BigDecimal}，不受影响。
 */
public class JsSafeLongSerializer extends JsonSerializer<Long> {

    /** JavaScript 的 Number.MAX_SAFE_INTEGER = 2^53 - 1 */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            // 超出 JS 安全整数范围 → 字符串，避免前端 Number 舍入后回传错误 ID
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value);
        }
    }
}
