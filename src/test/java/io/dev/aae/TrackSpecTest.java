package io.dev.aae;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackSpecTest {

    @Test
    void constructorsAndBuilderCreateImmutableMixedTracks() {
        GridPoint first = point(0, 0);
        GridPoint turn = point(1, 0);
        GridPoint last = point(2, 1);
        List<GridPoint> source = new ArrayList<>();
        source.add(first);
        source.add(turn);
        source.add(last);
        TrackSpec direct = new TrackSpec(source);
        TrackBuilder builder = TrackBuilder.start(first).east().eastSouth();
        TrackSpec built = builder.noLoop();
        source.set(0, point(9, 9));
        builder.south();

        assertEquals(List.of(first, turn, last), direct.points());
        assertEquals(direct, built);
        assertEquals(direct.hashCode(), built.hashCode());
        assertFalse(built.isLoop());
        assertEquals(4, builder.noLoop().points().size());
        assertThrows(UnsupportedOperationException.class, () -> direct.points().add(point(3, 1)));
        assertThrows(UnsupportedOperationException.class, () -> built.points().clear());
    }

    @Test
    void trackMustContainOnlyUniqueNonNullPoints() {
        GridPoint origin = point(0, 0);
        assertThrows(IllegalArgumentException.class, () -> new TrackSpec(List.of()));
        assertThrows(NullPointerException.class, () -> new TrackSpec((List<GridPoint>) null));
        assertThrows(NullPointerException.class, () -> new TrackSpec(origin, null));
        assertThrows(NullPointerException.class, () -> TrackBuilder.start(null));
        assertThrows(IllegalArgumentException.class, () -> new TrackSpec(origin, origin));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(origin, point(1, 0), origin));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(true, origin, point(1, 0), origin, point(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TrackBuilder.start(origin).east().west().noLoop());
    }

    @Test
    void mixedTrackAllowsOnlyEightNeighborUnitSteps() {
        TrackSpec track = TrackBuilder.start(0, 0).east().eastNorth().north().noLoop();
        assertEquals(4, track.points().size());
        for (GridPoint invalid : List.of(point(2, 0), point(2, 2), point(1, 2), point(0, 0),
                point(Long.MIN_VALUE, 0))) {
            assertThrows(IllegalArgumentException.class, () -> new TrackSpec(point(0, 0), invalid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(point(Long.MIN_VALUE, 0), point(Long.MAX_VALUE, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(point(0, Long.MIN_VALUE), point(0, Long.MAX_VALUE)));
        assertEquals(2, new TrackSpec(point(Long.MAX_VALUE - 1, Long.MIN_VALUE + 1),
                point(Long.MAX_VALUE, Long.MIN_VALUE)).points().size());
    }

    @Test
    void builderSupportsAllEightDirectionsAndRejectsCoordinateOverflow() {
        // 每次从同一起点沿一条指引走一步，八个终点分别对应八邻接格点。
        GridPoint origin = point(0, 0);
        List<TrackSpec> tracks = List.of(
                TrackBuilder.start(origin).north().noLoop(), TrackBuilder.start(origin).east().noLoop(),
                TrackBuilder.start(origin).south().noLoop(), TrackBuilder.start(origin).west().noLoop(),
                TrackBuilder.start(origin).eastNorth().noLoop(), TrackBuilder.start(origin).eastSouth().noLoop(),
                TrackBuilder.start(origin).westSouth().noLoop(), TrackBuilder.start(origin).westNorth().noLoop());
        List<GridPoint> ends = List.of(point(0, -1), point(1, 0), point(0, 1), point(-1, 0),
                point(1, -1), point(1, 1), point(-1, 1), point(-1, -1));
        for (int i = 0; i < tracks.size(); i++) {
            assertEquals(List.of(origin, ends.get(i)), tracks.get(i).points());
        }
        TrackBuilder builder = TrackBuilder.start(point(Long.MAX_VALUE, 0));
        assertThrows(ArithmeticException.class, builder::east);
        assertThrows(ArithmeticException.class, builder::eastNorth);
        assertEquals(List.of(point(Long.MAX_VALUE, 0)), builder.noLoop().points());
        assertThrows(ArithmeticException.class,
                () -> TrackBuilder.start(point(0, Long.MIN_VALUE)).westNorth());
    }

    @Test
    void loopRequiresExplicitDeclarationAndAtLeastThreeDistinctPoints() {
        GridPoint first = point(0, 0);
        var triangle = TrackBuilder.start(first).east().south();
        var openTriangle = triangle.noLoop();
        var loopTriangle = triangle.loop();
        assertFalse(new TrackSpec(first).isLoop());
        assertFalse(new TrackSpec(first, point(1, 0)).isLoop());
        assertFalse(openTriangle.isLoop());
        assertTrue(loopTriangle.isLoop());
        assertEquals(
                loopTriangle,
                new TrackSpec(true, first, point(1, 0), point(1, 1), first));
        assertEquals(loopTriangle, triangle.loop());
        assertEquals(loopTriangle, triangle.westNorth().loop());
        assertThrows(IllegalArgumentException.class, () -> new TrackSpec(true, first));
        assertThrows(IllegalArgumentException.class, () -> new TrackSpec(true, first, point(1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(List.of(first, point(1, 0), first), true));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(List.of(first, point(1, 0), point(2, 0)), true));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackSpec(List.of(first, point(1, 0), point(1, 1), first, first), true));
        assertThrows(IllegalArgumentException.class, () -> TrackBuilder.start(first).east().east().loop());
    }

    @Test
    void networkFollowsPlusAndMinusThroughMixedTurnsAndStopsAtEnds() {
        GridPoint first = point(0, 0);
        GridPoint turn = point(1, 0);
        GridPoint last = point(2, 1);
        TrackNetwork network = TrackNetwork.from(List.of(new TrackSpec(first, turn, last)));
        assertEquals(turn, network.destination(first, TrackDirection.PLUS));
        assertEquals(last, network.destination(turn, TrackDirection.PLUS));
        assertEquals(turn, network.destination(last, TrackDirection.MINUS));
        assertEquals(first, network.destination(turn, TrackDirection.MINUS));
        assertEquals(first, network.destination(first, TrackDirection.MINUS));
        assertEquals(last, network.destination(last, TrackDirection.PLUS));
        for (TrackDirection direction : TrackDirection.values()) {
            assertEquals(point(20, 20), network.destination(point(20, 20), direction));
        }
    }

    @Test
    void networkConnectsBothDirectionsOnlyAcrossDeclaredLoopBoundary() {
        GridPoint first = point(0, 0);
        GridPoint last = point(1, 1);
        List<GridPoint> points = List.of(first, point(1, 0), last);
        TrackNetwork closed = TrackNetwork.from(List.of(new TrackSpec(points, true)));
        TrackNetwork open = TrackNetwork.from(List.of(new TrackSpec(points)));
        assertEquals(first, closed.destination(last, TrackDirection.PLUS));
        assertEquals(last, closed.destination(first, TrackDirection.MINUS));
        assertEquals(last, open.destination(last, TrackDirection.PLUS));
        assertEquals(first, open.destination(first, TrackDirection.MINUS));
    }

    @Test
    void networkRejectsFormerEndpointConnectionsAndInvalidTracks() {
        GridPoint shared = point(1, 0);
        TrackSpec first = new TrackSpec(point(0, 0), shared);
        assertOverlap(List.of(first, new TrackSpec(shared, point(2, 1))), shared);
        assertOverlap(List.of(first, new TrackSpec(point(2, 0), shared)), shared);
        assertThrows(NullPointerException.class, () -> TrackNetwork.from(null));
        assertThrows(NullPointerException.class, () -> TrackNetwork.from(Arrays.asList(first, null)));
    }

    @Test
    void destinationRequiresOriginAndDirection() {
        TrackNetwork network = TrackNetwork.from(List.of());
        assertThrows(NullPointerException.class, () -> network.destination(null, TrackDirection.PLUS));
        assertThrows(NullPointerException.class, () -> network.destination(point(0, 0), null));
    }

    @Test
    void endpointAndInternalNodeOverlapAreRejected() {
        GridPoint shared = point(1, 0);
        assertOverlap(List.of(new TrackSpec(shared, point(0, 0)),
                new TrackSpec(shared, point(2, 1))), shared);
        assertOverlap(List.of(new TrackSpec(point(0, 0), shared, point(2, 0)),
                new TrackSpec(point(0, -1), shared)), shared);
    }

    @Test
    void singlePointTracksClaimNodesAndFirstOverlapWins() {
        GridPoint shared = point(1, 0);
        TrackSpec single = new TrackSpec(shared);
        assertOverlap(List.of(single, single), shared);
        assertOverlap(List.of(single, new TrackSpec(point(0, 0), shared)), shared);
        assertOverlap(List.of(new TrackSpec(point(0, 0), shared), single), shared);
        assertOverlap(List.of(single, new TrackSpec(shared, point(2, 1)),
                new TrackSpec(shared, point(1, 1))), shared);
        TrackNetwork network = TrackNetwork.from(List.of(single));
        assertEquals(shared, network.destination(shared, TrackDirection.PLUS));
        assertEquals(shared, network.destination(shared, TrackDirection.MINUS));
    }

    @Test
    void closedTrackNodesCannotBeShared() {
        GridPoint shared = point(1, 0);
        assertOverlap(List.of(new TrackSpec(true, point(0, 0), shared, point(1, 1)),
                new TrackSpec(shared, point(2, 0))), shared);
    }

    @Test
    void crossingSegmentsWithoutSharedNodesRemainIndependent() {
        // 两条对角指引在 (0.5,0.5) 相交，但四个节点各自独占，因此不会换轨。
        GridPoint a = point(0, 0), b = point(1, 1), c = point(0, 1), d = point(1, 0);
        TrackNetwork network = TrackNetwork.from(List.of(new TrackSpec(a, b), new TrackSpec(c, d)));
        assertEquals(b, network.destination(a, TrackDirection.PLUS));
        assertEquals(a, network.destination(b, TrackDirection.MINUS));
        assertEquals(d, network.destination(c, TrackDirection.PLUS));
        assertEquals(c, network.destination(d, TrackDirection.MINUS));
        assertEquals(b, network.destination(b, TrackDirection.PLUS));
        assertEquals(d, network.destination(d, TrackDirection.PLUS));
        assertEquals(a, network.destination(a, TrackDirection.MINUS));
        assertEquals(c, network.destination(c, TrackDirection.MINUS));
    }

    private static void assertOverlap(List<TrackSpec> tracks, GridPoint point) {
        var failure = assertThrows(InvalidTrackOverlapException.class, () -> TrackNetwork.from(tracks));
        assertEquals("track[1] overlaps track[0] at " + point, failure.getMessage());
    }

    private static GridPoint point(long x, long y) {
        return new GridPoint(x, y);
    }
}
