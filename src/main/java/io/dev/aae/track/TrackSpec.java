package io.dev.aae.track;

import io.dev.aae.GridPoint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 由正交或对角单位步连接的不可变有序轨道；闭环必须显式声明。 */
public record TrackSpec(List<GridPoint> points, boolean isLoop) {

    public TrackSpec(List<GridPoint> points) {
        this(points, false);
    }

    public TrackSpec(GridPoint... points) {
        this(List.of(points), false);
    }

    public TrackSpec(boolean isLoop, GridPoint... points) {
        this(List.of(points), isLoop);
    }

    public TrackSpec {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("track must contain at least one point");
        }

        var first = points.get(0);
        var last = points.get(points.size() - 1);

        if (isLoop && points.size() > 2 && first.equals(last)) {
            points = List.copyOf(points.subList(0, points.size() - 1));
            last = points.get(points.size() - 1);
        } else points = List.copyOf(points);

        Set<GridPoint> uniquePoints = new HashSet<>();
        GridPoint previous = null;
        for (GridPoint point : points) {
            if (!uniquePoints.add(point)) {
                throw new IllegalArgumentException("duplicate track point: " + point);
            }
            if (previous != null && noUnitStep(previous, point)) {
                throw new IllegalArgumentException("track segment is not a unit step: " + previous + " -> " + point);
            }
            previous = point;
        }
        if (isLoop && (points.size() < 3 || noUnitStep(last, first))) {
            throw new IllegalArgumentException("loop requires at least three distinct points and a unit closing step");
        }
    }

    public static boolean noUnitStep(GridPoint from, GridPoint to) {
        long dx, dy;
        try {
            dx = Math.subtractExact(to.x(), from.x());
            dy = Math.subtractExact(to.y(), from.y());
        } catch (ArithmeticException ignored) {
            return true;
        }
        return dx < -1 || dx > 1 || dy < -1 || dy > 1 || (dx == 0 && dy == 0);
    }
}
