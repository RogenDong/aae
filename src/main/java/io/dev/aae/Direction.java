package io.dev.aae;

import java.util.Objects;

/**
 * 四个基本方位，按顺时针顺序排列。
 *
 * <p>坐标约定为正 x 轴指向东、正 y 轴指向南，因此 NORTH 的 y 增量为 -1，
 * SOUTH 的 y 增量为 +1。</p>
 *
 * <p>本类同时是“局部坐标 ↔ 世界坐标”换算的唯一入口：局部形状按朝东状态定义，
 * 此时正 x 向东、正 y 向南；旋转后正 x 沿本方位，正 y 沿本方位的右侧。
 * {@link GridPoint#translate(Direction)} 负责带方位的基本平移，
 * {@link #localOffsetToWorld(GridPoint)} 与 {@link #worldOffsetToLocal(GridPoint)}
 * 负责局部坐标系下的偏移换算。</p>
 */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private static final Direction[] VALUES = values();

    /** 偏移换算的零锚点：偏移版方法等价于把锚点放在原点。 */
    private static final GridPoint ORIGIN = new GridPoint(0, 0);

    private final long deltaX;
    private final long deltaY;

    Direction(long deltaX, long deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public long deltaX() {
        return deltaX;
    }

    /** 返回面向该方位时的 y 增量；正 y 指向南。 */
    public long deltaY() {
        return deltaY;
    }

    public Direction turnLeft() {
        return VALUES[(ordinal() + 3) & 3];
    }

    public Direction turnRight() {
        return VALUES[(ordinal() + 1) & 3];
    }

    public Direction opposite() {
        return VALUES[(ordinal() + 2) & 3];
    }

    /**
     * 把局部偏移换算成相对锚点的世界偏移。
     *
     * <p>朝东时保持局部偏移不变，朝南、朝西、朝北分别顺时针旋转 90°、180°、270°。
     * 这是本项目唯一的“局部 → 世界”换算实现，
     * {@link #localToWorld(GridPoint, GridPoint)} 与 {@link GlyphSpec#worldPoint(GridPoint)}
     * 都委托到 {@link #combine(GridPoint, long, long)}，不要再在别处重写这套 switch。</p>
     *
     * @param localOffset 占位局部偏移，{@code (0,0)} 是锚点
     * @return 相对锚点的世界偏移
     */
    public GridPoint localOffsetToWorld(GridPoint localOffset) {
        Objects.requireNonNull(localOffset, "localOffset");
        return combine(ORIGIN, localOffset.x(), localOffset.y());
    }

    /**
     * 把相对锚点的世界偏移换算回局部偏移；{@link #localOffsetToWorld(GridPoint)} 的逆变换。
     *
     * <p>朝南与朝北互为逆旋转，朝东与朝西各自为自身的逆。
     * 选择逆旋转后复用正向映射表。</p>
     *
     * @param worldOffset 相对锚点的世界偏移
     * @return 占位局部偏移
     */
    public GridPoint worldOffsetToLocal(GridPoint worldOffset) {
        Objects.requireNonNull(worldOffset, "worldOffset");
        Direction inverseRotation = switch (this) {
            case EAST, WEST -> this;
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
        };
        return inverseRotation.localOffsetToWorld(worldOffset);
    }

    /**
     * 将朝东状态下定义的局部偏移按本方位旋转，再加到世界锚点上。
     *
     * <p>锚点与偏移在同一个算式里加减，而不先算出一个取反后的偏移再相加。以
     * {@code WEST} 为例，{@code origin.x} 与 {@code localOffset.x} 都取
     * {@link Long#MIN_VALUE} 时结果应为 0：这里的 {@code origin.x - localOffset.x} 成立，
     * 而先对偏移取反会溢出。它与 {@link GridPoint#translate(Direction, long)} 保持同一约定。</p>
     */
    public GridPoint localToWorld(GridPoint origin, GridPoint localOffset) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(localOffset, "localOffset");
        return combine(origin, localOffset.x(), localOffset.y());
    }

    /**
     * 唯一的映射表：把局部 {@code (localX, localY)} 按本方位加到锚点 {@code origin} 上。
     *
     * <p>四个方位的局部基都是带符号的坐标置换，所以每个世界轴都只是取局部某一轴并可能取反；
     * 取反与相加在同一个 {@code Math.*Exact} 调用里完成，避免中间结果溢出。</p>
     */
    private GridPoint combine(GridPoint origin, long localX, long localY) {
        return switch (this) {
            // 世界偏移 = (x_local, y_local)
            case EAST -> new GridPoint(
                    Math.addExact(origin.x(), localX),
                    Math.addExact(origin.y(), localY));
            // 世界偏移 = (-y_local, x_local)
            case SOUTH -> new GridPoint(
                    Math.subtractExact(origin.x(), localY),
                    Math.addExact(origin.y(), localX));
            // 世界偏移 = (-x_local, -y_local)
            case WEST -> new GridPoint(
                    Math.subtractExact(origin.x(), localX),
                    Math.subtractExact(origin.y(), localY));
            // 世界偏移 = (y_local, -x_local)
            case NORTH -> new GridPoint(
                    Math.addExact(origin.x(), localY),
                    Math.subtractExact(origin.y(), localX));
        };
    }

    static Direction fromOrdinal(int ordinal) {
        return VALUES[ordinal & 3];
    }
}
