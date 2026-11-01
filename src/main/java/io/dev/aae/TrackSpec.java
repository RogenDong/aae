package io.dev.aae;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 一条由同类单位步连接而成的不可变有序轨道。 */
public record TrackSpec(TrackType type, List<GridPoint> points) {

    public TrackSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(points, "points");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("track must contain at least one point");
        }

        Set<GridPoint> uniquePoints = new HashSet<>();
        GridPoint previous = null;
        for (GridPoint point : points) {
            Objects.requireNonNull(point, "points must not contain null");
            if (!uniquePoints.add(point)) {
                throw new IllegalArgumentException("duplicate track point: " + point);
            }
            if (previous != null && !type.isUnitStep(previous, point)) {
                throw new IllegalArgumentException(
                        "track segment is not a " + type + " unit step: "
                                + previous + " -> " + point);
            }
            previous = point;
        }
    }

    /** 创建只含正交单位步的轨道。 */
    public static TrackSpec orthogonal(GridPoint... points) {
        Objects.requireNonNull(points, "points");
        return new TrackSpec(TrackType.ORTHOGONAL, List.of(points));
    }

    /** 创建只含对角单位步的轨道。 */
    public static TrackSpec diagonal(GridPoint... points) {
        Objects.requireNonNull(points, "points");
        return new TrackSpec(TrackType.DIAGONAL, List.of(points));
    }

    /** 首尾还能以同类型单位步相连时，该轨道形成闭环。 */
    public boolean isLoop() {
        return points.size() > 2
                && type.isUnitStep(points.get(points.size() - 1), points.get(0));
    }
}
