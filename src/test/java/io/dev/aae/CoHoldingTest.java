package io.dev.aae;

import io.dev.aae.mech.*;

import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoHoldingTest {
    private static final GridPoint ORIGIN = new GridPoint(0, 0);
    private static final Atom ATOM = Atom.of(1);

    @ParameterizedTest
    @CsvSource({"f.,f.", "f.,.f"})
    void distinctBasesCanGrabTheSameMoleculeTogetherOrInSequence(String first, String second) {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, first),
                        arm(0, 1, Direction.NORTH, 1, second)), List.of(),
                InputSpec.single(ORIGIN, ATOM));

        simulator.run(2);

        assertFalse(simulator.isHalted());
        assertTrue(simulator.warnings().isEmpty());
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(simulator.heldEntities(0), simulator.heldEntities(1));
        assertEquals(1, simulator.heldEntities(0).size());
        assertEquals(ORIGIN, simulator.mechanism(0).grabPosition());
        assertEquals(ORIGIN, simulator.mechanism(1).grabPosition());
    }

    @Test
    void multipleContactsCountOncePerMechanismAndReleaseIndependently() {
        // 同一机构的两个接触点只贡献一次持有；另一机构独立贡献一次，逐个释放不得串改。
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, 0x11, "frrr"),
                        arm(0, 2, Direction.NORTH, 1, "f.rr")),
                List.of(),
                InputSpec.of(
                        new AtomPlacement(ORIGIN, ATOM.withBond(Direction.SOUTH, 2)),
                        AtomPlacement.of(0, 1, Atom.of(3).withBond(Direction.NORTH, 2)))
        );
        simulator.step();
        long entity = simulator.entityAt(ORIGIN);
        List<Long> expected = List.of(entity, simulator.entityAt(new GridPoint(0, 1)));
        List<Long> held = simulator.heldEntities(0);
        assertEquals(expected, held);
        assertEquals(expected, simulator.heldEntities(1));
        assertThrows(UnsupportedOperationException.class, held::clear);
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(new GridPoint(0, 1)));
        assertTrue(simulator.motionIntents(0).isEmpty());
        simulator.step();
        assertEquals(expected, held);
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertEquals(expected, simulator.heldEntities(1));
        assertEquals(1, simulator.grabsAt(ORIGIN));
        assertEquals(1, simulator.grabsAt(new GridPoint(0, 1)));
        simulator.run(2);
        assertFalse(simulator.isHalted());
        assertTrue(simulator.heldEntities(1).isEmpty());
        assertEquals(0, simulator.grabsAt(ORIGIN));
        assertEquals(0, simulator.grabsAt(new GridPoint(0, 1)));
        assertEquals(entity, simulator.entityAt(ORIGIN));
    }

    @Test
    void duplicateBranchBaseRotationsAreAppliedOnlyOnce() {
        // 连通双原子分子和远处旁观原子分别播种，排除补料后以键向识别重复施加的基座旋转。
        Atom top = ATOM.withBond(Direction.SOUTH, 2);
        Atom bottom = Atom.of(3).withBond(Direction.NORTH, 2);
        OrthogonalSimulator simulator = initialStateSimulator(
                List.of(arm(-1, 0, Direction.EAST, 1, 0x11, "fda")), List.of(),
                InputSpec.of(new AtomPlacement(ORIGIN, top), AtomPlacement.of(0, 1, bottom)),
                InputSpec.single(new GridPoint(10, 10), Atom.of(9)));
        simulator.step();
        long entity = simulator.entityAt(ORIGIN);
        long otherEntity = simulator.entityAt(new GridPoint(0, 1));
        assertEquals(List.of(entity, otherEntity), simulator.heldEntities(0));
        List<EntityIntent> rotations = simulator.motionIntents(0);
        assertEquals(List.of(EntityIntent.rotate(entity, 0, new GridPoint(-1, 0), 1),
                EntityIntent.rotate(otherEntity, 0, new GridPoint(-1, 0), 1)), rotations);
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(ATOM.withBond(Direction.WEST, 2), simulator.atomAt(new GridPoint(-1, 1)));
        assertEquals(Atom.of(3).withBond(Direction.EAST, 2), simulator.atomAt(new GridPoint(-2, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(-1, 1)));
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(top, simulator.atomAt(ORIGIN));
        assertEquals(bottom, simulator.atomAt(new GridPoint(0, 1)));
        assertEquals(entity, simulator.entityAt(ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "e"})
    void incompatibleBranchCentersOrDisplacementsRollBackTheWholeCycle(String instruction) {
        // 定长多臂抓住连通三原子分子；两抓点给出不同枢轴中心时，旁路机构也不得部分提交。
        Atom top = ATOM.withBond(Direction.SOUTH, 2);
        Atom bottom = Atom.of(3).withBond(Direction.NORTH, 2);
        OrthogonalSimulator simulator = initialStateSimulator(
                List.of(arm(-2, 0, Direction.EAST, 2, 0x11, "f" + instruction),
                        arm(10, 10, Direction.EAST, 1, "fd")), List.of(),
                InputSpec.of(new AtomPlacement(ORIGIN, top),
                        AtomPlacement.of(0, 1, Atom.of(4)
                                .withBond(Direction.NORTH, 2).withBond(Direction.SOUTH, 2)),
                        AtomPlacement.of(0, 2, bottom)),
                InputSpec.single(new GridPoint(11, 10), Atom.of(5)));
        simulator.step();
        List<MechanismView> before = simulator.mechanisms();
        List<Long> held = simulator.heldEntities(0);
        long entity = simulator.entityAt(ORIGIN);
        List<EntityIntent> intents = simulator.motionIntents(0);
        assertEquals(6, intents.size());
        List<EntityIntent> sameEntityIntents = intents.stream()
                .filter(intent -> intent.entity() == entity).toList();
        assertEquals(2, sameEntityIntents.size());
        assertFalse(sameEntityIntents.get(0).compatibleWith(sameEntityIntents.get(1)));
        simulator.step();
        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertEquals(before, simulator.mechanisms());
        assertEquals(held, simulator.heldEntities(0));
        assertEquals(top, simulator.atomAt(ORIGIN));
        assertEquals(bottom, simulator.atomAt(new GridPoint(0, 2)));
        assertEquals(Atom.of(5), simulator.atomAt(new GridPoint(11, 10)));
        assertNull(simulator.atomAt(new GridPoint(10, 11)));
        assertEquals(1, simulator.grabsAt(ORIGIN));
        assertEquals(1, simulator.grabsAt(new GridPoint(0, 2)));
        assertEquals(1, simulator.grabsAt(new GridPoint(11, 10)));
    }

    @ParameterizedTest
    @CsvSource({"WAIT,.,1", "GRAB,f,1", "ENDPOINT,g,1", "OFF_TRACK,g,1",
            "STATIONARY_EXTEND,w,2", "STATIONARY_RETRACT,s,2", "MAXIMUM,w,3", "MINIMUM,s,1"})
    void stationaryCoHolderBlocksPistonMotion(String scenario, String instruction, int stationaryLength) {
        // 定长臂在中间长度收到 w/s 仍提供 STAY；活塞自身的最大/最小长度边界也保持相同的共持约束。
        GridPoint stationaryBase = new GridPoint(0, stationaryLength);
        List<TrackSpec> tracks = scenario.equals("ENDPOINT")
                ? List.of(new TrackSpec(new GridPoint(-1, stationaryLength), stationaryBase))
                : List.of();
        MechanismSpec coHolder = scenario.equals("MAXIMUM") || scenario.equals("MINIMUM")
                ? piston(0, stationaryLength, Direction.NORTH, stationaryLength, "f" + instruction)
                : arm(0, stationaryLength, Direction.NORTH, stationaryLength, "f" + instruction);
        OrthogonalSimulator simulator = simulator(
                List.of(piston(-1, 0, Direction.EAST, 1, "fw"), coHolder),
                tracks, InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        assertFalse(simulator.isHalted());
        long entity = simulator.entityAt(ORIGIN);
        assertEquals(List.of(EntityIntent.stay(entity, 1)), simulator.motionIntents(1));

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertEquals(1, simulator.warnings().size());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertTrue(simulator.warnings().get(0).contains("from mechanism[0]"));
        assertTrue(simulator.warnings().get(0).contains("from mechanism[1]"));
        assertEquals(1, simulator.mechanism(0).length());
        assertEquals(stationaryLength, simulator.mechanism(1).length());
        assertEquals(stationaryBase, simulator.mechanism(1).position());
        assertEquals(entity, simulator.entityAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
    }

    @Test
    void stationaryTrackEndpointBlocksAnotherHoldersTranslation() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(0, 1, Direction.NORTH, 1, "fg"),
                        arm(-1, 0, Direction.EAST, 1, "fg")),
                List.of(TrackBuilder.start(-1, 1).east().noLoop(),
                        TrackBuilder.start(-1, 0).west().noLoop()),
                InputSpec.single(ORIGIN, ATOM));
        simulator.step();

        simulator.step();

        assertTrue(simulator.isHalted());
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertEquals(new GridPoint(-1, 0), simulator.mechanism(1).position());
        assertEquals(new GridPoint(0, 1), simulator.mechanism(0).position());
        assertEquals(ATOM, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    @Test
    void newlyJoinedHolderBlocksRotationAndItsGrabIsRolledBack() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(1, 0, Direction.EAST, 1, ".fd"),
                        arm(3, 0, Direction.WEST, 1, "..f")), List.of(),
                InputSpec.single(new GridPoint(2, 0), ATOM));
        simulator.run(2);
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(3, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertEquals(Direction.EAST, simulator.mechanism(0).direction());
        assertEquals(ATOM, simulator.atomAt(new GridPoint(2, 0)));
        assertNull(simulator.atomAt(new GridPoint(1, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));
        assertTrue(simulator.heldEntities(1).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"g,-1,0", "d,1,1"})
    void releasingCoHolderAllowsTranslationOrRotation(String motion, long x, long y) {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(1, 0, Direction.WEST, 1, "fr"),
                        arm(0, 1, Direction.NORTH, 1, "f" + motion)),
                List.of(TrackBuilder.start(0, 1).west().noLoop()),
                InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        long entity = simulator.entityAt(ORIGIN);

        simulator.step();

        GridPoint destination = new GridPoint(x, y);
        assertFalse(simulator.isHalted());
        assertEquals(entity, simulator.entityAt(destination));
        assertEquals(ATOM, simulator.atomAt(destination));
        assertEquals(destination, simulator.mechanism(1).grabPosition());
        assertEquals(1, simulator.grabsAt(destination));
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertEquals(List.of(entity), simulator.heldEntities(1));
    }

    @Test
    void identicalTranslationsMoveACoHeldMoleculeOnlyOnce() {
        // 第一机构两支接触同一分子，第二机构再共持；重复同位移约束仍只能移动一次。
        Atom left = ATOM.withBond(Direction.EAST, 2);
        Atom right = Atom.of(3).withBond(Direction.WEST, 2);
        OrthogonalSimulator simulator = simulator(
                List.of(arm(0, 1, Direction.NORTH, 1, 0x11, "fg"),
                        arm(1, -1, Direction.SOUTH, 1, "fg")),
                List.of(
                        TrackBuilder.start(0, 1).west().noLoop(),
                        TrackBuilder.start(1, -1).west().noLoop()),
                InputSpec.of(new AtomPlacement(ORIGIN, left),
                        AtomPlacement.of(1, 0, right)));

        simulator.step();
        assertEquals(List.of(simulator.entityAt(ORIGIN), simulator.entityAt(new GridPoint(1, 0))),
                simulator.heldEntities(0));
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(2, simulator.motionIntents(0).size());
        simulator.step();

        assertFalse(simulator.isHalted());
        assertEquals(new GridPoint(-1, 1), simulator.mechanism(0).position());
        assertEquals(new GridPoint(0, -1), simulator.mechanism(1).position());
        assertEquals(left, simulator.atomAt(new GridPoint(-1, 0)));
        assertEquals(right, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.boardSize());
        assertEquals(2, simulator.grabsAt(new GridPoint(-1, 0)));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    @Test
    void identicalPivotsFromDistinctBasesRotateTheMoleculeOnlyOnce() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, "fe"),
                        arm(0, 1, Direction.NORTH, 1, "fe")),
                List.of(),
                InputSpec.of(
                        new AtomPlacement(ORIGIN, ATOM.withBond(Direction.NORTH, 2)),
                        AtomPlacement.of(0, -1, Atom.of(3).withBond(Direction.SOUTH, 2)))
        );

        simulator.run(2);

        assertFalse(simulator.isHalted());
        assertEquals(ATOM.withBond(Direction.EAST, 2), simulator.atomAt(ORIGIN));
        assertEquals(Atom.of(3).withBond(Direction.WEST, 2),
                simulator.atomAt(new GridPoint(1, 0)));
        assertNull(simulator.atomAt(new GridPoint(0, -1)));
        assertEquals(2, simulator.boardSize());
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));
        assertEquals(ORIGIN, simulator.mechanism(0).grabPosition());
        assertEquals(ORIGIN, simulator.mechanism(1).grabPosition());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incompatibleTranslationsOrRotationsRollBackBothHolders(boolean rotation) {
        List<MechanismSpec> arms = rotation
                ? List.of(arm(0, 1, Direction.NORTH, 1, "fd"),
                        arm(0, -1, Direction.SOUTH, 1, "fa"))
                : List.of(arm(1, 0, Direction.WEST, 1, "fg"),
                        arm(0, -1, Direction.SOUTH, 1, "ft"));
        List<TrackSpec> tracks = rotation ? List.of()
                : List.of(TrackBuilder.start(1, 0).north().west().noLoop());
        OrthogonalSimulator simulator = simulator(arms, tracks, InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        List<MechanismView> before = simulator.mechanisms();

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertEquals(before, simulator.mechanisms());
        assertEquals(ATOM, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    @SuppressWarnings("unchecked")
    private static OrthogonalSimulator initialStateSimulator(
            List<MechanismSpec> arms, List<TrackSpec> tracks, InputSpec... molecules) {
        // 分子参数均通过生产校验；这里只播种一次性棋盘初态，不声明会自动补料的生产 I/O。
        // 同步双向实体索引与后续编号，连通关系直接从当前棋盘读取，保持共持与回滚的运行态语义。
        new SimulationPlan(arms, tracks, List.of(molecules), List.of(), 1);
        OrthogonalSimulator simulator = TestInventory.simulator(new SimulationPlan(arms, tracks));
        simulator.init();
        try {
            var boardField = OrthogonalSimulator.class.getDeclaredField("board");
            boardField.setAccessible(true);
            AtomGrid board = (AtomGrid) boardField.get(simulator);
            var entitiesField = OrthogonalSimulator.class.getDeclaredField("entityAtPosition");
            entitiesField.setAccessible(true);
            Map<GridPoint, Long> entities = (Map<GridPoint, Long>) entitiesField.get(simulator);
            var positionsField = OrthogonalSimulator.class.getDeclaredField("positionOfEntity");
            positionsField.setAccessible(true);
            Map<Long, GridPoint> positions = (Map<Long, GridPoint>) positionsField.get(simulator);
            long nextId = 1;
            for (InputSpec molecule : molecules) {
                for (AtomPlacement placement : molecule.atoms()) {
                    assertNull(board.get(placement.position()), "初态分子不得重叠");
                    board.put(placement.position(), placement.atom());
                    entities.put(placement.position(), nextId);
                    positions.put(nextId++, placement.position());
                }
            }
            var nextIdField = OrthogonalSimulator.class.getDeclaredField("nextEntityId");
            nextIdField.setAccessible(true);
            nextIdField.setLong(simulator, nextId);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("无法建立共持测试初态", failure);
        }
        return simulator;
    }

    private static OrthogonalSimulator simulator(
            List<MechanismSpec> arms, List<TrackSpec> tracks, InputSpec input) {
        OrthogonalSimulator simulator = TestInventory.simulator(
                new SimulationPlan(arms, tracks, List.of(input), List.of(), 1));
        simulator.init();
        return simulator;
    }

    private static MechanismSpec arm(long x, long y, Direction direction, int length, String program) {
        // 定长机构的类型由场景明确指定，不根据指令字符串推断是否为活塞。
        return new StationarySpec(new GridPoint(x, y), direction, length, InstructionTape.once(program));
    }

    private static MechanismSpec piston(long x, long y, Direction direction, int length, String program) {
        // 显式选择可伸缩单臂，使共持运动者与固定臂长约束分开建模。
        return new PistonSpec(new GridPoint(x, y), direction, length, InstructionTape.once(program));
    }

    private static MechanismSpec arm(
            long x, long y, Direction direction, int length, int code, String program) {
        // 显式组合编码只用于多分支场景，其余计划参数继续沿用现有单次指令带约定。
        return new StationarySpec(new GridPoint(x, y), direction, length,
                new ArmCombination(code), InstructionTape.once(program));
    }
}
