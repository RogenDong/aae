package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackSpecTest {

    @Test
    void factoriesCreateTypedImmutableTracks() {
        GridPoint first = point(0, 0);
        GridPoint second = point(1, 0);
        List<GridPoint> source = new ArrayList<>(List.of(first, second));

        TrackSpec direct = new TrackSpec(TrackType.ORTHOGONAL, source);
        TrackSpec diagonal = TrackSpec.diagonal(first, point(1, 1));
        source.set(0, point(9, 9));

        assertEquals(TrackType.ORTHOGONAL, direct.type());
        assertEquals(List.of(first, second), direct.points());
        assertEquals(TrackType.DIAGONAL, diagonal.type());
        assertThrows(UnsupportedOperationException.class, () -> direct.points().add(point(2, 0)));
    }

    @Test
    void trackMustContainOnlyUniqueNonNullPoints() {
        GridPoint origin = point(0, 0);

        assertThrows(IllegalArgumentException.class, TrackSpec::orthogonal);
        assertThrows(IllegalArgumentException.class, TrackSpec::diagonal);
        assertThrows(NullPointerException.class, () -> new TrackSpec(null, List.of(origin)));
        assertThrows(NullPointerException.class, () -> new TrackSpec(TrackType.ORTHOGONAL, null));
        assertThrows(
                NullPointerException.class,
                () -> new TrackSpec(
                        TrackType.ORTHOGONAL,
                        Arrays.asList(origin, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrackSpec.orthogonal(origin, origin));
    }

    @Test
    void orthogonalTrackAllowsOnlyCardinalUnitSteps() {
        TrackSpec track = TrackSpec.orthogonal(
                point(0, 0),
                point(1, 0),
                point(1, -1),
                point(2, -1));

        assertEquals(4, track.points().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> TrackSpec.orthogonal(point(0, 0), point(1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrackSpec.orthogonal(point(0, 0), point(2, 0)));
    }

    @Test
    void diagonalTrackAllowsOnlyDiagonalUnitSteps() {
        TrackSpec track = TrackSpec.diagonal(
                point(0, 0),
                point(1, 1),
                point(2, 0),
                point(3, 1));

        assertEquals(4, track.points().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> TrackSpec.diagonal(point(0, 0), point(1, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrackSpec.diagonal(point(0, 0), point(2, 2)));
    }

    @Test
    void loopRequiresAtLeastThreePointsAndMatchingClosingStep() {
        assertFalse(TrackSpec.orthogonal(point(0, 0)).isLoop());
        assertFalse(TrackSpec.orthogonal(point(0, 0), point(1, 0)).isLoop());
        assertFalse(
                TrackSpec.orthogonal(
                        point(0, 0),
                        point(1, 0),
                        point(2, 0)).isLoop());
        assertTrue(
                TrackSpec.orthogonal(
                        point(0, 0),
                        point(1, 0),
                        point(1, 1),
                        point(0, 1)).isLoop());
        assertTrue(
                TrackSpec.diagonal(
                        point(0, 0),
                        point(1, 1),
                        point(0, 2),
                        point(-1, 1)).isLoop());
    }

    @Test
    void networkFollowsPlusAndMinusThroughTurnsAndStopsAtEnds() {
        GridPoint first = point(0, 0);
        GridPoint turn = point(1, 0);
        GridPoint last = point(1, 1);
        TrackNetwork network = TrackNetwork.from(
                List.of(TrackSpec.orthogonal(first, turn, last)));

        assertEquals(turn, network.destination(first, TrackDirection.PLUS));
        assertEquals(last, network.destination(turn, TrackDirection.PLUS));
        assertEquals(turn, network.destination(last, TrackDirection.MINUS));
        assertEquals(first, network.destination(turn, TrackDirection.MINUS));
        assertEquals(first, network.destination(first, TrackDirection.MINUS));
        assertEquals(last, network.destination(last, TrackDirection.PLUS));
        assertEquals(point(20, 20), network.destination(point(20, 20), TrackDirection.PLUS));
    }

    @Test
    void networkConnectsBothDirectionsAcrossLoopBoundary() {
        GridPoint first = point(0, 0);
        GridPoint second = point(1, 1);
        GridPoint third = point(0, 2);
        GridPoint last = point(-1, 1);
        TrackNetwork network = TrackNetwork.from(
                List.of(TrackSpec.diagonal(first, second, third, last)));

        assertEquals(first, network.destination(last, TrackDirection.PLUS));
        assertEquals(last, network.destination(first, TrackDirection.MINUS));
    }

    @Test
    void networkAllowsLegalEndpointConnectionsAndRejectsInvalidTracks() {
        GridPoint shared = point(1, 0);
        TrackSpec first = TrackSpec.orthogonal(point(0, 0), shared);
        TrackSpec second = TrackSpec.diagonal(shared, point(2, 1));

        TrackNetwork network = TrackNetwork.from(List.of(first, second));
        assertEquals(point(2, 1), network.destination(shared, TrackDirection.PLUS));
        assertEquals(point(0, 0), network.destination(shared, TrackDirection.MINUS));
        assertThrows(NullPointerException.class, () -> TrackNetwork.from(null));
        assertThrows(
                NullPointerException.class,
                () -> TrackNetwork.from(Arrays.asList(first, null)));
        // 撞车 head to head
        assertThrows(
                InvalidTrackNetworkException.class,
                () -> TrackNetwork.from(List.of(
                        first,
                        TrackSpec.orthogonal(point(2, 0), shared))));
    }

    @Test
    void destinationRequiresOriginAndDirection() {
        TrackNetwork network = TrackNetwork.from(List.of());

        assertThrows(
                NullPointerException.class,
                () -> network.destination(null, TrackDirection.PLUS));
        assertThrows(
                NullPointerException.class,
                () -> network.destination(point(0, 0), null));
    }

    @Test
    void sameHeadOverlapIsRejected() {
        GridPoint shared = new GridPoint(1, 0);
        InvalidTrackNetworkException failure = assertThrows(
                InvalidTrackNetworkException.class,
                () -> TrackNetwork.from(List.of(
                        TrackSpec.orthogonal(shared, new GridPoint(0, 0)),
                        TrackSpec.diagonal(shared, new GridPoint(2, 1)))));
        assertEquals(InvalidTrackNetworkException.Reason.NON_ENDPOINT_OVERLAP, failure.reason());
    }

    @Test
    void noMoreThanTwoTracksMayMeetAtOnePoint() {
        GridPoint shared = new GridPoint(1, 0);
        InvalidTrackNetworkException failure = assertThrows(
                InvalidTrackNetworkException.class,
                () -> TrackNetwork.from(List.of(
                        TrackSpec.orthogonal(new GridPoint(0, 0), shared),
                        TrackSpec.diagonal(shared, new GridPoint(2, 1)),
                        TrackSpec.orthogonal(shared, new GridPoint(1, 1)))));
        assertEquals(InvalidTrackNetworkException.Reason.MORE_THAN_TWO_TRACKS_OVERLAP,
                failure.reason());
    }

    @Test
    void trackNetworkStillHasNoOverlapForAClosedTrack() {
        GridPoint shared = new GridPoint(0, 0);
        assertThrows(
                InvalidTrackNetworkException.class,
                () -> TrackNetwork.from(List.of(
                        TrackSpec.orthogonal(shared, new GridPoint(1, 0), new GridPoint(1, 1),
                                new GridPoint(0, 1)),
                        TrackSpec.orthogonal(shared, new GridPoint(-1, 0)))));
    }

    private static GridPoint point(long x, long y) {
        return new GridPoint(x, y);
    }
}
