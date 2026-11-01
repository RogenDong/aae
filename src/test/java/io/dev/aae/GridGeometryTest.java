package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GridGeometryTest {

    @Test
    void cardinalDirectionsUseCoordinatesWithSouthwardY() {
        GridPoint origin = new GridPoint(10, -4);

        assertEquals(new GridPoint(10, -5), origin.translate(Direction.NORTH));
        assertEquals(new GridPoint(11, -4), origin.translate(Direction.EAST));
        assertEquals(new GridPoint(10, -3), origin.translate(Direction.SOUTH));
        assertEquals(new GridPoint(9, -4), origin.translate(Direction.WEST));
    }

    @Test
    void translateAgreesWithDirectionDeltas() {
        GridPoint origin = new GridPoint(12, -7);

        for (Direction direction : Direction.values()) {
            assertEquals(
                    new GridPoint(
                            origin.x() + direction.deltaX(),
                            origin.y() + direction.deltaY()),
                    origin.translate(direction),
                    "translate 必须与 " + direction + " 的 deltaX/deltaY 一致");
        }
    }

    @Test
    void northDecreasesYAndSouthIncreasesY() {
        assertEquals(-1, Direction.NORTH.deltaY());
        assertEquals(1, Direction.SOUTH.deltaY());
        assertEquals(0, Direction.NORTH.deltaX());
        assertEquals(0, Direction.SOUTH.deltaX());

        GridPoint origin = new GridPoint(0, 0);
        assertEquals(new GridPoint(0, -3), origin.translate(Direction.NORTH, 3));
        assertEquals(new GridPoint(0, 3), origin.translate(Direction.SOUTH, 3));
    }

    @Test
    void fourQuarterTurnsRestoreTheDirection() {
        Direction direction = Direction.NORTH;
        for (int index = 0; index < 4; index++) {
            direction = direction.turnRight();
        }

        assertEquals(Direction.NORTH, direction);
        assertEquals(Direction.NORTH, Direction.NORTH.turnLeft().turnRight());
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
    }

    @Test
    void localCoordinatesRotateFromEastAroundTheAnchor() {
        GridPoint origin = new GridPoint(5, 7);
        GridPoint local = new GridPoint(2, 3);

        assertEquals(new GridPoint(7, 10), Direction.EAST.localToWorld(origin, local));
        assertEquals(new GridPoint(2, 9), Direction.SOUTH.localToWorld(origin, local));
        assertEquals(new GridPoint(3, 4), Direction.WEST.localToWorld(origin, local));
        assertEquals(new GridPoint(8, 5), Direction.NORTH.localToWorld(origin, local));

        assertEquals(new GridPoint(2, 3), Direction.EAST.localOffsetToWorld(local));
        assertEquals(new GridPoint(-3, 2), Direction.SOUTH.localOffsetToWorld(local));
        assertEquals(new GridPoint(-2, -3), Direction.WEST.localOffsetToWorld(local));
        assertEquals(new GridPoint(3, -2), Direction.NORTH.localOffsetToWorld(local));
    }

    @Test
    void localFrameFollowsTheFacingDirection() {
        GridPoint origin = new GridPoint(5, 7);

        for (Direction direction : Direction.values()) {
            Direction right = direction.turnRight();
            assertEquals(
                    new GridPoint(
                            origin.x() + direction.deltaX() * 4,
                            origin.y() + direction.deltaY() * 4),
                    direction.localToWorld(origin, new GridPoint(4, 0)),
                    "局部正 x 应沿 " + direction);
            assertEquals(
                    new GridPoint(
                            origin.x() + right.deltaX() * 4,
                            origin.y() + right.deltaY() * 4),
                    direction.localToWorld(origin, new GridPoint(0, 4)),
                    "局部正 y 应沿 " + direction + " 的右侧");
        }
    }

    @Test
    void worldOffsetsUseTheInverseRotation() {
        GridPoint world = new GridPoint(2, 3);

        assertEquals(new GridPoint(2, 3), Direction.EAST.worldOffsetToLocal(world));
        assertEquals(new GridPoint(3, -2), Direction.SOUTH.worldOffsetToLocal(world));
        assertEquals(new GridPoint(-2, -3), Direction.WEST.worldOffsetToLocal(world));
        assertEquals(new GridPoint(-3, 2), Direction.NORTH.worldOffsetToLocal(world));
    }

    @Test
    void offsetTransformsRoundTripInBothDirections() {
        for (Direction direction : Direction.values()) {
            for (long x : new long[] {-3, -1, 0, 1, 2}) {
                for (long y : new long[] {-3, -1, 0, 1, 2}) {
                    GridPoint point = new GridPoint(x, y);
                    assertEquals(point,
                            direction.worldOffsetToLocal(direction.localOffsetToWorld(point)),
                            direction + " local round trip: " + point);
                    assertEquals(point,
                            direction.localOffsetToWorld(direction.worldOffsetToLocal(point)),
                            direction + " world round trip: " + point);
                }
            }
        }
    }

    @Test
    void clockwiseQuarterTurnsRotateOffsetsAndRestoreTheOriginalPoint() {
        GridPoint original = new GridPoint(2, 3);
        GridPoint rotated = original;
        GridPoint[] expected = {
                new GridPoint(-3, 2), new GridPoint(-2, -3),
                new GridPoint(3, -2), original
        };
        Direction direction = Direction.EAST;

        for (GridPoint point : expected) {
            rotated = Direction.SOUTH.localOffsetToWorld(rotated);
            direction = direction.turnRight();
            assertEquals(point, rotated);
            assertEquals(point, direction.localOffsetToWorld(original));
        }
    }

    @Test
    void manhattanDistanceSupportsNegativeAndFarCoordinates() {
        assertEquals(
                14,
                new GridPoint(-5, 8).manhattanDistanceTo(new GridPoint(4, 3)));
        assertThrows(
                ArithmeticException.class,
                () -> new GridPoint(Long.MIN_VALUE, 0)
                        .manhattanDistanceTo(new GridPoint(Long.MAX_VALUE, 0)));
    }

    @Test
    void extremeSignedOffsetsSucceedWhenTheFinalCoordinateFits() {
        assertEquals(
                new GridPoint(0, 0),
                new GridPoint(Long.MIN_VALUE, 0)
                        .translate(Direction.WEST, Long.MIN_VALUE));
        assertEquals(
                new GridPoint(0, 0),
                Direction.WEST.localToWorld(
                        new GridPoint(Long.MIN_VALUE, Long.MIN_VALUE),
                        new GridPoint(Long.MIN_VALUE, Long.MIN_VALUE)));
        assertEquals(
                new GridPoint(0, 0),
                Direction.SOUTH.localToWorld(
                        new GridPoint(Long.MIN_VALUE, 0),
                        new GridPoint(0, Long.MIN_VALUE)));
        assertEquals(
                new GridPoint(0, 0),
                Direction.NORTH.localToWorld(
                        new GridPoint(0, Long.MIN_VALUE),
                        new GridPoint(Long.MIN_VALUE, 0)));
    }

    @Test
    void coordinateTransformsRejectResultsOutsideTheLongRange() {
        assertThrows(ArithmeticException.class, () -> Direction.EAST.localToWorld(
                new GridPoint(Long.MAX_VALUE, 0), new GridPoint(1, 0)));
        assertThrows(ArithmeticException.class, () -> Direction.SOUTH.localToWorld(
                new GridPoint(Long.MIN_VALUE, 0), new GridPoint(0, 1)));
        assertThrows(ArithmeticException.class, () -> Direction.WEST.localToWorld(
                new GridPoint(0, Long.MIN_VALUE), new GridPoint(0, 1)));
        assertThrows(ArithmeticException.class, () -> Direction.NORTH.localToWorld(
                new GridPoint(0, Long.MIN_VALUE), new GridPoint(1, 0)));

        GridPoint minX = new GridPoint(Long.MIN_VALUE, 0);
        GridPoint minY = new GridPoint(0, Long.MIN_VALUE);
        assertEquals(minX, Direction.EAST.localOffsetToWorld(minX));
        assertEquals(minY, Direction.EAST.worldOffsetToLocal(minY));
        assertEquals(minY, Direction.SOUTH.localOffsetToWorld(minX));
        assertEquals(minX, Direction.SOUTH.worldOffsetToLocal(minY));
        assertEquals(minX, Direction.NORTH.localOffsetToWorld(minY));
        assertEquals(minY, Direction.NORTH.worldOffsetToLocal(minX));
        assertThrows(ArithmeticException.class, () -> Direction.SOUTH.localOffsetToWorld(minY));
        assertThrows(ArithmeticException.class, () -> Direction.SOUTH.worldOffsetToLocal(minX));
        assertThrows(ArithmeticException.class, () -> Direction.NORTH.localOffsetToWorld(minX));
        assertThrows(ArithmeticException.class, () -> Direction.NORTH.worldOffsetToLocal(minY));
        for (GridPoint point : new GridPoint[] {minX, minY}) {
            assertThrows(ArithmeticException.class, () -> Direction.WEST.localOffsetToWorld(point));
            assertThrows(ArithmeticException.class, () -> Direction.WEST.worldOffsetToLocal(point));
        }
    }
}
