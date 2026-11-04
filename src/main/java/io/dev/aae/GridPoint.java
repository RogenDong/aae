package io.dev.aae;

import java.util.Objects;

/**
 * 无界正交网格上的不可变点。
 * 正 x 轴指向东，正 y 轴指向南（向北为负）。
 */
public record GridPoint(long x, long y) {

    public GridPoint translate(Direction direction) {
        return translate(direction, 1);
    }

    public GridPoint translateDiagonal(Direction direction) {
        return translateDiagonal(direction, 1);
    }

    /**
     * 沿指定方位平移指定距离。正 y 指向南，因此 NORTH 减小 y、SOUTH 增大 y。
     *
     * <p>这里按方位直接做加减而不是乘以 {@link Direction#deltaX()} /
     * {@link Direction#deltaY()}，是为了让 {@code translate(WEST, Long.MIN_VALUE)}
     * 这类极端偏移在最终坐标仍然落在 long 范围内时也能成功；
     * 各方位与 {@code Direction} 的增量必须保持一致，由单元测试固化。</p>
     */
    public GridPoint translate(Direction direction, long distance) {
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case NORTH -> new GridPoint(x, Math.subtractExact(y, distance));
            case EAST -> new GridPoint(Math.addExact(x, distance), y);
            case SOUTH -> new GridPoint(x, Math.addExact(y, distance));
            case WEST -> new GridPoint(Math.subtractExact(x, distance), y);
        };
    }

    /**
     * 沿对角线平移指定距离。Direction 表示旋转方位：
     * NORTH 为东北、EAST 为东南、SOUTH 为西南、WEST 为西北。
     *
     * <p>这里按方位直接做精确加减，是为了让 {@code translateDiagonal(WEST, Long.MIN_VALUE)}
     * 这类极端偏移在最终坐标仍然落在 long 范围内时也能成功；
     * 对角方位映射由单元测试固化。</p>
     */
    public GridPoint translateDiagonal(Direction direction, long distance) {
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case NORTH -> new GridPoint(Math.addExact(x, distance), Math.subtractExact(y, distance));
            case EAST -> new GridPoint(Math.addExact(x, distance), Math.addExact(y, distance));
            case SOUTH -> new GridPoint(Math.subtractExact(x, distance), Math.addExact(y, distance));
            case WEST -> new GridPoint(Math.subtractExact(x, distance), Math.subtractExact(y, distance));
        };
    }

    public GridPoint add(GridPoint offset) {
        Objects.requireNonNull(offset, "offset");
        return new GridPoint(
                Math.addExact(x, offset.x),
                Math.addExact(y, offset.y));
    }

    public long manhattanDistanceTo(GridPoint other) {
        Objects.requireNonNull(other, "other");
        long deltaX = absoluteDifference(x, other.x);
        long deltaY = absoluteDifference(y, other.y);
        return Math.addExact(deltaX, deltaY);
    }

    private static long absoluteDifference(long left, long right) {
        long difference = Math.subtractExact(left, right);
        if (difference == Long.MIN_VALUE) {
            throw new ArithmeticException("distance exceeds long range");
        }
        return Math.abs(difference);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
