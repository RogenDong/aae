package io.dev.aae;

/**
 * 符文运行期非法操作时抛出的异常。
 *
 * <p>典型的触发条件是文档列出的几类保护：在符文阶段之外使用 {@link GlyphBoard}（包括
 * 只读查询）等。传入 {@code null}、超出 {@code 0..0xFFFF} 的原子类型或作为长期键使用
 * {@code 0/0xFF} 等参数错误由相应的空值或参数校验异常报告。
 * 此外，机构或实体运动落到符文延迟产物的格点（尚未落盘的预留格点，或本半周期刚落盘的产物）
 * 时也抛出这个异常——它不是符文回调发出的，而是延迟产物的一致性错误，因此同样回滚整个周期
 * 而不是走警告停机。</p>
 *
 * <p>故意继承 {@link RuntimeException}：{@link OrthogonalSimulator#step()} 不吞异常、不包装
 * 异常，抛出这个异常时会把<strong>同一个异常对象</strong>原样送到调用方，同时整个周期已经
 * 回滚、模拟器进入 {@code halted}。回调抛出的业务异常（同样是运行时异常）走完全相同的
 * 回滚流程并被原样重抛。</p>
 */
public class GlyphExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GlyphExecutionException(String message) {
        super(message);
    }

    public GlyphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
