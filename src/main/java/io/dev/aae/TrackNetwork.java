package io.dev.aae;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 将轨道点列编译为按正负方向查询的目标表。 */
final class TrackNetwork {

    private final Map<GridPoint, GridPoint> plusDestinations;
    private final Map<GridPoint, GridPoint> minusDestinations;

    private TrackNetwork(
            Map<GridPoint, GridPoint> plusDestinations,
            Map<GridPoint, GridPoint> minusDestinations) {
        this.plusDestinations = Map.copyOf(plusDestinations);
        this.minusDestinations = Map.copyOf(minusDestinations);
    }

    // TODO 重做轨道衔接
    static TrackNetwork from(List<TrackSpec> tracks) {
        Objects.requireNonNull(tracks, "tracks");

        Map<GridPoint, GridPoint> plus = new HashMap<>();
        Map<GridPoint, GridPoint> minus = new HashMap<>();
        Map<GridPoint, List<TrackEntry>> memberships = new LinkedHashMap<>();
        for (int ti = 0; ti < tracks.size(); ti++) {
            TrackSpec track = Objects.requireNonNull(tracks.get(ti), "tracks must not contain null");
            List<GridPoint> points = track.points();

            // 抛弃空轨道、单点轨道
            if (points.size() < 2) continue;

            // points.first is tail (-)
            // points.last is head (+)
            for (int i = 1; i < points.size(); i++) {
                GridPoint previous = points.get(i - 1);
                GridPoint current = points.get(i);
                plus.put(previous, current);
                minus.put(current, previous);
            }
            if (track.isLoop()) {
                GridPoint tail = points.get(0);
                GridPoint head = points.get(points.size() - 1);
                plus.put(head, tail);
                minus.put(tail, head);
            }
            TrackEntry te = new TrackEntry(ti, track);
            for (GridPoint point : points) {
                memberships.computeIfAbsent(point, p -> new ArrayList<>()).add(te);
            }
        }

        // 只要重叠合法，plus/minus 表便能正确导航，无需主动寻找和衔接头尾节点
        validateOverlaps(memberships);
        // TODO 检查衔接轨道闭环

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

    private static void validateOverlaps(Map<GridPoint, List<TrackEntry>> memberships) {
        for (Map.Entry<GridPoint, List<TrackEntry>> entry : memberships.entrySet()) {
            List<TrackEntry> owners = entry.getValue();
            if (owners.size() < 2) continue;

            GridPoint point = entry.getKey();
            if (owners.size() > 2) {
                throw new InvalidTrackOverlapException(
                        InvalidTrackNetworkException.Reason.MORE_THAN_TWO_TRACKS_OVERLAP,
                        "more than two tracks overlap at " + point);
            }

            var first = owners.get(0);
            var second = owners.get(1);
            int i = -1;

            // TODO 闭环检查延迟到最后
            if (first.track.isLoop()) {
                i = first.index();
            } else if (second.track.isLoop()) {
                i = second.index();
            }
            if (i >= 0) {
                throw new InvalidTrackOverlapException(InvalidTrackNetworkException.Reason.LOOP_TRACK_OVERLAP,
                        "loop track[" + i + "] overlaps another track at " + point);
            }

            assertEndpoint(point, first);
            assertEndpoint(point, second);

            var ft = first.track.points();
            var st = second.track.points();
            boolean firstTail = point.equals(ft.get(0));
            boolean secondTail = point.equals(st.get(0));
            boolean firstHead = point.equals(ft.get(ft.size() - 1));
            boolean secondHead = point.equals(st.get(st.size() - 1));
            if (firstTail == secondTail || firstHead == secondHead) {
                throw new InvalidTrackOverlapException(InvalidTrackNetworkException.Reason.NON_ENDPOINT_OVERLAP,
                        "tracks must meet head-to-tail at " + point);
            }
        }
    }

    private static void assertEndpoint(GridPoint point, TrackEntry track) throws InvalidTrackNetworkException {
        List<GridPoint> points = track.track().points();
        if (point.equals(points.get(0)) || point.equals(points.get(points.size() - 1)))
            return;

        throw new InvalidTrackOverlapException(InvalidTrackNetworkException.Reason.NON_ENDPOINT_OVERLAP,
                "tracks overlap at non-endpoint " + point + " on track[" + track.index() + "]");
    }

    private record TrackEntry(int index, TrackSpec track) {
    }
}
