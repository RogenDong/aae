package io.dev.aae;

import java.util.Optional;
import java.util.Set;

/**
 * 符文回调经 {@link GlyphContext#board()} 拿到的可变棋盘视图。
 *
 * <p>视图的有效期是<b>整个半周期的符文阶段</b>：模拟器在每个半周期开始时新建一个视图，
 * 让计划里声明的全部符文依次使用同一个视图，符文阶段结束后才让它失效。因此后一个符文能
 * 看到前一个符文已经提交的变更；<b>回调窗口之外任何方法——包括只读查询——都抛出
 * {@link GlyphExecutionException}</b>。需要把棋盘状态带出回调的符文应当复制值。</p>
 *
 * <p>所有操作都维护 {@code entityId}、抓取计数、持有关系、互惠键和周期事务，自定义符文
 * 无法绕过这些约束。写操作统一遵循“先检查条件、再执行”的约定：条件不满足时返回
 * {@link TryResult#SKIPPED}，不抛异常。符文级的前置条件（输入格点是否符合预期、输出格点
 * 是否空闲）由 {@link GlyphDefinition#inputs()} / {@link GlyphDefinition#outputs()} 声明，
 * 模拟器在执行行为之前统一检查；任一项不满足就整条符文跳过。</p>
 *
 * <p>定位参数只能是当前定义的 placeholder 索引，没有任意坐标访问入口。
 * 每个符文执行前视图重新绑定当前实例。方向键操作的邻居必须也属于当前 placeholder，
 * 否则读取返回 false 或不报告方向，写入返回 {@link TryResult#SKIPPED}。</p>
 */
public interface GlyphBoard extends GlyphReader {

    /**
     * 指定格点的原子；没有原子时返回空。
     *
     * <p>与 {@link GlyphReader#atomAt(int)} 的区别是本方法受回调窗口约束：视图失效后
     * 抛出 {@link GlyphExecutionException}。</p>
     */
    @Override
    Optional<Atom> atomAt(int placeholder) throws GlyphExecutionException;

    /**
     * 该格点是否已占用；受回调窗口约束。
     */
    @Override
    boolean isOccupied(int placeholder) throws GlyphExecutionException;

    /**
     * 指定格点的稳定实体标识；受回调窗口约束。
     */
    @Override
    long entityAt(int placeholder) throws GlyphExecutionException;

    /**
     * 包含指定格点的分子；受回调窗口约束。
     */
    @Override
    Molecule moleculeAt(int placeholder) throws GlyphExecutionException;

    /**
     * 指定格点上原子的抓取计数；受回调窗口约束。
     */
    @Override
    int grabCountAt(int placeholder) throws GlyphExecutionException;

    /**
     * 邻里查询：指定格点方向上是否存在长期键；受回调窗口约束。
     */
    @Override
    boolean isBonded(int placeholder, Direction direction) throws GlyphExecutionException;

    /**
     * 该格点原子全部有键方向的集合，按 {@link Direction#values()} 顺序。
     */
    @Override
    Set<Direction> bondedDirections(int placeholder) throws GlyphExecutionException;

    /**
     * 原地替换原子类型。
     *
     * <p>保留 {@code entityId}、全部键位和抓取状态。</p>
     *
     * @param type 新的原子类型，范围 {@code 0..0xFFFF}，包含 {@code 0}
     * @return {@link TryResult#APPLIED} 表示已替换；目标格点没有原子时返回
     *         {@link TryResult#SKIPPED}
     * @throws IllegalArgumentException 类型超出 {@code 0..0xFFFF}
     * @throws GlyphExecutionException  视图已失效
     */
    TryResult setType(int placeholder, int type) throws GlyphExecutionException;

    /**
     * 生成一个新原子。
     *
     * <p>目标格点空闲时立即落盘并分配新的 {@code entityId}；目标格点被占用时<b>不覆盖占用格</b>，
     * 而是把产物登记为延迟生成——它立刻占用该格点（同一半周期内后续符文看到的
     * {@code isOccupied} 即为真），并延迟到当前整数周期第二半周期结束。延迟队列
     * 中的格点会阻挡机构运动（同格冲突走警告停机路径）并阻塞与之相交的输入模板。</p>
     *
     * @return {@link TryResult#APPLIED} 表示已落盘；目标格点被占用时返回
     *         {@link TryResult#SKIPPED}，产物进入延迟队列
     * @param atom 要生成的原子，类型 {@code 0} 也是有效类型
     * @throws GlyphExecutionException  视图已失效
     */
    TryResult spawn(int placeholder, Atom atom) throws GlyphExecutionException;

    /**
     * 延迟生成：立即占用目标格点，产物稍后落盘。
     *
     * <p>这是 {@link #spawn} 的“总是延迟”形式：产物只在当前整数周期的后续半周期尝试落盘；
     * 第二半周期结束仍未落盘就被丢弃，不跨整数周期等待。</p>
     *
     * <p>预留的格点在同一个半周期内对后续符文表现为已占用；机构运动落到尚未落盘的预留格点
     * 时抛出 {@link GlyphExecutionException} 并回滚整个周期，输入模板与预留格点相交时静默
     * 跳过整份模板。</p>
     *
     * @return 登记成功返回 {@link TryResult#APPLIED}
     * @param atom 要生成的原子，类型 {@code 0} 也是有效类型
     * @throws GlyphExecutionException  视图已失效
     */
    TryResult queueSpawn(int placeholder, Atom atom) throws GlyphExecutionException;

    /**
     * 删除原子。
     *
     * <p>被抓取、仍有任意键或刚解除键的原子一律拒绝。允许删除时会清理邻居的反向键。</p>
     *
     * @return {@link TryResult#APPLIED} 表示已删除；目标格点没有原子、或原子不可安全删除时
     *         返回 {@link TryResult#SKIPPED}
     * @throws GlyphExecutionException 视图已失效
     */
    TryResult remove(int placeholder) throws GlyphExecutionException;

    /**
     * 建立互惠键。
     *
     * <p>面对冲突时：任一侧该方向已有同键或异键（长期键）时静默跳过、什么都不做；只有
     * 单侧残留的键会被直接覆盖</p>
     *
     * @param bondCode 要写入的键类型字节；{@code 0}（无键）与 {@link Atom#TEMPORARY_BOND} 被拒绝
     * @return {@link TryResult#APPLIED} 表示写入了键；任一端没有原子、或该方向已被长期键
     *         占据时返回 {@link TryResult#SKIPPED}
     * @throws IllegalArgumentException 键字节是 {@code 0} 或 {@link Atom#TEMPORARY_BOND}
     * @throws GlyphExecutionException  视图已失效
     */
    TryResult bond(int placeholder, Direction direction, byte bondCode) throws GlyphExecutionException;

    /**
     * 拆除互惠键，并在两侧写入“刚解除键”的临时标记。
     *
     * <p>只有两侧都存在长期键时才拆除；单侧残留键不会被本方法清理。临时标记会阻止两侧原子
     * 在当前周期被消费或删除，由模拟器在下一个周期开始时清除。</p>
     *
     * @return {@link TryResult#APPLIED} 表示拆除了键；两侧不构成互惠长期键时返回
     *         {@link TryResult#SKIPPED}
     * @throws GlyphExecutionException 视图已失效
     */
    TryResult unbond(int placeholder, Direction direction) throws GlyphExecutionException;

    /**
     * 该格点方向上的键是否刚在本周期被拆除。
     *
     * <p>临时标记的有效期是整个周期：两个半周期都能观察到，下一个周期开始时被清除。</p>
     */
    boolean wasJustUnbonded(int placeholder, Direction direction) throws GlyphExecutionException;
}
