package io.dev.aae;

/**
 * 化学键连通判据：给定某个方向上的键类型字节，判断它是否算连通。
 *
 * <p>用它而不是 {@code Predicate<Byte>}，是因为分子搜索与 {@link AtomGrid#isBonded}
 * 会在每个格点的每个方向上调用判据，装箱会直接进入热路径。</p>
 */
@FunctionalInterface
public interface BondFilter {

    /** 该方向的键类型字节是否算连通。 */
    boolean test(byte bondType);
}
