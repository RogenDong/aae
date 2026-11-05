package io.dev.aae;

import io.dev.aae.track.InvalidTrackOverlapException;
import io.dev.aae.track.TrackDirection;
import io.dev.aae.track.TrackSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 将节点互不共享的轨道编译为按正负方向查询的目标表。 */
final class TrackNetwork {

    private final Map<GridPoint, GridPoint> plusDestinations;
    private final Map<GridPoint, GridPoint> minusDestinations;

    private TrackNetwork(Map<GridPoint, GridPoint> plus, Map<GridPoint, GridPoint> minus) {
        this.plusDestinations = Map.copyOf(plus);
        this.minusDestinations = Map.copyOf(minus);
    }

    static TrackNetwork from(List<TrackSpec> tracks) {
        Objects.requireNonNull(tracks, "tracks");
        Map<GridPoint, Integer> owners = new HashMap<>();
        for (int index = 0; index < tracks.size(); index++) {
            TrackSpec track = Objects.requireNonNull(tracks.get(index), "tracks must not contain null");
            for (GridPoint point : track.points()) {
                Integer previous = owners.putIfAbsent(point, index);
                if (previous != null) {
                    throw new InvalidTrackOverlapException(
                            "track[" + index + "] overlaps track[" + previous + "] at " + point);
                }
            }
        }

        Map<GridPoint, GridPoint> plus = new HashMap<>();
        Map<GridPoint, GridPoint> minus = new HashMap<>();
        for (TrackSpec track : tracks) {
            List<GridPoint> points = track.points();
            for (int i = 1; i < points.size(); i++) {
                plus.put(points.get(i - 1), points.get(i));
                minus.put(points.get(i), points.get(i - 1));
            }
            if (track.isLoop()) {
                GridPoint first = points.get(0);
                GridPoint last = points.get(points.size() - 1);
                plus.put(last, first);
                minus.put(first, last);
            }
        }
        return new TrackNetwork(plus, minus);
    }

    GridPoint destination(GridPoint origin, TrackDirection direction) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case PLUS -> plusDestinations.getOrDefault(origin, origin);
            case MINUS -> minusDestinations.getOrDefault(origin, origin);
        };
    }
}
