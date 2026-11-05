package com.aurum.crm.module.crm.ai.provider;

import java.util.Iterator;

/**
 * 可关闭的迭代器 —— 流式响应的载体。
 *
 * <p>为什么不直接用 {@code Stream}：流式响应持有上游的 HTTP 连接，
 * 「读完了要关」和「读一半中断了也要关」是两个独立诉求，
 * 用迭代器 + {@code try-with-resources} 能把释放点写在调用处，一眼可见。
 *
 * <p>为什么要显式声明 {@link AutoCloseable}：{@code Iterator} 本身没有 {@code close()}，
 * 光靠 {@code AutoCloseable} 的默认实现会遮住流真正的关闭逻辑。
 */
public interface CloseableIterator<T> extends Iterator<T>, AutoCloseable {

    /** 释放底层资源（HTTP 连接 / 缓冲）。重复调用必须是安全的。 */
    @Override
    void close();
}
