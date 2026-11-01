package io.dev.aae;

import java.util.Optional;
import java.util.Set;

/**
 * 符文回调对棋盘的只读视图。
 *
 * <p>只暴露原子数值与分子/抓取查询；内部的 {@link AtomGrid}、实体索引和持有集合都不对外
 * 暴露，因此自定义符文无法破坏 {@code entityId} 稳定性。</p>
 *
 * <p>所有 {@code placeholder} 参数都是当前符文定义占位列表的 0-based 索引，
 * 按当前实例朝向解析成世界格点；越界抛出 {@link IndexOutOfBoundsException}。
 * 方向参数使用世界方向，键查询只报告邻居也在当前占位内的方向。</p>
 */
public interface GlyphReader {

    /** 指定格点的原子；没有原子时返回 {@link Optional#empty()}。 */
    Optional<Atom> atomAt(int placeholder);

    /**
     * 该格点当前是否已占用。
     *
     * <p>{@link GlyphBoard#spawn}/{@link GlyphBoard#queueSpawn} 登记的延迟产物从登记那一刻起
     * 就算占用，因此后续符文看不到也放不进东西；在当前整数周期的剩余半周期内，这个格点
     * 会阻挡机构运动与输入模板。{@link GlyphBoard} 的实现额外要求回调窗口仍然有效。</p>
     */
    boolean isOccupied(int placeholder);

    /** 指定格点的稳定实体标识；没有原子时返回 {@code 0L}。 */
    long entityAt(int placeholder);

    /** 包含指定格点的分子；该格点没有原子时返回 {@link Molecule#empty()}。 */
    Molecule moleculeAt(int placeholder);

    /** 指定格点上原子的抓取计数（被几个机构持有）。 */
    int grabCountAt(int placeholder);

    /**
     * 邻里查询：指定格点方向上是否存在长期键。
     *
     * <p>“刚解除键的临时键”不算长期键，但可以用 {@link GlyphBoard#wasJustUnbonded} 单独询问。</p>
     */
    boolean isBonded(int placeholder, Direction direction);

    /** 该格点原子指向当前占位内邻居的长期键方向集合，按 {@link Direction#values()} 顺序。 */
    Set<Direction> bondedDirections(int placeholder);
}
