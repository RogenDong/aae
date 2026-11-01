package io.dev.aae;

/**
 * 传给 {@link GlyphBehavior#apply} 的执行上下文。
 *
 * <p>上下文由模拟器在回调期间提供：{@link #board()} 拿到的可变棋盘视图只在回调内有效，
 * 半周期的符文阶段结束后失效。上下文对象本身可以在回调里读取任意次，但不保证跨回调复用，
 * 实现不要把它保存下来。</p>
 */
public interface GlyphContext {

    /** 当前周期号；两个半周期看到的周期号相同。 */
    long cycle();

    /** 当前半周期。 */
    GlyphPhase phase();

    /** 当前符文声明。 */
    GlyphSpec spec();

    /** 当前符文声明对应的类型定义。 */
    GlyphDefinition<?> definition();

    /** 当前回调窗口内可用的可变棋盘视图。 */
    GlyphBoard board();

    /** 当前定义的占位索引对应的世界格点。 */
    GridPoint worldPoint(int placeholder);

    /** 占位索引对应世界格点在指定世界方向上的邻居。 */
    GridPoint neighbor(int placeholder, Direction direction);

    /** 把世界坐标换算回占位局部坐标。 */
    GridPoint toLocal(GridPoint worldPoint);
}
