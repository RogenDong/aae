package io.dev.aae;

import java.util.Objects;

/**
 * 模拟棋盘的坐标范围。
 *
 * <p>两种形态：{@link #unbounded()} 是无限棋盘，任何 {@link GridPoint} 都合法；
 * {@link #of(long, long)} 是宽 {@code width}、高 {@code height} 的有界棋盘，原点 {@code (0,0)}
 * 固定为西北角，棋盘只向正 x（东）与正 y（南）展开，合法格点是半开区间
 * {@code [0,width) × [0,height)}。负坐标、以及等于宽或高的坐标都在棋盘外。</p>
 *
 * <p>本类只描述坐标范围；由 {@link OrthogonalSimulator} 校验布局和运行时落点。</p>
 */
public final class BoardLimit {

    /** 无限棋盘：不做任何坐标限制。 */
    private static final BoardLimit UNBOUNDED = new BoardLimit(0, 0, false);

    private final long width;
    private final long height;
    private final boolean bounded;

    private BoardLimit(long width, long height, boolean bounded) {
        this.width = width;
        this.height = height;
        this.bounded = bounded;
    }

    /** 无限棋盘。 */
    public static BoardLimit unbounded() {
        return UNBOUNDED;
    }

    /**
     * 宽 {@code width}、高 {@code height} 的有界棋盘，原点在西北角。
     *
     * @throws IllegalArgumentException 宽或高不为正
     */
    public static BoardLimit of(long width, long height) {
        if (width < 1) {
            throw new IllegalArgumentException("board width must be positive: " + width);
        }
        if (height < 1) {
            throw new IllegalArgumentException("board height must be positive: " + height);
        }
        return new BoardLimit(width, height, true);
    }

    /** 是否限制了棋盘范围。 */
    public boolean isBounded() {
        return bounded;
    }

    /**
     * 棋盘宽度（列数）；无限棋盘返回 {@code 0}。
     *
     * <p>调用方应先看 {@link #isBounded()}。</p>
     */
    public long width() {
        return width;
    }

    /**
     * 棋盘高度（行数）；无限棋盘返回 {@code 0}。
     *
     * <p>调用方应先看 {@link #isBounded()}。</p>
     */
    public long height() {
        return height;
    }

    /** 该格点是否落在棋盘内；无限棋盘总是返回 {@code true}。 */
    public boolean contains(GridPoint position) {
        Objects.requireNonNull(position, "position");
        return contains(position.x(), position.y());
    }

    /** 该坐标是否落在棋盘内；无限棋盘总是返回 {@code true}。 */
    public boolean contains(long x, long y) {
        return !bounded || (x >= 0 && x < width && y >= 0 && y < height);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BoardLimit limit
                && bounded == limit.bounded
                && width == limit.width
                && height == limit.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bounded, width, height);
    }

    @Override
    public String toString() {
        return bounded
                ? "bounded board [0," + width + ")x[0," + height + ")"
                : "unbounded board";
    }
}