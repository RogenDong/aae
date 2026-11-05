package io.dev.aae;

import io.dev.aae.glyph.GlyphDefinition;
import io.dev.aae.glyph.GlyphRegistry;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrthogonalSimulatorLifecycleTest {

    // ---------- 输入生产 ----------

    @Test
    void inputRefillsAfterTransportAndOutputConsumesAfterRelease() {
        OrthogonalSimulator simulator = new OrthogonalSimulator(shuttlePlan(2, List.of()));
        assertEquals(0, simulator.boardSize());
        assertFalse(simulator.isComplete());
        simulator.step();
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        simulator.run(2);
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        assertFalse(simulator.isHalted());
        assertEquals(new GridPoint(1, 1), simulator.mechanism(0).position());
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 0)));
        assertEquals(1, simulator.heldEntities(0).size());
        assertEquals(0, simulator.outputCount(0));
        long inputEntity = simulator.entityAt(new GridPoint(0, 0));
        simulator.step();
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(0L, simulator.entityAt(new GridPoint(1, 0)));
        assertEquals(0, simulator.grabsAt(new GridPoint(1, 0)));
        assertEquals(inputEntity, simulator.entityAt(new GridPoint(0, 0)));
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(1, simulator.outputCount(0));
        assertFalse(simulator.isComplete());
        simulator.run(5);
        assertEquals(2, simulator.outputCount(0));
        assertTrue(simulator.isComplete());
        assertEquals(9, simulator.cycle());
    }

    @Test
    void inputRefillsInSecondHalfAndReleasedOutputIsConsumedInFirstHalf() {
        List<Long> inputIds = new ArrayList<>();
        List<Long> outputCounts = new ArrayList<>();
        OrthogonalSimulator[] observed = new OrthogonalSimulator[1];
        String type = "test.lifecycle:observe-halves";
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.single(type, context -> {
            if (context.cycle() == 2) inputIds.add(observed[0].entityAt(new GridPoint(0, 0)));
            if (context.cycle() == 3) outputCounts.add(observed[0].outputCount(0));
        }))) {
            observed[0] = new OrthogonalSimulator(shuttlePlan(1,
                    List.of(new GlyphSpec(type, new GridPoint(9, 9)))));
            observed[0].run(4);
            assertEquals(2, inputIds.size());
            assertNotEquals(inputIds.get(0), inputIds.get(1));
            assertTrue(inputIds.stream().allMatch(id -> id > 0));
            // 符文阶段在消费之前：第二半周期已经能观察到第一半周期的输出消费。
            assertEquals(List.of(0L, 1L), outputCounts);
            assertTrue(observed[0].isComplete());
        }
    }

    @Test
    void inputWaitsWhileItsPlaceholderIsOccupiedByAnAtom() {
        // 机械臂在周期 1 抓住示例原子、周期 2 在原位释放，释放后的原子一直
        // 挡在输入占位上，输入不能再生产。
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(1, 0, Direction.WEST, ".fr")),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertEquals(1, simulator.boardSize());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(0, simulator.grabsAt(new GridPoint(0, 0)));
    }

    @Test
    void inputProducesMultiatomBondedMolecules() {
        Atom eastBonded = Atom.of(1).withBond(Direction.EAST, 2);
        Atom westBonded = Atom.of(5).withBond(Direction.WEST, 2);
        InputSpec input = InputSpec.of(
                AtomPlacement.of(0, 0, eastBonded),
                AtomPlacement.of(1, 0, westBonded));

        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(), List.of(input), List.of(), 1));
        simulator.step();

        assertEquals(2, simulator.boardSize());
        assertEquals(2, simulator.moleculeAt(new GridPoint(0, 0)).size());
        assertEquals(2, simulator.moleculeAt(new GridPoint(1, 0)).size());
    }

    // ---------- 输出匹配 ----------

    @Test
    void outputConsumesOnlyAnExactTypeBondAndShapeMatch() {
        Atom eastBonded = Atom.of(0).withBond(Direction.EAST, 2);
        Atom mismatchedWest = Atom.of(0x0201).withBond(Direction.WEST, 2);
        Atom westBonded = Atom.of(0x0101).withBond(Direction.WEST, 2);

        OrthogonalSimulator matching = pairSimulator(
                List.of(eastBonded, westBonded), List.of(eastBonded, westBonded));
        matching.run(4);
        assertEquals(0, matching.outputCount(0));
        assertTrue(matching.grabsAt(new GridPoint(-2, 0)) > 0);
        assertTrue(matching.grabsAt(new GridPoint(-1, 0)) > 0);
        long outsideEntity = matching.entityAt(new GridPoint(0, 0));
        matching.step();

        // 匹配分子在释放后消费；输入处保留补充生产的另一份模板。
        assertEquals(1, matching.outputCount(0));
        assertEquals(2, matching.boardSize());
        assertNull(matching.atomAt(new GridPoint(-2, 0)));
        assertNull(matching.atomAt(new GridPoint(-1, 0)));
        assertEquals(0L, matching.entityAt(new GridPoint(-2, 0)));
        assertEquals(0L, matching.entityAt(new GridPoint(-1, 0)));
        assertEquals(outsideEntity, matching.entityAt(new GridPoint(0, 0)));
        assertEquals(eastBonded, matching.atomAt(new GridPoint(0, 0)));

        for (List<Atom> actual : List.of(
                List.of(eastBonded, mismatchedWest),
                List.of(Atom.of(0), Atom.of(0x0101)),
                List.of(eastBonded, Atom.of(0x0101).withBond(Direction.WEST, 3)),
                List.of(eastBonded, Atom.of(0x0101).withBond(Direction.NORTH, 2)),
                List.of(eastBonded, westBonded.withBond(Direction.EAST, 2)),
                List.of(eastBonded))) {
            OrthogonalSimulator mismatch = pairSimulator(actual, List.of(eastBonded, westBonded));
            mismatch.run(4);
            assertFalse(mismatch.isHalted());
            long westEntity = mismatch.entityAt(new GridPoint(-2, 0));
            long eastEntity = mismatch.entityAt(new GridPoint(-1, 0));
            mismatch.step();
            assertFalse(mismatch.isHalted());
            assertEquals(0, mismatch.outputCount(0));
            assertEquals(westEntity, mismatch.entityAt(new GridPoint(-2, 0)));
            assertEquals(eastEntity, mismatch.entityAt(new GridPoint(-1, 0)));
            assertEquals(actual.get(0), mismatch.atomAt(new GridPoint(-2, 0)));
            if (actual.size() == 2) {
                assertEquals(actual.get(1), mismatch.atomAt(new GridPoint(-1, 0)));
                assertEquals(4, mismatch.boardSize());
            } else {
                assertNull(mismatch.atomAt(new GridPoint(-1, 0)));
                assertEquals(2, mismatch.boardSize());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputRejectsTemporaryBondsAndMissingEntitiesBeforeRemovingAnyAtom()
            throws ReflectiveOperationException {
        GridPoint west = new GridPoint(-2, 0);
        GridPoint east = new GridPoint(-1, 0);
        Atom eastBonded = Atom.of(0).withBond(Direction.EAST, 2);
        Atom westBonded = Atom.of(0x0101).withBond(Direction.WEST, 2);
        OrthogonalSimulator simulator = pairSimulator(
                List.of(eastBonded, westBonded.withType(0x0201)), List.of(eastBonded, westBonded));
        simulator.run(5);
        long westEntity = simulator.entityAt(west);
        long eastEntity = simulator.entityAt(east);
        long outsideEntity = simulator.entityAt(new GridPoint(0, 0));
        assertTrue(westEntity > 0);
        assertTrue(eastEntity > 0);
        assertEquals(0, simulator.grabsAt(east));

        // 节点：已释放但类型不符的双原子输出；导航顺序：搬运至输出，再直接调用消费阶段。
        // 符文占位不能与输出相交，因此通过反射注入临时键/缺失索引；预期任一校验失败都保留整份模板。
        var boardField = OrthogonalSimulator.class.getDeclaredField("board");
        boardField.setAccessible(true);
        AtomGrid board = (AtomGrid) boardField.get(simulator);
        var entitiesField = OrthogonalSimulator.class.getDeclaredField("entityAtPosition");
        entitiesField.setAccessible(true);
        Map<GridPoint, Long> entities = (Map<GridPoint, Long>) entitiesField.get(simulator);
        var positionsField = OrthogonalSimulator.class.getDeclaredField("positionOfEntity");
        positionsField.setAccessible(true);
        Map<Long, GridPoint> positions = (Map<Long, GridPoint>) positionsField.get(simulator);
        var grabsField = OrthogonalSimulator.class.getDeclaredField("grabCounts");
        grabsField.setAccessible(true);
        Map<Long, Integer> grabs = (Map<Long, Integer>) grabsField.get(simulator);
        var consume = OrthogonalSimulator.class.getDeclaredMethod("consumeOutputs");
        consume.setAccessible(true);

        for (Direction direction : Direction.values()) {
            Atom temporary = westBonded.withBond(direction, Atom.TEMPORARY_BOND);
            board.put(east, temporary);
            consume.invoke(simulator);
            assertEquals(0, simulator.outputCount(0));
            assertEquals(eastBonded, simulator.atomAt(west));
            assertEquals(temporary, simulator.atomAt(east));
            assertEquals(westEntity, simulator.entityAt(west));
            assertEquals(eastEntity, simulator.entityAt(east));
        }

        board.put(east, westBonded);
        entities.remove(east);
        consume.invoke(simulator);
        assertEquals(0, simulator.outputCount(0));
        assertEquals(eastBonded, simulator.atomAt(west));
        assertEquals(westBonded, simulator.atomAt(east));
        assertEquals(westEntity, simulator.entityAt(west));
        assertEquals(west, positions.get(westEntity));
        assertEquals(east, positions.get(eastEntity));

        entities.put(east, eastEntity);
        consume.invoke(simulator);
        assertEquals(1, simulator.outputCount(0));
        assertNull(simulator.atomAt(west));
        assertNull(simulator.atomAt(east));
        assertEquals(0L, simulator.entityAt(west));
        assertEquals(0L, simulator.entityAt(east));
        assertFalse(positions.containsKey(westEntity));
        assertFalse(positions.containsKey(eastEntity));
        assertFalse(grabs.containsKey(westEntity));
        assertFalse(grabs.containsKey(eastEntity));
        assertEquals(outsideEntity, simulator.entityAt(new GridPoint(0, 0)));
        assertEquals(eastBonded, simulator.atomAt(new GridPoint(0, 0)));
    }

    @Test
    void completionRequiresEveryOutputToReachTheTarget() {
        OrthogonalSimulator simulator = completionSimulator(2);
        simulator.run(9);
        assertEquals(2, simulator.outputCount(0));
        assertEquals(1, simulator.outputCount(1));
        long[] snapshot = simulator.outputCounts();
        assertArrayEquals(new long[] {2, 1}, snapshot);
        long[] modified = simulator.outputCounts();
        modified[0] = 999;
        assertEquals(2, simulator.outputCount(0));
        assertFalse(simulator.isComplete());
        simulator.run(2);
        assertEquals(2, simulator.outputCount(1));
        assertTrue(simulator.isComplete());
        assertEquals(2, simulator.targetOutputs());
        assertArrayEquals(new long[] {2, 2}, simulator.outputCounts());
        simulator.reset();
        assertArrayEquals(new long[2], simulator.outputCounts());
        assertArrayEquals(new long[] {2, 1}, snapshot);

        OrthogonalSimulator strict = completionSimulator(3);
        strict.run(14);
        assertEquals(3, strict.outputCount(0));
        assertEquals(2, strict.outputCount(1));
        assertFalse(strict.isComplete());
        strict.run(3);
        assertEquals(3, strict.outputCount(1));
        assertTrue(strict.isComplete());
    }

    @Test
    void planWithoutOutputsIsNeverComplete() {
        var simulator = new OrthogonalSimulator(SimulationPlan.of());
        assertFalse(simulator.isComplete());
        assertArrayEquals(new long[0], simulator.outputCounts());
    }

    @Test
    void outputCountsRestoreTheirDeclarationSlotsWhenACycleRollsBack() {
        String type = "test.lifecycle:output-rollback";
        RuntimeException failure = new IllegalStateException("after first-half consumption");
        OrthogonalSimulator[] observed = new OrthogonalSimulator[1];
        GridPoint consumed = new GridPoint(6, 5);
        long[] consumedEntity = new long[1];
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.single(type, context -> {
            if (context.cycle() == 4 && context.phase().isSecondHalf()) {
                assertArrayEquals(new long[] {1, 1}, observed[0].outputCounts());
                assertNull(observed[0].atomAt(consumed));
                assertEquals(0L, observed[0].entityAt(consumed));
                assertEquals(0, observed[0].grabsAt(consumed));
                assertFalse(observed[0].heldEntities(1).contains(consumedEntity[0]));
                throw failure;
            }
        }))) {
            SimulationPlan base = completionSimulator(2).plan();
            var simulator = new OrthogonalSimulator(new SimulationPlan(base.mechanisms(), base.tracks(),
                    base.inputs(), base.outputs(), List.of(new GlyphSpec(type, new GridPoint(20, 20))), 2));
            observed[0] = simulator;
            simulator.run(4);
            assertArrayEquals(new long[] {1, 0}, simulator.outputCounts());
            List<GridPoint> positions = List.copyOf(simulator.atomPositions());
            List<Atom> atoms = positions.stream().map(simulator::atomAt).toList();
            List<Long> entities = positions.stream().map(simulator::entityAt).toList();
            List<Integer> grabs = positions.stream().map(simulator::grabsAt).toList();
            List<Long> held = simulator.heldEntities(1);
            consumedEntity[0] = simulator.entityAt(consumed);
            assertTrue(consumedEntity[0] > 0);
            assertSame(failure, assertThrows(RuntimeException.class, simulator::step));
            assertTrue(simulator.isHalted());
            assertArrayEquals(new long[] {1, 0}, simulator.outputCounts());
            assertEquals(positions, List.copyOf(simulator.atomPositions()));
            assertEquals(atoms, positions.stream().map(simulator::atomAt).toList());
            assertEquals(entities, positions.stream().map(simulator::entityAt).toList());
            assertEquals(grabs, positions.stream().map(simulator::grabsAt).toList());
            assertEquals(held, simulator.heldEntities(1));
        }
    }

    @Test
    void outputCountRejectsIndexesOutsideEmptyAndNonemptyLists() {
        for (var simulator : List.of(new OrthogonalSimulator(SimulationPlan.of()), completionSimulator(1))) {
            assertThrows(IndexOutOfBoundsException.class, () -> simulator.outputCount(-1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> simulator.outputCount(simulator.plan().outputs().size()));
        }
    }

    // ---------- 抓取、释放与携带 ----------

    @Test
    void grabbedMoleculesRotateAroundTheMechanismWithTheirBonds() {
        Atom eastBonded = Atom.of(0).withBond(Direction.EAST, 2);
        Atom westBonded = Atom.of(0xFFFF).withBond(Direction.WEST, 2);
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fd")),
                List.of(InputSpec.of(
                        AtomPlacement.of(0, 0, eastBonded),
                        AtomPlacement.of(1, 0, westBonded))),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertEquals(Direction.EAST, simulator.mechanism(0).direction());
        Atom rotatedSalt = simulator.atomAt(new GridPoint(1, 1));
        Atom rotatedWater = simulator.atomAt(new GridPoint(1, 2));
        assertEquals(0, rotatedSalt.type());
        assertEquals((byte) 2, rotatedSalt.bondType(Direction.SOUTH));
        assertEquals(0xFFFF, rotatedWater.type());
        assertEquals((byte) 2, rotatedWater.bondType(Direction.NORTH));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 2)));
    }

    @Test
    void grabIsNoOpWhenAlreadyHoldingAndReleaseIsNoOpWhenHoldingNothing() {
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(new MechanismSpec(new GridPoint(1, 0), Direction.WEST,
                        InstructionTape.once("rffrr"))),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1));

        simulator.init();
        simulator.step();
        assertTrue(simulator.heldEntities(0).isEmpty());
        simulator.run(2);

        // 重复抓取不累加计数；重复释放也不会让计数变成负数。
        assertEquals(1, simulator.boardSize());
        assertEquals(1, simulator.grabsAt(new GridPoint(0, 0)));
        assertEquals(1, simulator.heldEntities(0).size());
        simulator.run(2);
        assertFalse(simulator.isHalted());
        assertEquals(1, simulator.boardSize());
        assertEquals(0, simulator.grabsAt(new GridPoint(0, 0)));
        assertTrue(simulator.heldEntities(0).isEmpty());
    }

    // ---------- 意图冲突与停机 ----------

    @Test
    void atomsMovingOntoTheSameCellHaltInsteadOfOverwriting() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism(1, 1, Direction.NORTH, ".fg"),
                        mechanism(3, -1, Direction.SOUTH, ".fg")),
                List.of(
                        track(1, 1, 2, 1),
                        track(3, -1, 2, -1)),
                List.of(
                        InputSpec.single(new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single(new GridPoint(3, 0), otherAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertTrue(simulator.isHalted());
        // 冲突发生在第三个周期的第二半周期，因此 cycle 停在 3。
        assertEquals(3, simulator.cycle());
        assertEquals(2, simulator.boardSize());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(otherAtom(), simulator.atomAt(new GridPoint(3, 0)));
    }

    @Test
    void haltStopsAllFurtherStepsAndResetRecovers() {
        // 机构抓着 (1,0) 的原子沿轨道往东走，第二半周期撞上静止的 (2,0) 原子而停机。
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(1, 1, Direction.NORTH, ".fg")),
                List.of(track(1, 1, 2, 1)),
                List.of(
                        InputSpec.single(new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single(new GridPoint(2, 0), otherAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);
        assertTrue(simulator.isHalted());
        long haltedCycle = simulator.cycle();
        assertTrue(simulator.warnings().get(0).contains("already occupied by entity"));
        assertEquals(new GridPoint(1, 1), simulator.mechanism(0).position());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(otherAtom(), simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 0)));

        simulator.step();
        simulator.run(10);
        assertEquals(haltedCycle, simulator.cycle());
        assertEquals(1, simulator.warnings().size());

        simulator.reset();
        assertFalse(simulator.isHalted());
        assertEquals(0, simulator.cycle());
        assertTrue(simulator.warnings().isEmpty());
        assertEquals(0, simulator.boardSize());

        simulator.run(4);
        assertTrue(simulator.isHalted());
        assertEquals(haltedCycle, simulator.cycle());
    }

    @Test
    void boardQueriesReflectTheAtomicState() {
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1));

        assertNull(simulator.atomAt(new GridPoint(0, 0)));
        assertTrue(simulator.atomPositions().isEmpty());
        assertTrue(simulator.moleculeAt(new GridPoint(0, 0)).isEmpty());

        simulator.step();

        assertEquals(1, simulator.boardSize());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(List.of(new GridPoint(0, 0)), List.copyOf(simulator.atomPositions()));
        assertEquals(1, simulator.moleculeAt(new GridPoint(0, 0)).size());
        assertEquals(1, simulator.molecules().size());
    }

    // ---------- 辅助 ----------

    private static OrthogonalSimulator pairSimulator(
            List<Atom> inputAtoms, List<Atom> outputAtoms) {
        // 两步向西搬运，输出不与输入相交；两臂分别携带，缺键样本也能完整抵达输出。
        List<AtomPlacement> inputs = new ArrayList<>();
        for (int i = 0; i < inputAtoms.size(); i++) {
            inputs.add(AtomPlacement.of(i, 0, inputAtoms.get(i)));
        }
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fggr"),
                        mechanism(1, -1, Direction.SOUTH, ".fggr")),
                List.of(TrackBuilder.start(0, 1).west().west().noLoop(),
                        TrackBuilder.start(1, -1).west().west().noLoop()),
                List.of(new InputSpec(inputs)),
                List.of(OutputSpec.of(
                        AtomPlacement.of(-2, 0, outputAtoms.get(0)),
                        AtomPlacement.of(-1, 0, outputAtoms.get(1)))), 1);
        return new OrthogonalSimulator(plan);
    }

    private static SimulationPlan shuttlePlan(long target, List<GlyphSpec> glyphs) {
        return new SimulationPlan(List.of(mechanism(0, 1, Direction.NORTH, ".fgrt")),
                List.of(track(0, 1, 1, 1)),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single(new GridPoint(1, 0), sampleAtom())), glyphs, target);
    }

    private static OrthogonalSimulator completionSimulator(long target) {
        return new OrthogonalSimulator(new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fgrt"),
                        mechanism(5, 6, Direction.NORTH, "..fgrt")),
                List.of(track(0, 1, 1, 1), track(5, 6, 6, 6)),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom()),
                        InputSpec.single(new GridPoint(5, 5), otherAtom())),
                List.of(OutputSpec.single(new GridPoint(1, 0), sampleAtom()),
                        OutputSpec.single(new GridPoint(6, 5), otherAtom())), target));
    }

    private static MechanismSpec mechanism(
            long x, long y, Direction direction, String program) {
        return new MechanismSpec(new GridPoint(x, y), direction, program);
    }

    private static TrackSpec track(long... coordinates) {
        if ((coordinates.length & 1) != 0) {
            throw new IllegalArgumentException("track coordinates must be x/y pairs");
        }
        GridPoint[] points = new GridPoint[coordinates.length / 2];
        for (int index = 0; index < points.length; index++) {
            points[index] = new GridPoint(coordinates[index * 2], coordinates[index * 2 + 1]);
        }
        return new TrackSpec(points);
    }

    private static Atom sampleAtom() {
        return Atom.of(0);
    }

    private static Atom otherAtom() {
        return Atom.of(0x8000);
    }
}
