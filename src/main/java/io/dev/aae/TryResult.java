package io.dev.aae;

/**
 * 符文棋盘写操作的结果：{@code APPLIED} 表示条件满足并已提交，{@code SKIPPED} 表示条件
 * 不满足、本次调用被静默跳过（棋盘没有任何变化）。
 *
 * <p>{@code setType}、{@code spawn}、{@code queueSpawn}、{@code remove}、{@code bond}、
 * {@code unbond} 都是“先检查、再执行”的：不满足前置条件时按 {@code SKIPPED} 返回，而不是
 * 抛异常。只有用法错误（视图已失效、参数为 {@code null}、键字节 {@code 0/0xFF}、
 * {@code queueSpawn} 的产物尚未落盘）才抛 {@link GlyphExecutionException}。</p>
 */
public enum TryResult {

    /** 条件满足，操作已执行。 */
    APPLIED,

    /** 条件不满足，操作被跳过，棋盘保持原样。 */
    SKIPPED;

    /** 本次调用是否真的改变了棋盘。 */
    public boolean applied() {
        return this == APPLIED;
    }

    /** 本次调用是否被跳过。 */
    public boolean skipped() {
        return this == SKIPPED;
    }
}
