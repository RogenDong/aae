package io.dev.aae;

import java.util.Objects;

/**
 * {@link GlyphContext} 的模拟器内部实现。
 *
 * <p>模拟器每个半周期复用同一个上下文对象，逐个符文用 {@link #bind} 绑定当前实例和状态。
 * 上下文本身不含可变业务状态，所有可变棋盘数据都在 {@link GlyphBoardView} 里。</p>
 */
final class GlyphContextImpl implements GlyphContext {

    private long cycle;
    private GlyphPhase phase;
    private GlyphInstance<?> instance;
    private GlyphBoard board;

    GlyphContextImpl(long cycle, GlyphPhase phase) {
        this.cycle = cycle;
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    /** 切换半周期；模拟器在每个半周期执行符文之前调用。 */
    void bindToPhase(GlyphPhase phase) {
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    /** 绑定本半周期的棋盘视图；模拟器在符文轮次开始前调用。 */
    void bind(GlyphBoard board) {
        this.board = Objects.requireNonNull(board, "board");
    }

    /** 同步周期号；模拟器每次 {@code step()} 之前调用。 */
    void bindToCycle(long cycle) {
        this.cycle = cycle;
    }

    void bind(GlyphInstance<?> instance) {
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    @Override
    public long cycle() {
        return cycle;
    }

    @Override
    public GlyphPhase phase() {
        return phase;
    }

    @Override
    public GlyphSpec spec() {
        return requireInstance().spec();
    }

    @Override
    public GlyphDefinition<?> definition() {
        return requireInstance().definition();
    }

    @Override
    public GlyphBoard board() {
        GlyphBoard current = board;
        if (current == null) {
            throw new IllegalStateException("glyph context is not bound to a board view");
        }
        return current;
    }

    @Override
    public GridPoint worldPoint(int placeholder) {
        GlyphInstance<?> current = requireInstance();
        return current.worldPoint(current.definition().placeholderAt(placeholder));
    }

    @Override
    public GridPoint neighbor(int placeholder, Direction direction) {
        return worldPoint(placeholder).translate(Objects.requireNonNull(direction, "direction"));
    }

    @Override
    public GridPoint toLocal(GridPoint worldPoint) {
        Objects.requireNonNull(worldPoint, "worldPoint");
        GlyphSpec spec = requireInstance().spec();
        GridPoint delta = new GridPoint(
                Math.subtractExact(worldPoint.x(), spec.position().x()),
                Math.subtractExact(worldPoint.y(), spec.position().y()));
        return GlyphSpec.toLocal(spec.direction(), delta);
    }

    private GlyphInstance<?> requireInstance() {
        GlyphInstance<?> current = instance;
        if (current == null) {
            throw new IllegalStateException("glyph context is not bound to an instance");
        }
        return current;
    }
}
