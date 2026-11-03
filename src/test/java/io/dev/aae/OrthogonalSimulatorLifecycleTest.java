package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
        assertEquals(0, simulator.outputCount("out"));
        simulator.step();
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(1, simulator.outputCount("out"));
        assertFalse(simulator.isComplete());
        simulator.run(5);
        assertEquals(2, simulator.outputCount("out"));
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
            if (context.cycle() == 3) outputCounts.add(observed[0].outputCount("out"));
        }))) {
            observed[0] = new OrthogonalSimulator(shuttlePlan(1,
                    List.of(new GlyphSpec("observer", type, new GridPoint(9, 9)))));
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
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
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
        Atom eastBonded = Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2);
        Atom westBonded = Atom.of((byte) 5).withBond(Direction.WEST, (byte) 2);
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(0, 0), eastBonded),
                new AtomPlacement(new GridPoint(1, 0), westBonded));

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
        Atom eastBonded = Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2);
        Atom mismatchedWest = Atom.of((byte) 4).withBond(Direction.WEST, (byte) 2);
        Atom westBonded = Atom.of((byte) 5).withBond(Direction.WEST, (byte) 2);

        OrthogonalSimulator matching = pairSimulator(
                List.of(eastBonded, westBonded), List.of(eastBonded, westBonded));
        OrthogonalSimulator wrongType = pairSimulator(
                List.of(eastBonded, mismatchedWest), List.of(eastBonded, westBonded));
        OrthogonalSimulator missingBond = pairSimulator(
                List.of(Atom.of((byte) 1), Atom.of((byte) 5)),
                List.of(eastBonded, westBonded));

        matching.run(5);
        wrongType.run(5);
        missingBond.run(5);

        // 匹配分子在释放后消费；输入处保留补充生产的另一份模板。
        assertEquals(1, matching.outputCount("out"));
        assertEquals(2, matching.boardSize());
        assertNull(matching.atomAt(new GridPoint(-2, 0)));
        assertNull(matching.atomAt(new GridPoint(-1, 0)));
        assertEquals(0, wrongType.outputCount("out"));
        assertEquals(4, wrongType.boardSize());
        assertEquals(mismatchedWest, wrongType.atomAt(new GridPoint(-1, 0)));
        assertEquals(0, missingBond.outputCount("out"));
        assertEquals(4, missingBond.boardSize());
        assertEquals(Atom.of((byte) 1), missingBond.atomAt(new GridPoint(-2, 0)));
        assertEquals(Atom.of((byte) 5), missingBond.atomAt(new GridPoint(-1, 0)));
    }

    @Test
    void completionRequiresEveryOutputToReachTheTarget() {
        OrthogonalSimulator simulator = completionSimulator(2);
        simulator.run(9);
        assertEquals(2, simulator.outputCount("out-a"));
        assertEquals(1, simulator.outputCount("out-b"));
        assertFalse(simulator.isComplete());
        simulator.run(2);
        assertEquals(2, simulator.outputCount("out-b"));
        assertTrue(simulator.isComplete());
        assertEquals(2, simulator.targetOutputs());

        OrthogonalSimulator strict = completionSimulator(3);
        strict.run(14);
        assertEquals(3, strict.outputCount("out-a"));
        assertEquals(2, strict.outputCount("out-b"));
        assertFalse(strict.isComplete());
        strict.run(3);
        assertEquals(3, strict.outputCount("out-b"));
        assertTrue(strict.isComplete());
    }

    @Test
    void planWithoutOutputsIsNeverComplete() {
        assertFalse(new OrthogonalSimulator(SimulationPlan.of()).isComplete());
    }

    // ---------- 抓取、释放与携带 ----------

    @Test
    void grabbedMoleculesRotateAroundTheMechanismWithTheirBonds() {
        Atom eastBonded = Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2);
        Atom westBonded = Atom.of((byte) 5).withBond(Direction.WEST, (byte) 2);
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fd")),
                List.of(InputSpec.of(
                        "in",
                        new AtomPlacement(new GridPoint(0, 0), eastBonded),
                        new AtomPlacement(new GridPoint(1, 0), westBonded))),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertEquals(Direction.EAST, simulator.mechanism(0).direction());
        Atom rotatedSalt = simulator.atomAt(new GridPoint(1, 1));
        Atom rotatedWater = simulator.atomAt(new GridPoint(1, 2));
        assertEquals((byte) 1, rotatedSalt.type());
        assertEquals((byte) 2, rotatedSalt.bondType(Direction.SOUTH));
        assertEquals((byte) 5, rotatedWater.type());
        assertEquals((byte) 2, rotatedWater.bondType(Direction.NORTH));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 2)));
    }

    @Test
    void grabIsNoOpWhenAlreadyHoldingAndReleaseIsNoOpWhenHoldingNothing() {
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(new MechanismSpec(new GridPoint(1, 0), Direction.WEST,
                        InstructionTape.once("rffrr"))),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
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
                        InputSpec.single("in-a", new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(3, 0), otherAtom())),
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
                        InputSpec.single("in-a", new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(2, 0), otherAtom())),
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
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
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
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fggr"),
                        mechanism(1, -1, Direction.SOUTH, ".fggr")),
                List.of(track(0, 1, -1, 1, -2, 1), track(1, -1, 0, -1, -1, -1)),
                List.of(InputSpec.of("in",
                        new AtomPlacement(new GridPoint(0, 0), inputAtoms.get(0)),
                        new AtomPlacement(new GridPoint(1, 0), inputAtoms.get(1)))),
                List.of(OutputSpec.of("out",
                        new AtomPlacement(new GridPoint(-2, 0), outputAtoms.get(0)),
                        new AtomPlacement(new GridPoint(-1, 0), outputAtoms.get(1)))), 1);
        return new OrthogonalSimulator(plan);
    }

    private static SimulationPlan shuttlePlan(long target, List<GlyphSpec> glyphs) {
        return new SimulationPlan(List.of(mechanism(0, 1, Direction.NORTH, ".fgrt")),
                List.of(track(0, 1, 1, 1)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single("out", new GridPoint(1, 0), sampleAtom())), glyphs, target);
    }

    private static OrthogonalSimulator completionSimulator(long target) {
        return new OrthogonalSimulator(new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fgrt"),
                        mechanism(5, 6, Direction.NORTH, "..fgrt")),
                List.of(track(0, 1, 1, 1), track(5, 6, 6, 6)),
                List.of(InputSpec.single("in-a", new GridPoint(0, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(5, 5), otherAtom())),
                List.of(OutputSpec.single("out-a", new GridPoint(1, 0), sampleAtom()),
                        OutputSpec.single("out-b", new GridPoint(6, 5), otherAtom())), target));
    }

    private static MechanismSpec mechanism(
            long x, long y, Direction direction, String program) {
        return new MechanismSpec(
                new GridPoint(x, y),
                direction,
                InstructionTape.looping(program));
    }

    private static TrackSpec track(long... coordinates) {
        if ((coordinates.length & 1) != 0) {
            throw new IllegalArgumentException("track coordinates must be x/y pairs");
        }
        GridPoint[] points = new GridPoint[coordinates.length / 2];
        for (int index = 0; index < points.length; index++) {
            points[index] = new GridPoint(coordinates[index * 2], coordinates[index * 2 + 1]);
        }
        return TrackSpec.orthogonal(points);
    }

    private static Atom sampleAtom() {
        return Atom.of((byte) 1);
    }

    private static Atom otherAtom() {
        return Atom.of((byte) 5);
    }
}
