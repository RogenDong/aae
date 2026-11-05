package io.dev.aae.glyph;

import io.dev.aae.OrthogonalSimulator;

/**
 * 一个符文类型的行为。
 *
 * <p>接口本身无状态：同一份定义会被多个计划、多个模拟器并发复用，因此任何可变数据都必须
 * 放在 {@code previousState} / 返回值这一对不可变状态值里。返回的下一阶段状态会在下一个
 * 半周期作为 {@code previousState} 传回来；状态值按不可变值使用，实现不要原地修改传入的
 * 对象。</p>
 *
 * <p>回调可以抛出任意运行时业务异常，也可以抛出 {@link GlyphExecutionException}。两种
 * 异常都会让整个周期回滚、模拟器进入停机状态，并把同一个异常对象原样抛给
 * {@link OrthogonalSimulator#step()} 的调用方。</p>
 *
 * @param <S> 实例状态类型；无状态实现用 {@link Void}
 */
@FunctionalInterface
public interface GlyphBehavior<S> {

    /**
     * 执行一个半周期。
     *
     * @param context       本半周期的执行上下文
     * @param previousState 上一个半周期返回的状态；首个半周期为工厂创建的初始状态
     * @return 传给下一个半周期的状态
     */
    S apply(GlyphContext context, S previousState);
}
