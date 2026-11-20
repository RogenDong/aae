package io.dev.aae;

import io.dev.aae.mech.*;

import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrthogonalSimulatorTest {

    @Test
    void movementFollowsTrackPolarityThroughTurnsInsteadOfMechanismFacing() {
        TrackSpec track = TrackBuilder.start(0, 0).east().eastSouth().noLoop();
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(0, 0, Direction.SOUTH, "ggt"));

        simulator.step();
        assertEquals(new GridPoint(1, 0), simulator.mechanism(0).position());
        assertEquals(Direction.SOUTH, simulator.mechanism(0).direction());

        simulator.step();
        assertEquals(new GridPoint(2, 1), simulator.mechanism(0).position());
        assertEquals(Direction.SOUTH, simulator.mechanism(0).direction());

        simulator.step();
        assertEquals(new GridPoint(1, 0), simulator.mechanism(0).position());
    }

    @Test
    void diagonalTrackCanTurnBetweenDiagonalDirections() {
        TrackSpec track = TrackBuilder.start(0, 0).eastNorth().westNorth().noLoop();
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(0, 0, Direction.WEST, "gg"));

        simulator.run(2);

        assertEquals(new GridPoint(0, -2), simulator.mechanism(0).position());
        assertEquals(Direction.WEST, simulator.mechanism(0).direction());
    }

    @Test
    void openEndpointsAndCellsWithoutTrackAreStationary() {
        TrackSpec track = TrackBuilder.start(0, 0).east().noLoop();
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(0, 0, Direction.EAST, "t"),
                mechanism(1, 0, Direction.WEST, "g"),
                mechanism(9, 9, Direction.NORTH, "g"));

        simulator.step();

        assertEquals(new GridPoint(0, 0), simulator.mechanism(0).position());
        assertEquals(new GridPoint(1, 0), simulator.mechanism(1).position());
        assertEquals(new GridPoint(9, 9), simulator.mechanism(2).position());
    }

    @Test
    void onlyExplicitMixedLoopsWrapAtAdjacentEnds() {
        var trackBuilder = TrackBuilder.start(0, 0).east().south();
        OrthogonalSimulator closed = simulator(List.of(trackBuilder.loop()),
                mechanism(0, 0, Direction.NORTH, "ggg"));
        OrthogonalSimulator open = simulator(List.of(trackBuilder.noLoop()),
                mechanism(0, 0, Direction.NORTH, "ggg"));

        closed.run(3);
        open.run(3);

        assertEquals(new GridPoint(0, 0), closed.mechanism(0).position());
        assertEquals(new GridPoint(1, 1), open.mechanism(0).position());
    }

    @ParameterizedTest
    @CsvSource({"2,t", "1,."})
    void overlappingMechanismDestinationsHaltAndRollBack(long rightX, String rightProgram) {
        TrackSpec track = TrackBuilder.start(0, 0).east().east().noLoop();
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(0, 0, Direction.NORTH, "g"),
                mechanism(rightX, 0, Direction.SOUTH, rightProgram));

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertEquals(1, simulator.warnings().size());
        assertTrue(simulator.warnings().get(0).contains("mechanism[0] conflicts with mechanism[1] at (1,0)"));
        assertEquals(new GridPoint(0, 0), simulator.mechanism(0).position());
        assertEquals(new GridPoint(rightX, 0), simulator.mechanism(1).position());
        assertEquals(List.of(simulator.mechanism(0), simulator.mechanism(1)),
                simulator.mechanisms());
    }

    @Test
    void mechanismsMaySwapPositionsInOneCycle() {
        TrackSpec track = TrackBuilder.start(0, 0).east().noLoop();
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(0, 0, Direction.NORTH, "g"),
                mechanism(1, 0, Direction.NORTH, "t"));

        simulator.step();

        assertEquals(new GridPoint(1, 0), simulator.mechanism(0).position());
        assertEquals(new GridPoint(0, 0), simulator.mechanism(1).position());
    }

    @Test
    void declarationOrderDoesNotChangePerMechanismState() {
        MechanismSpec a = mechanism(0, 0, Direction.NORTH, "ggdga");
        MechanismSpec b = mechanism(10, 0, Direction.WEST, "dgtg.");
        List<TrackSpec> tracks = List.of(squareLoop(0, 0), squareLoop(10, 0));
        OrthogonalSimulator forwardOrder = simulator(tracks, a, b);
        OrthogonalSimulator reverseOrder = simulator(tracks, b, a);

        forwardOrder.run(250);
        reverseOrder.run(250);

        assertEquals(forwardOrder.mechanism(0), reverseOrder.mechanism(1));
        assertEquals(forwardOrder.mechanism(1), reverseOrder.mechanism(0));
    }

    @Test
    void runningInBatchesMatchesOneContinuousRun() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism(0, 0, Direction.SOUTH, "ggadt."),
                        mechanism(10, 0, Direction.EAST, "dgga")),
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
        // 原计划与平移副本均为定长机械，整体平移不得改变轨道节拍和旋转结果。
        GridPoint offset = new GridPoint(100, -50);
        TrackSpec originalTrack = squareLoop(3, 4);
        TrackSpec translatedTrack = new TrackSpec(
                originalTrack.points().stream().map(point -> point.add(offset)).toList(),
                originalTrack.isLoop());
        MechanismSpec originalSpec = mechanism(3, 4, Direction.WEST, "ggagdt");
        MechanismSpec translatedSpec = new StationarySpec(
                originalSpec.initialPosition().add(offset),
                originalSpec.initialDirection(),
                originalSpec.tape());
        OrthogonalSimulator original = simulator(List.of(originalTrack), originalSpec);
        OrthogonalSimulator translated = simulator(List.of(translatedTrack), translatedSpec);

        original.run(117);
        translated.run(117);

        assertEquals(
                original.mechanism(0).position().add(offset),
                translated.mechanism(0).position());
        assertEquals(
                original.mechanism(0).direction(),
                translated.mechanism(0).direction());
    }

    @Test
    void resetRestoresTheInitialState() {
        OrthogonalSimulator simulator = simulator(
                List.of(squareLoop(5, 6)),
                mechanism(5, 6, Direction.SOUTH, "gd"));
        simulator.run(20);

        simulator.reset();

        assertEquals(0, simulator.cycle());
        assertEquals(
                new MechanismView(new GridPoint(5, 6), Direction.SOUTH, false),
                simulator.mechanism(0));
    }

    @Test
    void seededProgramsMatchAnIndependentTrackReferenceModel() {
        Random random = new Random(0x5eedL);
        List<MechanismSpec> specs = new ArrayList<>();
        List<TrackSpec> tracks = new ArrayList<>();
        List<ReferenceState> reference = new ArrayList<>();

        for (int index = 0; index < 40; index++) {
            long baseX = index * 4L;
            Direction direction = Direction.values()[random.nextInt(4)];
            String program = randomProgram(random, 3 + random.nextInt(12));
            TrackSpec track = squareLoop(baseX, 0);
            specs.add(mechanism(baseX, 0, direction, program));
            tracks.add(track);
        }

        // 模拟器初始化时把所有指令带同步到最长长度，因此参考模型也必须按统一长度读取，
        // 才能与模拟器逐周期对齐；指令带容不改写。
        int unifiedTapeLength = specs.stream()
                .mapToInt(spec -> spec.tape().length())
                .max()
                .orElse(0);
        for (int index = 0; index < specs.size(); index++) {
            MechanismSpec spec = specs.get(index);
            reference.add(new ReferenceState(
                    tracks.get(index).points(),
                    spec.initialDirection(),
                    spec.tape(),
                    unifiedTapeLength));
        }

        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(specs, tracks));
        for (long cycle = 0; cycle < 500; cycle++) {
            simulator.step();
            for (int index = 0; index < specs.size(); index++) {
                ReferenceState expected = reference.get(index);
                expected.step(cycle);
                MechanismView mechanism = simulator.mechanism(index);
                assertEquals(expected.view(mechanism.grabbing()), mechanism);
            }
        }
    }

    @Test
    void rejectsInvalidRunCounts() {
        OrthogonalSimulator simulator = simulator();

        assertThrows(IllegalArgumentException.class, () -> simulator.run(-1));
    }

    @Test
    void queriesUseDeclarationIndexesAcrossMovementAndReset() {
        OrthogonalSimulator simulator = simulator(
                List.of(squareLoop(0, 0), squareLoop(10, 0)),
                mechanism(0, 0, Direction.NORTH, "g"),
                mechanism(10, 0, Direction.WEST, "d"));
        List<MechanismView> initial = simulator.mechanisms();
        assertEquals(List.of(
                new MechanismView(new GridPoint(0, 0), Direction.NORTH, false),
                new MechanismView(new GridPoint(10, 0), Direction.WEST, false)), initial);
        assertThrows(UnsupportedOperationException.class, initial::clear);

        simulator.step();

        assertEquals(new GridPoint(1, 0), simulator.mechanism(0).position());
        assertEquals(Direction.NORTH, simulator.mechanism(1).direction());
        for (int index = 0; index < simulator.mechanismCount(); index++) {
            assertEquals(simulator.mechanisms().get(index), simulator.mechanism(index));
            assertTrue(simulator.heldEntities(index).isEmpty());
        }
        assertEquals(new GridPoint(0, 0), initial.get(0).position());

        simulator.reset();

        assertEquals(initial, simulator.mechanisms());
        for (int index = 0; index < simulator.mechanismCount(); index++) {
            assertEquals(initial.get(index), simulator.mechanism(index));
        }
    }

    @Test
    void rejectsIndexesOutsideTheMechanismList() {
        for (OrthogonalSimulator simulator : List.of(simulator(), simulator(
                mechanism(0, 0, Direction.NORTH, "."),
                mechanism(1, 0, Direction.SOUTH, ".")))) {
            assertThrows(IndexOutOfBoundsException.class, () -> simulator.mechanism(-1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> simulator.mechanism(simulator.mechanismCount()));
            assertThrows(IndexOutOfBoundsException.class, () -> simulator.heldEntities(-1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> simulator.heldEntities(simulator.mechanismCount()));
        }
    }

    @Test
    void trackMovementSupportsCoordinatesAtTheLongBoundary() {
        TrackSpec track = new TrackSpec(
                new GridPoint(Long.MAX_VALUE - 1, Long.MIN_VALUE),
                new GridPoint(Long.MAX_VALUE, Long.MIN_VALUE));
        OrthogonalSimulator simulator = simulator(
                List.of(track),
                mechanism(Long.MAX_VALUE - 1, Long.MIN_VALUE, Direction.WEST, "g"));

        simulator.step();

        assertEquals(
                new GridPoint(Long.MAX_VALUE, Long.MIN_VALUE),
                simulator.mechanism(0).position());
    }

    @Test
    void boundedBoardUsesNorthWestOriginAndRequiresPositiveDimensions() {
        // 同一个计划可以配不同棋盘；宽高是列数与行数，而不是最大坐标。
        SimulationPlan plan = SimulationPlan.of();
        assertTrue(new OrthogonalSimulator(plan).boardLimit().contains(-1, -1));
        BoardLimit board = new OrthogonalSimulator(plan, new Inventory(), 3, 2).boardLimit();
        assertEquals(BoardLimit.of(3, 2), board);
        assertTrue(board.contains(0, 0));
        assertTrue(board.contains(2, 1));
        assertFalse(board.contains(-1, 0));
        assertFalse(board.contains(0, -1));
        assertFalse(board.contains(3, 1));
        assertFalse(board.contains(2, 2));
        assertThrows(IllegalArgumentException.class, () -> new OrthogonalSimulator(plan, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new OrthogonalSimulator(plan, 3, -1));
    }

    @ParameterizedTest
    @CsvSource({"-1, 'base (-1,0)'", "1, 'grab point (2,0)'"})
    void boundedBoardRollsBackWhenEitherBaseOrGrabPointLeaves(long nextX, String expectedWarning) {
        // 朝东的机械沿轨道走一步：向西只有基座越界，向东则只有抓握点越界。
        GridPoint origin = new GridPoint(0, 0);
        SimulationPlan plan = SimulationPlan.withTracks(
                List.of(new TrackSpec(origin, new GridPoint(nextX, 0))),
                mechanism(0, 0, Direction.EAST, "g"));
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan, 2, 1);
        assertFalse(simulator.isHalted());

        simulator.run(5);

        assertTrue(simulator.isHalted());
        assertTrue(simulator.warnings().get(0).contains(expectedWarning));
        assertEquals(origin, simulator.mechanism(0).position());
        assertEquals(1, simulator.cycle());
    }
    private static OrthogonalSimulator simulator(MechanismSpec... mechanisms) {
        return new OrthogonalSimulator(SimulationPlan.of(mechanisms));
    }

    private static OrthogonalSimulator simulator(
            List<TrackSpec> tracks, MechanismSpec... mechanisms) {
        return new OrthogonalSimulator(SimulationPlan.withTracks(tracks, mechanisms));
    }

    private static MechanismSpec mechanism(
            long x,
            long y,
            Direction direction,
            String program) {
        // 轨道与转向验收使用定长机构，移动能力不依赖活塞伸缩。
        return new StationarySpec(new GridPoint(x, y), direction, program);
    }

    private static TrackSpec squareLoop(long x, long y) {
        return TrackBuilder.start(x, y).east().south().west().loop();
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
        /** 参考模型读取指令带的有效长度：与模拟器的统一长度一致。 */
        private final int tapeLength;
        private int trackIndex;
        private Direction direction;

        private ReferenceState(
                List<GridPoint> loop, Direction direction, InstructionTape tape, int tapeLength) {
            this.loop = loop;
            this.direction = direction;
            this.tape = tape;
            this.tapeLength = tapeLength;
        }

        private void step(long cycle) {
            switch (tape.instructionAt(cycle, tapeLength)) {
                case MOVE_FORWARD -> trackIndex = (trackIndex + 1) % loop.size();
                case MOVE_BACKWARD -> trackIndex = (trackIndex + loop.size() - 1) % loop.size();
                case ROTATE_CCW -> direction = direction.turnLeft();
                case ROTATE_CW -> direction = direction.turnRight();
                default -> {
                }
            }
        }

        private MechanismView view(boolean grabbing) {
            return new MechanismView(loop.get(trackIndex), direction, grabbing);
        }
    }
}
