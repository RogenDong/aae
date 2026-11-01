package io.dev.aae;

/** 已占用网格位置的闭区间边界。 */
public record GridBounds(long minX, long minY, long maxX, long maxY) {
    public GridBounds {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("minimum bounds must not exceed maximum bounds");
        }
    }

    public long width() {
        return Math.addExact(Math.subtractExact(maxX, minX), 1);
    }

    public long height() {
        return Math.addExact(Math.subtractExact(maxY, minY), 1);
    }
}
