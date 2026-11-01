package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrthogonalSimulatorLifecycleTest {

    // ---------- 输入生产 ----------

    @Test
    void inputProducesAtTheEndOfEveryFreeHalfCycleAndOutputConsumesIt() {
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single("out", new GridPoint(0, 0), sampleAtom())),
                2);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);

        assertEquals(0, simulator.boardSize());
        assertFalse(simulator.isComplete());

        // 一个周期包含两个半周期：第一半周期生产并立即消费一次，第二半周期再消费一次。
        simulator.step();
        assertEquals(2, simulator.outputCount("out"));
        assertTrue(simulator.isComplete());
        assertEquals(1, simulator.cycle());
    }

    @Test
    void inputsAndOutputsBothRunInEachHalfCycle() {
        // 允许“输入与输出在同一周期的两个半周期分别生产或消费”的最小可观察形态：
        // 输入模板共用一个格点，因此第二半周期无法再生产，而输出每个半周期消费一次。
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single("out", new GridPoint(0, 0), sampleAtom())),
                4);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);

        simulator.step();
        assertEquals(2, simulator.outputCount("out"));
        assertFalse(simulator.isComplete());

        simulator.step();
        assertEquals(4, simulator.outputCount("out"));
        assertTrue(simulator.isComplete());
        assertEquals(2, simulator.cycle());
    }

    @Test
    void inputWaitsWhileItsPlaceholderIsOccupiedByAnAtom() {
        // 机械臂在周期 1 抓住示例原子、周期 2 在原位释放，释放后的原子一直
        // 挡在输入占位上，输入不能再生产。
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 1, 0, Direction.WEST, ".fr")),
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

        matching.step();
        wrongType.step();
        missingBond.step();

        // 两个半周期里，匹配的分子被生产并消费两次；两个格点因此都被清空。
        assertEquals(2, matching.outputCount("out"));
        assertEquals(0, matching.boardSize());
        assertEquals(0, wrongType.outputCount("out"));
        assertEquals(2, wrongType.boardSize());
        assertEquals(0, missingBond.outputCount("out"));
        assertEquals(2, missingBond.boardSize());
    }

    @Test
    void outputIgnoresGrabbedMoleculesUntilTheyAreReleased() {
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 1, 0, Direction.WEST, "fgr")),
                List.of(track(1, 0, 0, 0)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single("out", new GridPoint(-1, 0), sampleAtom())),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.init();

        // 周期 2 分子被搬到输出锚点上，但持有期间消费被抑制。
        simulator.run(2);
        assertEquals(0, simulator.outputCount("out"));
        assertEquals(1, simulator.grabsAt(new GridPoint(-1, 0)));

        // 新规则在周期 2 结束前检查到分子与机构基座重叠，整周期回滚并停机；
        // 后续 step 不再推进。
        simulator.run(1);
        assertTrue(simulator.isHalted());
        assertEquals(0, simulator.outputCount("out"));
        assertFalse(simulator.isComplete());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(-1, 0)));
    }

    @Test
    void completionRequiresEveryOutputToReachTheTarget() {
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(
                        InputSpec.single("in-a", new GridPoint(0, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(5, 5), otherAtom())),
                List.of(
                        OutputSpec.single("out-a", new GridPoint(0, 0), sampleAtom()),
                        OutputSpec.single("out-b", new GridPoint(5, 5), otherAtom())),
                2);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.step();
        // 一个周期包含两个半周期，输入生产与输出消费各发生两次，两个输出都直接达标。
        assertEquals(2, simulator.outputCount("out-a"));
        assertEquals(2, simulator.outputCount("out-b"));
        assertTrue(simulator.isComplete());
        assertEquals(2, simulator.targetOutputs());

        // 目标提到 3 之后不再完成，直到第三个周期之后才再次达标。
        SimulationPlan stricter = new SimulationPlan(
                List.of(),
                List.of(
                        InputSpec.single("in-a", new GridPoint(0, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(5, 5), otherAtom())),
                List.of(
                        OutputSpec.single("out-a", new GridPoint(0, 0), sampleAtom()),
                        OutputSpec.single("out-b", new GridPoint(5, 5), otherAtom())),
                4);
        OrthogonalSimulator strict = new OrthogonalSimulator(stricter);
        strict.step();
        assertEquals(2, strict.outputCount("out-a"));
        assertFalse(strict.isComplete());
        strict.step();
        assertEquals(4, strict.outputCount("out-a"));
        assertTrue(strict.isComplete());
    }

    @Test
    void planWithoutOutputsIsNeverComplete() {
        assertFalse(new OrthogonalSimulator(SimulationPlan.of()).isComplete());
    }

    // ---------- 抓取、释放与携带 ----------

    @Test
    void grabbedAtomsAreCarriedByMechanismMotion() {
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 1, 0, Direction.WEST, ".fgr")),
                List.of(track(1, 0, 0, 0)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);

        assertTrue(simulator.isHalted());
        assertEquals(4, simulator.cycle());
        assertEquals(new GridPoint(0, 0), simulator.mechanism("arm").position());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(-1, 0)));
        assertEquals(1, simulator.grabsAt(new GridPoint(-1, 0)));
        assertFalse(simulator.heldEntities("arm").isEmpty());
    }

    @Test
    void grabbedMoleculesRotateAroundTheMechanismWithTheirBonds() {
        Atom eastBonded = Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2);
        Atom westBonded = Atom.of((byte) 5).withBond(Direction.WEST, (byte) 2);
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 0, 1, Direction.NORTH, ".fd")),
                List.of(InputSpec.of(
                        "in",
                        new AtomPlacement(new GridPoint(0, 0), eastBonded),
                        new AtomPlacement(new GridPoint(1, 0), westBonded))),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertEquals(Direction.EAST, simulator.mechanism("arm").direction());
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
                List.of(mechanism("arm", 1, 0, Direction.WEST, "ff")),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1));

        simulator.run(3);

        // 周期 0 抓空、周期 1 抓取成功、周期 2 已经持有所以抓取是空操作。
        assertEquals(1, simulator.boardSize());
        assertEquals(1, simulator.grabsAt(new GridPoint(0, 0)));
        assertEquals(1, simulator.heldEntities("arm").size());
    }

    @Test
    void twoMechanismsMayHoldTheSameMolecule() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("a", 1, 0, Direction.WEST, ".f"),
                        mechanism("b", 0, 1, Direction.NORTH, ".f")),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(2);

        assertEquals(2, simulator.grabsAt(new GridPoint(0, 0)));
        assertEquals(1, simulator.heldEntities("a").size());
        assertEquals(1, simulator.heldEntities("b").size());
    }

    // ---------- 意图冲突与停机 ----------

    @Test
    void grabAndTranslationInTheSameCycleNoLongerConflict() {
        // GRAB 放在第一半周期、MOVE 放在第二半周期，因此“同一周期既抓取又搬运”不再是
        // 不兼容意图组合，而是先抓住、再搬运：一个周期里就能抓住并移动一格。
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 0, 0, Direction.EAST, "fg")),
                List.of(track(0, 0, 1, 0)),
                List.of(InputSpec.single("in", new GridPoint(1, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.init();
        // 第一个周期：指令 t 在起始格点上没有落点，机构原地不动，只发生抓取。
        simulator.step();
        assertFalse(simulator.isHalted());
        assertTrue(simulator.mechanism("arm").grabbing());
        assertEquals(new GridPoint(0, 0), simulator.mechanism("arm").position());

        // 第二个周期：指令 f 已经被抓住（空操作），指令 t 把机构与原子一起搬到轨道下一格。
        simulator.step();

        assertFalse(simulator.isHalted());
        assertTrue(simulator.warnings().isEmpty());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.mechanism("arm").grabbing());
        assertEquals(new GridPoint(1, 0), simulator.mechanism("arm").position());
        // 原子被搬到 (2,0)；原来的 (1,0) 空出来后被输入重新生产。
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));
        assertEquals(2, simulator.boardSize());
    }

    @Test
    void grabAndRotationInTheSameCycleFollowTheTwoHalfCycles() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("carrier", 1, 0, Direction.EAST, ".fd"),
                        mechanism("grabber", 3, 0, Direction.WEST, "..f")),
                List.of(InputSpec.single("in", new GridPoint(2, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);

        assertFalse(simulator.isHalted());
        assertTrue(simulator.warnings().isEmpty());
        // 第一半周期抓住 (2,0) 的原子，第二半周期携带它绕 (1,0) 顺时针转到 (1,1)。
        assertEquals(Direction.SOUTH, simulator.mechanism("carrier").direction());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 1)));
        // 下一周期起两个机构都持有同一个分子，因此抓取计数是 2；被搬运的原子已经转到 (1,1)。
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 1)));
        assertEquals(new GridPoint(1, 0), simulator.mechanism("carrier").position());
    }

    @Test
    void releaseIsCompatibleWithTranslation() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("dropper", 1, 0, Direction.WEST, ".f.r"),
                        mechanism("carrier", 0, 1, Direction.NORTH, ".f.g")),
                List.of(track(0, 1, 0, 0)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);

        assertFalse(simulator.isHalted());
        // 释放与平移合并：dropper 放手，carrier 继续把实体带到 (0, -1)。
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, -1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(0, -1)));
        assertTrue(simulator.heldEntities("dropper").isEmpty());
        assertEquals(1, simulator.heldEntities("carrier").size());
    }

    @Test
    void releaseIsCompatibleWithRotation() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("dropper", 1, 0, Direction.WEST, ".f.r"),
                        mechanism("carrier", 0, 1, Direction.NORTH, ".f.d")),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);

        assertFalse(simulator.isHalted());
        // 释放与旋转合并：carrier 右转，实体绕 (0,1) 顺时针转到 (1,1)。
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, 1)));
        assertTrue(simulator.heldEntities("dropper").isEmpty());
    }

    @Test
    void conflictingTranslationsHaltAndIdenticalOnesMerge() {
        SimulationPlan conflict = new SimulationPlan(
                List.of(
                        mechanism("west", 1, 0, Direction.WEST, ".fg"),
                        mechanism("south", 0, -1, Direction.SOUTH, ".ft")),
                List.of(track(1, 0, 0, 0, 0, -1)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);
        SimulationPlan merge = new SimulationPlan(
                List.of(
                        mechanism("west-a", 1, 0, Direction.WEST, ".fg"),
                        mechanism("west-b", 1, 0, Direction.WEST, ".fg")),
                List.of(track(1, 0, 0, 0)),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator conflicting = new OrthogonalSimulator(conflict);
        conflicting.run(3);
        assertTrue(conflicting.isHalted());
        assertEquals(3, conflicting.cycle());
        assertEquals(sampleAtom(), conflicting.atomAt(new GridPoint(0, 0)));

        OrthogonalSimulator merged = new OrthogonalSimulator(merge);
        merged.run(3);
        assertFalse(merged.isHalted());
        assertEquals(sampleAtom(), merged.atomAt(new GridPoint(-1, 0)));
        assertEquals(2, merged.grabsAt(new GridPoint(-1, 0)));
    }

    @Test
    void conflictingRotationsHaltAndIdenticalOnesMerge() {
        SimulationPlan conflict = new SimulationPlan(
                List.of(
                        mechanism("north", 0, 1, Direction.NORTH, ".fd"),
                        mechanism("south", 0, -1, Direction.SOUTH, ".fa")),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);
        SimulationPlan merge = new SimulationPlan(
                List.of(
                        mechanism("north-a", 0, 1, Direction.NORTH, ".fd"),
                        mechanism("north-b", 0, 1, Direction.NORTH, ".fd")),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator conflicting = new OrthogonalSimulator(conflict);
        conflicting.run(3);
        assertTrue(conflicting.isHalted());
        assertEquals(3, conflicting.cycle());
        assertEquals(sampleAtom(), conflicting.atomAt(new GridPoint(0, 0)));

        OrthogonalSimulator merged = new OrthogonalSimulator(merge);
        merged.run(3);
        assertFalse(merged.isHalted());
        // 相同中心、相同转向只旋转一次：(0,0) 绕 (0,1) 顺时针转到 (1,1)。
        assertEquals(sampleAtom(), merged.atomAt(new GridPoint(1, 1)));
        assertEquals(2, merged.grabsAt(new GridPoint(1, 1)));
    }

    @Test
    void atomsMovingOntoTheSameCellHaltInsteadOfOverwriting() {
        SimulationPlan plan = new SimulationPlan(
                List.of(
                        mechanism("east", 0, 0, Direction.EAST, ".fg"),
                        mechanism("west", 4, 0, Direction.WEST, ".fg")),
                List.of(
                        track(0, 0, 1, 0),
                        track(4, 0, 3, 0)),
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
    void atomMovingOntoAnOccupiedCellHaltsInsteadOfOverwriting() {
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 0, 0, Direction.EAST, ".fg")),
                List.of(track(0, 0, 1, 0)),
                List.of(
                        InputSpec.single("in-a", new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(2, 0), otherAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(3);

        assertTrue(simulator.isHalted());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(otherAtom(), simulator.atomAt(new GridPoint(2, 0)));
    }

    @Test
    void haltStopsAllFurtherStepsAndResetRecovers() {
        // 机构抓着 (1,0) 的原子沿轨道往东走，第二半周期撞上静止的 (2,0) 原子而停机。
        SimulationPlan plan = new SimulationPlan(
                List.of(mechanism("arm", 0, 0, Direction.EAST, ".fg")),
                List.of(track(0, 0, 1, 0)),
                List.of(
                        InputSpec.single("in-a", new GridPoint(1, 0), sampleAtom()),
                        InputSpec.single("in-b", new GridPoint(2, 0), otherAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.run(4);
        assertTrue(simulator.isHalted());
        long haltedCycle = simulator.cycle();

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
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.of(
                        "in",
                        new AtomPlacement(new GridPoint(0, 0), inputAtoms.get(0)),
                        new AtomPlacement(new GridPoint(1, 0), inputAtoms.get(1)))),
                List.of(OutputSpec.of(
                        "out",
                        new AtomPlacement(new GridPoint(0, 0), outputAtoms.get(0)),
                        new AtomPlacement(new GridPoint(1, 0), outputAtoms.get(1)))),
                1);
        return new OrthogonalSimulator(plan);
    }

    private static MechanismSpec mechanism(
            String id, long x, long y, Direction direction, String program) {
        return new MechanismSpec(
                id,
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
