package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrthogonalSimulatorTest {

    @Test
    void movementFollowsTrackPolarityThroughTurnsInsteadOfMechanismFacing() {
        TrackSpec track = TrackSpec.orthogonal(
                new GridPoint(0, 0),
                new GridPoint(1, 0),
                new GridPoint(1, -1));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("arm", 0, 0, Direction.SOUTH, "ggt"));

        simulator.step();
        assertEquals(new GridPoint(1, 0), simulator.mechanism("arm").position());
        assertEquals(Direction.SOUTH, simulator.mechanism("arm").direction());

        simulator.step();
        assertEquals(new GridPoint(1, -1), simulator.mechanism("arm").position());

        simulator.step();
        assertEquals(new GridPoint(1, 0), simulator.mechanism("arm").position());
    }

    @Test
    void diagonalTrackCanTurnBetweenDiagonalDirections() {
        TrackSpec track = TrackSpec.diagonal(
                new GridPoint(0, 0),
                new GridPoint(1, -1),
                new GridPoint(0, -2));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("arm", 0, 0, Direction.WEST, "gg"));

        simulator.run(2);

        assertEquals(new GridPoint(0, -2), simulator.mechanism("arm").position());
        assertEquals(Direction.WEST, simulator.mechanism("arm").direction());
    }

    @Test
    void openEndpointsAndCellsWithoutTrackAreStationary() {
        TrackSpec track = TrackSpec.orthogonal(
                new GridPoint(0, 0), new GridPoint(1, 0));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("minus-end", 0, 0, Direction.EAST, "t"),
                mechanism("plus-end", 1, 0, Direction.WEST, "g"),
                mechanism("off-track", 9, 9, Direction.NORTH, "g"));

        simulator.step();

        assertEquals(new GridPoint(0, 0), simulator.mechanism("minus-end").position());
        assertEquals(new GridPoint(1, 0), simulator.mechanism("plus-end").position());
        assertEquals(new GridPoint(9, 9), simulator.mechanism("off-track").position());
    }

    @Test
    void adjacentEndsOfLongTrackFormALoop() {
        TrackSpec loop = squareLoop(0, 0);
        OrthogonalSimulator simulator = simulator(
                List.of(loop), mechanism("arm", 0, 0, Direction.NORTH, "gggg"));

        simulator.run(4);

        assertEquals(new GridPoint(0, 0), simulator.mechanism("arm").position());
    }

    @ParameterizedTest
    @CsvSource({"2,t", "1,."})
    void overlappingMechanismDestinationsHaltAndRollBack(long rightX, String rightProgram) {
        TrackSpec track = TrackSpec.orthogonal(
                new GridPoint(0, 0), new GridPoint(1, 0), new GridPoint(2, 0));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("left", 0, 0, Direction.NORTH, "g"),
                mechanism("right", rightX, 0, Direction.SOUTH, rightProgram));

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertEquals(1, simulator.warnings().size());
        assertTrue(simulator.warnings().get(0).contains("mechanism left conflicts with mechanism right at (1,0)"));
        assertEquals(new GridPoint(0, 0), simulator.mechanism("left").position());
        assertEquals(new GridPoint(rightX, 0), simulator.mechanism("right").position());
        assertEquals(List.of(simulator.mechanism("left"), simulator.mechanism("right")),
                simulator.mechanisms());
    }

    @Test
    void mechanismsMaySwapPositionsInOneCycle() {
        TrackSpec track = TrackSpec.orthogonal(
                new GridPoint(0, 0), new GridPoint(1, 0));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("a", 0, 0, Direction.NORTH, "g"),
                mechanism("b", 1, 0, Direction.NORTH, "t"));

        simulator.step();

        assertEquals(new GridPoint(1, 0), simulator.mechanism("a").position());
        assertEquals(new GridPoint(0, 0), simulator.mechanism("b").position());
    }

    @Test
    void declarationOrderDoesNotChangePerMechanismState() {
        MechanismSpec a = mechanism("a", 0, 0, Direction.NORTH, "ggdga");
        MechanismSpec b = mechanism("b", 10, 0, Direction.WEST, "dgtg.");
        List<TrackSpec> tracks = List.of(squareLoop(0, 0), squareLoop(10, 0));
        OrthogonalSimulator forwardOrder = simulator(tracks, a, b);
        OrthogonalSimulator reverseOrder = simulator(tracks, b, a);

        forwardOrder.run(250);
        reverseOrder.run(250);

        assertEquals(forwardOrder.mechanism("a"), reverseOrder.mechanism("a"));
        assertEquals(forwardOrder.mechanism("b"), reverseOrder.mechanism("b"));
    }

    @Test
    void runningInBatchesMatchesOneContinuousRun() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("a", 0, 0, Direction.SOUTH, "ggadt."),
                        mechanism("b", 10, 0, Direction.EAST, "dgga")),
                List.of(squareLoop(0, 0), squareLoop(10, 0)));
        OrthogonalSimulator continuous = new OrthogonalSimulator(plan);
        OrthogonalSimulator batched = new OrthogonalSimulator(plan);

        continuous.run(1_000);
        batched.run(400);
        batched.run(600);

        assertEquals(continuous.cycle(), batched.cycle());
        assertEquals(continuous.mechanisms(), batched.mechanisms());
        assertEquals(continuous.checksum(), batched.checksum());
    }

    @Test
    void translatingTheWholePlanTranslatesTheResult() {
        GridPoint offset = new GridPoint(100, -50);
        TrackSpec originalTrack = squareLoop(3, 4);
        TrackSpec translatedTrack = new TrackSpec(
                originalTrack.type(),
                originalTrack.points().stream().map(point -> point.add(offset)).toList());
        MechanismSpec originalSpec = mechanism("m", 3, 4, Direction.WEST, "ggagdt");
        MechanismSpec translatedSpec = new MechanismSpec(
                originalSpec.id(),
                originalSpec.initialPosition().add(offset),
                originalSpec.initialDirection(),
                originalSpec.tape());
        OrthogonalSimulator original = simulator(List.of(originalTrack), originalSpec);
        OrthogonalSimulator translated = simulator(List.of(translatedTrack), translatedSpec);

        original.run(117);
        translated.run(117);

        assertEquals(
                original.mechanism("m").position().add(offset),
                translated.mechanism("m").position());
        assertEquals(
                original.mechanism("m").direction(),
                translated.mechanism("m").direction());
    }

    @Test
    void resetRestoresTheInitialState() {
        OrthogonalSimulator simulator = simulator(
                List.of(squareLoop(5, 6)),
                mechanism("m", 5, 6, Direction.SOUTH, "gd"));
        simulator.run(20);

        simulator.reset();

        assertEquals(0, simulator.cycle());
        assertEquals(
                new MechanismView("m", new GridPoint(5, 6), Direction.SOUTH, false),
                simulator.mechanism("m"));
    }

    @Test
    void seededProgramsMatchAnIndependentTrackReferenceModel() {
        Random random = new Random(0x5eedL);
        List<MechanismSpec> specs = new ArrayList<>();
        List<TrackSpec> tracks = new ArrayList<>();
        Map<String, ReferenceState> reference = new HashMap<>();

        for (int index = 0; index < 40; index++) {
            String id = "m-" + index;
            long baseX = index * 4L;
            Direction direction = Direction.values()[random.nextInt(4)];
            String program = randomProgram(random, 3 + random.nextInt(12));
            TrackSpec track = squareLoop(baseX, 0);
            MechanismSpec spec = mechanism(id, baseX, 0, direction, program);
            specs.add(spec);
            tracks.add(track);
            reference.put(id, new ReferenceState(track.points(), direction, spec.tape()));
        }

        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(specs, tracks));
        for (long cycle = 0; cycle < 500; cycle++) {
            simulator.step();
            for (MechanismSpec spec : specs) {
                ReferenceState expected = reference.get(spec.id());
                expected.step(cycle);
                MechanismView mechanism = simulator.mechanism(spec.id());
                assertEquals(expected.view(spec.id(), mechanism.grabbing()), mechanism);
            }
        }
    }

    @Test
    void rejectsInvalidRunCountsAndUnknownIds() {
        OrthogonalSimulator simulator = simulator();

        assertThrows(IllegalArgumentException.class, () -> simulator.run(-1));
        assertThrows(IllegalArgumentException.class, () -> simulator.mechanism("missing"));
    }

    @Test
    void trackMovementSupportsCoordinatesAtTheLongBoundary() {
        TrackSpec track = TrackSpec.orthogonal(
                new GridPoint(Long.MAX_VALUE - 1, Long.MIN_VALUE),
                new GridPoint(Long.MAX_VALUE, Long.MIN_VALUE));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism("edge", Long.MAX_VALUE - 1, Long.MIN_VALUE, Direction.WEST, "g"));

        simulator.step();

        assertEquals(
                new GridPoint(Long.MAX_VALUE, Long.MIN_VALUE),
                simulator.mechanism("edge").position());
    }

    private static OrthogonalSimulator simulator(MechanismSpec... mechanisms) {
        return new OrthogonalSimulator(SimulationPlan.of(mechanisms));
    }

    private static OrthogonalSimulator simulator(
            List<TrackSpec> tracks, MechanismSpec... mechanisms) {
        return new OrthogonalSimulator(SimulationPlan.withTracks(tracks, mechanisms));
    }

    private static MechanismSpec mechanism(
            String id,
            long x,
            long y,
            Direction direction,
            String program) {
        return new MechanismSpec(
                id,
                new GridPoint(x, y),
                direction,
                InstructionTape.looping(program));
    }

    private static TrackSpec squareLoop(long x, long y) {
        return TrackSpec.orthogonal(
                new GridPoint(x, y),
                new GridPoint(x + 1, y),
                new GridPoint(x + 1, y + 1),
                new GridPoint(x, y + 1));
    }

    private static String randomProgram(Random random, int length) {
        char[] symbols = {'t', 'g', 'a', 'd', '.'};
        StringBuilder program = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            program.append(symbols[random.nextInt(symbols.length)]);
        }
        return program.toString();
    }

    private static final class ReferenceState {
        private final List<GridPoint> loop;
        private final InstructionTape tape;
        private int trackIndex;
        private Direction direction;

        private ReferenceState(
                List<GridPoint> loop, Direction direction, InstructionTape tape) {
            this.loop = loop;
            this.direction = direction;
            this.tape = tape;
        }

        private void step(long cycle) {
            switch (tape.instructionAt(cycle)) {
                case MOVE_FORWARD -> trackIndex = (trackIndex + 1) % loop.size();
                case MOVE_BACKWARD -> trackIndex = (trackIndex + loop.size() - 1) % loop.size();
                case ROTATE_CCW -> direction = direction.turnLeft();
                case ROTATE_CW -> direction = direction.turnRight();
                default -> {
                }
            }
        }

        private MechanismView view(String id, boolean grabbing) {
            return new MechanismView(id, loop.get(trackIndex), direction, grabbing);
        }
    }
}
