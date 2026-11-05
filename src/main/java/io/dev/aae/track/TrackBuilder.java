package io.dev.aae.track;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 以连续步进的方式构建 {@link TrackSpec} */
public final class TrackBuilder {
    private GridPoint head;
    private final List<GridPoint> points;

    private TrackBuilder(GridPoint origin) {
        this.head = Objects.requireNonNull(origin, "origin");
        this.points = new ArrayList<>();
        points.add(origin);
    }

    public static TrackBuilder start(long x, long y) {
        return new TrackBuilder(new GridPoint(x, y));
    }

    public static TrackBuilder start(GridPoint origin) {
        return new TrackBuilder(origin);
    }

    /** 显式闭合末点到起点的单位边，返回独立快照；非法闭环抛出 IllegalArgumentException。 */
    public TrackSpec loop() {
        return new TrackSpec(points, true);
    }

    /** 返回开放轨道快照，即使首尾相邻也不补闭合边。 */
    public TrackSpec noLoop() {
        return new TrackSpec(points);
    }

    public TrackBuilder east() {
        head = head.translate(Direction.EAST);
        points.add(head);
        return this;
    }

    public TrackBuilder south() {
        head = head.translate(Direction.SOUTH);
        points.add(head);
        return this;
    }

    public TrackBuilder west() {
        head = head.translate(Direction.WEST);
        points.add(head);
        return this;
    }

    public TrackBuilder north() {
        head = head.translate(Direction.NORTH);
        points.add(head);
        return this;
    }

    public TrackBuilder eastNorth() {
        head = head.translateDiagonal(Direction.NORTH);
        points.add(head);
        return this;
    }

    public TrackBuilder eastSouth() {
        head = head.translateDiagonal(Direction.EAST);
        points.add(head);
        return this;
    }

    public TrackBuilder westSouth() {
        head = head.translateDiagonal(Direction.SOUTH);
        points.add(head);
        return this;
    }

    public TrackBuilder westNorth() {
        head = head.translateDiagonal(Direction.WEST);
        points.add(head);
        return this;
    }
}
