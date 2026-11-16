package io.dev.aae;

import io.dev.aae.mech.*;

import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MechanismMotionTest {

    @Test
    void mechanismSubtypesValidateLengthsAndKeepExplicitCapabilities() {
        // 封闭继承表达能力边界：静态臂拥有组合，活塞只有默认单臂，两者共享不可变公共定义。
        assertTrue(java.lang.reflect.Modifier.isPublic(MechanismSpec.class.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isAbstract(MechanismSpec.class.getModifiers()));
        assertTrue(MechanismSpec.class.isSealed());
        assertEquals(java.util.Set.of(StationarySpec.class, PistonSpec.class),
                java.util.Set.of(MechanismSpec.class.getPermittedSubclasses()));
        assertTrue(java.lang.reflect.Modifier.isFinal(StationarySpec.class.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(PistonSpec.class.getModifiers()));
        assertFalse(java.util.Arrays.stream(MechanismSpec.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == ArmCombination.class));
        assertFalse(java.util.Arrays.stream(PistonSpec.class.getConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type == ArmCombination.class));

        GridPoint origin = new GridPoint(0, 0);
        InstructionTape tape = InstructionTape.empty();
        MechanismSpec stationary = new StationarySpec(origin, Direction.NORTH, tape);
        MechanismSpec piston = new PistonSpec(origin, Direction.NORTH, tape);
        MechanismView view = new MechanismView(origin, Direction.NORTH, false);
        for (MechanismSpec spec : List.of(stationary, piston)) {
            assertEquals(origin, spec.initialPosition());
            assertEquals(Direction.NORTH, spec.initialDirection());
            assertEquals(1, spec.length());
            assertEquals(1, spec.initialLength());
            assertEquals(tape, spec.tape());
            assertEquals(ArmCombination.DEFAULT, spec.combination());
            assertThrows(NullPointerException.class, () -> spec.supportsInstruction(null));
            assertThrows(IllegalArgumentException.class, () -> spec.instructionAt(-1));
        }
        assertEquals(1, view.length());
        assertEquals(ArmCombination.DEFAULT, view.combination());
        assertEquals(1, ArmCombination.DEFAULT.code());
        for (Instruction instruction : Instruction.values()) {
            boolean isPistonInstruction = instruction == Instruction.EXTEND_PISTON
                    || instruction == Instruction.RETRACT_PISTON;
            assertEquals(!isPistonInstruction, stationary.supportsInstruction(instruction));
            assertTrue(piston.supportsInstruction(instruction));
        }

        // 原有定长构造形式归静态臂；活塞的显式长度和字符串入口不接受组合。
        ArmCombination diagonal = new ArmCombination(0x80);
        assertEquals(1, new StationarySpec(origin, Direction.NORTH, diagonal, tape).length());
        assertEquals(1, new StationarySpec(origin, Direction.NORTH, diagonal, "f").length());
        assertEquals(diagonal, new StationarySpec(origin, Direction.NORTH, 0x80, "f").combination());
        assertEquals(2, new StationarySpec(origin, Direction.NORTH, tape, 2).length());
        assertEquals(2, new PistonSpec(origin, Direction.NORTH, tape, 2).length());
        assertEquals(2, new PistonSpec(origin, Direction.NORTH, "f", 2).length());
        for (MechanismSpec stringSpec : List.of(
                new StationarySpec(origin, Direction.NORTH, 2, diagonal, "f"),
                new StationarySpec(origin, Direction.NORTH, "f", 2),
                new StationarySpec(origin, Direction.NORTH, "f"),
                new PistonSpec(origin, Direction.NORTH, 2, "f"),
                new PistonSpec(origin, Direction.NORTH, "f"))) {
            assertEquals(InstructionTape.Playback.LOOPING, stringSpec.tape().playback());
            assertEquals(Instruction.GRAB, stringSpec.instructionAt(0));
            assertEquals(Instruction.GRAB, stringSpec.instructionAt(1));
        }
        assertEquals(new GridPoint(-2, -2),
                new MechanismView(origin, Direction.NORTH, 2, diagonal, false).grabPosition());
        assertEquals(3, new MechanismView(origin, Direction.NORTH, true, 3).length());

        for (int invalidLength : new int[] {0, 4}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StationarySpec(origin, Direction.NORTH, invalidLength, tape));
            assertThrows(IllegalArgumentException.class,
                    () -> new PistonSpec(origin, Direction.NORTH, invalidLength, tape));
            assertThrows(IllegalArgumentException.class,
                    () -> new MechanismView(origin, Direction.NORTH, invalidLength, false));
        }
        assertThrows(NullPointerException.class, () -> new StationarySpec(null, Direction.NORTH, tape));
        assertThrows(NullPointerException.class, () -> new PistonSpec(null, Direction.NORTH, tape));
        assertThrows(NullPointerException.class, () -> new StationarySpec(origin, null, tape));
        assertThrows(NullPointerException.class, () -> new PistonSpec(origin, null, tape));
        assertThrows(NullPointerException.class,
                () -> new StationarySpec(origin, Direction.NORTH, (InstructionTape) null));
        assertThrows(NullPointerException.class,
                () -> new PistonSpec(origin, Direction.NORTH, (InstructionTape) null));
        assertThrows(NullPointerException.class,
                () -> new StationarySpec(origin, Direction.NORTH, (String) null));
        assertThrows(NullPointerException.class,
                () -> new PistonSpec(origin, Direction.NORTH, (String) null));
        assertThrows(NullPointerException.class,
                () -> new StationarySpec(origin, Direction.NORTH, (ArmCombination) null, tape));

        // 不支持的指令只映射为等待，绝不删除周期或改变单次/循环播放的起点与节拍。
        InstructionTape delayed = InstructionTape.parse("wsf", 4, InstructionTape.Playback.ONCE);
        MechanismSpec delayedStationary = new StationarySpec(origin, Direction.NORTH, delayed);
        MechanismSpec delayedPiston = new PistonSpec(origin, Direction.NORTH, delayed);
        assertEquals(Instruction.WAIT, delayedStationary.instructionAt(3));
        assertEquals(Instruction.EXTEND_PISTON, delayedStationary.tape().instructionAt(4));
        assertEquals(Instruction.WAIT, delayedStationary.instructionAt(4));
        assertEquals(Instruction.WAIT, delayedStationary.instructionAt(5));
        assertEquals(Instruction.GRAB, delayedStationary.instructionAt(6));
        assertEquals(Instruction.WAIT, delayedStationary.instructionAt(7));
        assertEquals(Instruction.EXTEND_PISTON, delayedPiston.instructionAt(4));
        assertEquals(Instruction.RETRACT_PISTON, delayedPiston.instructionAt(5));
        MechanismSpec looping = new StationarySpec(origin, Direction.NORTH,
                InstructionTape.parse("wsf", 2, InstructionTape.Playback.LOOPING));
        assertEquals(Instruction.WAIT, looping.instructionAt(1));
        assertEquals(Instruction.WAIT, looping.instructionAt(2));
        assertEquals(Instruction.GRAB, looping.instructionAt(4));
        assertEquals(Instruction.WAIT, looping.instructionAt(5));
        assertEquals(Instruction.GRAB, looping.instructionAt(7));

        // 由 record 迁为基类后保留值语义，诊断名称随具体类型更新；相同状态的不同机构类型不相等。
        assertEquals(stationary, new StationarySpec(origin, Direction.NORTH, tape));
        assertEquals(stationary.hashCode(), new StationarySpec(origin, Direction.NORTH, tape).hashCode());
        assertEquals(piston, new PistonSpec(origin, Direction.NORTH, tape));
        assertEquals(piston.hashCode(), new PistonSpec(origin, Direction.NORTH, tape).hashCode());
        assertNotEquals(stationary, piston);
        assertNotEquals(piston, stationary);
        assertNotEquals(stationary, new StationarySpec(origin, Direction.NORTH, diagonal, tape));
        assertNotEquals(stationary, new StationarySpec(origin, Direction.NORTH, 2, tape));
        assertNotEquals(stationary, new StationarySpec(origin, Direction.EAST, tape));
        assertNotEquals(stationary, new StationarySpec(new GridPoint(1, 0), Direction.NORTH, tape));
        assertNotEquals(stationary, new StationarySpec(origin, Direction.NORTH, InstructionTape.once("f")));
        assertTrue(stationary.toString().startsWith("StationarySpec["));
        assertTrue(piston.toString().startsWith("PistonSpec["));
    }

    @Test
    void combinationCodesAndHandWrittenCoordinatesPreserveRealBitIndices() {
        // 只选边界编码和手写几何样本，避免用生产坐标转换计算期望值而掩盖同源错误。
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ArmCombination(invalid));
        }
        assertEquals(new ArmCombination(127), ArmCombination.fromByte((byte) 0x7f));
        assertEquals(new ArmCombination(128), ArmCombination.fromByte((byte) 0x80));
        assertEquals(new ArmCombination(255), ArmCombination.fromByte((byte) 0xff));
        assertEquals(ArmCombination.DEFAULT, ArmCombination.fromByte((byte) 1));
        assertThrows(IllegalArgumentException.class, () -> ArmCombination.fromByte((byte) 0));
        assertEquals(8, new ArmCombination(255).armCount());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), new ArmCombination(255).enabledBits());
        ArmCombination branches = new ArmCombination(0x91);
        assertEquals(3, branches.armCount());
        assertEquals(List.of(0, 4, 7), branches.enabledBits());
        assertThrows(UnsupportedOperationException.class, () -> branches.enabledBits().add(2));
        assertEquals(new GridPoint(1, 0), branches.localOffset(0));
        assertEquals(new GridPoint(1, 1), branches.localOffset(4));
        assertEquals(new GridPoint(1, -1), branches.localOffset(7));
        assertEquals(new GridPoint(-1, 1), new ArmCombination(0x20).localOffset(5));
        assertEquals(new GridPoint(-1, -1), new ArmCombination(0x40).localOffset(6));
        assertEquals(new GridPoint(1, 1), branches.worldOffset(Direction.SOUTH, 7));
        assertEquals(new GridPoint(-1, -1), branches.worldOffset(Direction.WEST, 4));
        assertEquals(new GridPoint(1, -1), branches.worldOffset(Direction.NORTH, 4));
        GridPoint base = new GridPoint(10, -7);
        assertEquals(List.of(new GridPoint(12, -7), new GridPoint(12, -5), new GridPoint(12, -9)),
                branches.enabledGrabPositions(base, Direction.EAST, 2));
        assertEquals(List.of(new GridPoint(10, -4), new GridPoint(7, -4), new GridPoint(13, -4)),
                branches.enabledGrabPositions(base, Direction.SOUTH, 3));
        assertEquals(new GridPoint(11, -8), branches.grabPosition(base, Direction.NORTH, 1, 4));
        assertEquals(List.of(new GridPoint(10, -8), new GridPoint(11, -7), new GridPoint(10, -6)),
                new ArmCombination(0x0e).enabledGrabPositions(base, Direction.WEST, 1));
        // 数组 API 必须保留真实 bit 下标，并且只旋转一次；手写四朝向坐标避免同源几何错误。
        Direction[] facings = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        GridPoint[][] expectedOffsets = {
                {new GridPoint(1, 0), new GridPoint(1, 1), new GridPoint(1, -1)},
                {new GridPoint(0, 1), new GridPoint(-1, 1), new GridPoint(1, 1)},
                {new GridPoint(-1, 0), new GridPoint(-1, -1), new GridPoint(-1, 1)},
                {new GridPoint(0, -1), new GridPoint(1, -1), new GridPoint(-1, -1)}};
        GridPoint[][] expectedGrabs = {
                {new GridPoint(12, -7), new GridPoint(12, -5), new GridPoint(12, -9)},
                {new GridPoint(10, -5), new GridPoint(8, -5), new GridPoint(12, -5)},
                {new GridPoint(8, -7), new GridPoint(8, -9), new GridPoint(8, -5)},
                {new GridPoint(10, -9), new GridPoint(12, -9), new GridPoint(8, -9)}};
        for (int facing = 0; facing < facings.length; facing++) {
            GridPoint[] offsets = branches.worldOffset(facings[facing]);
            GridPoint[] grabs = branches.grabPosArray(base, facings[facing], 2);
            assertEquals(8, offsets.length);
            assertEquals(8, grabs.length);
            int enabled = 0;
            for (int bit = 0; bit < 8; bit++) {
                if ((branches.code() & (1 << bit)) == 0) {
                    assertNull(offsets[bit]);
                    assertNull(grabs[bit]);
                } else {
                    assertEquals(expectedOffsets[facing][enabled], offsets[bit]);
                    assertEquals(expectedGrabs[facing][enabled++], grabs[bit]);
                }
            }
            MechanismView view = new MechanismView(base, facings[facing], 2, branches, false);
            org.junit.jupiter.api.Assertions.assertArrayEquals(grabs, view.grabPosArray());
            org.junit.jupiter.api.Assertions.assertArrayEquals(offsets, view.worldOffset());
            assertEquals(List.of(expectedGrabs[facing]), view.grabPositions());
        }
        for (int invalidLength : new int[] {0, 4}) {
            assertThrows(IllegalArgumentException.class,
                    () -> branches.grabPosArray(base, Direction.NORTH, invalidLength));
        }
        assertThrows(ArithmeticException.class, () -> branches.grabPosArray(
                new GridPoint(Long.MAX_VALUE, 0), Direction.EAST, 1));
        assertThrows(IllegalArgumentException.class, () -> branches.localOffset(1));
        assertThrows(IndexOutOfBoundsException.class, () -> branches.localOffset(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> branches.localOffset(8));
        assertThrows(IllegalArgumentException.class,
                () -> branches.grabPosition(base, Direction.EAST, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> branches.grabPosition(base, Direction.EAST, 4, 0));
        assertThrows(ArithmeticException.class, () -> branches.grabPosition(
                new GridPoint(Long.MAX_VALUE, 0), Direction.EAST, 1, 0));
    }

    @Test
    void pistonLengthChangesWithinBoundsAndResetRestoresIt() {
        // 同一指令带显式区分类型：活塞在上下界截断，静态臂在中间长度也忽略伸缩且不压缩周期。
        InstructionTape tape = InstructionTape.once("wwsssd");
        OrthogonalSimulator piston = simulator(new PistonSpec(
                new GridPoint(4, 5), Direction.WEST, 2, tape));
        OrthogonalSimulator stationary = simulator(new StationarySpec(
                new GridPoint(4, 5), Direction.WEST, 2, tape));

        for (int expectedLength : new int[] {3, 3, 2, 1, 1}) {
            piston.step();
            stationary.step();
            assertMechanism(piston, expectedLength, new GridPoint(4, 5), Direction.WEST);
            assertMechanism(stationary, 2, new GridPoint(4, 5), Direction.WEST);
            assertFalse(stationary.isHalted());
        }
        assertEquals(5, stationary.cycle());
        piston.step();
        stationary.step();
        assertMechanism(piston, 1, new GridPoint(4, 5), Direction.NORTH);
        assertMechanism(stationary, 2, new GridPoint(4, 5), Direction.NORTH);

        piston.reset();
        stationary.reset();
        assertMechanism(piston, 2, new GridPoint(4, 5), Direction.WEST);
        assertMechanism(stationary, 2, new GridPoint(4, 5), Direction.WEST);
    }

    @Test
    void mechanismQueriesAndChecksumsIncludeLength() {
        // 查询同时保留长度和真实组合；改变组合但保持其他状态一致时，校验和必须区分。
        MechanismSpec extended = new StationarySpec(
                new GridPoint(0, 0),
                Direction.NORTH,
                2,
                InstructionTape.empty());
        OrthogonalSimulator simulator = simulator(extended);
        OrthogonalSimulator legacyLength = simulator(new StationarySpec(
                new GridPoint(0, 0), Direction.NORTH, InstructionTape.empty()));

        assertEquals(2, simulator.mechanism(0).length());
        assertEquals(2, simulator.mechanisms().get(0).length());
        assertNotEquals(legacyLength.checksum(), simulator.checksum());
        ArmCombination combination = new ArmCombination(0x81);
        OrthogonalSimulator combined = simulator(new StationarySpec(
                new GridPoint(0, 0), Direction.NORTH, 2, combination, InstructionTape.empty()));
        assertEquals(combination, combined.mechanism(0).combination());
        assertEquals(combination, combined.mechanisms().get(0).combination());
        assertEquals(List.of(new GridPoint(0, -2), new GridPoint(-2, -2)),
                combined.mechanism(0).grabPositions());
        assertEquals(new GridPoint(-2, -2), combined.mechanism(0).grabPosition(7));
        assertNotEquals(simulator.checksum(), combined.checksum());
        assertThrows(IllegalStateException.class, () -> combined.mechanism(0).grabPosition());
        assertThrows(IllegalArgumentException.class, () -> combined.mechanism(0).grabPosition(1));
        assertThrows(IndexOutOfBoundsException.class, () -> combined.mechanism(0).grabPosition(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> combined.mechanism(0).grabPosition(8));
        assertThrows(UnsupportedOperationException.class,
                () -> combined.mechanism(0).grabPositions().clear());
    }

    @Test
    void pistonExtensionAndRetractionCarryTheGrabbedMolecule() {
        // 单臂活塞抓住连通双原子分子，两实体必须整体沿同一轴向伸缩；预览与实际位移一致。
        Atom grabbed = sampleAtom().withBond(Direction.SOUTH, 2);
        Atom attached = blockerAtom().withBond(Direction.NORTH, 2);
        MechanismSpec piston = new PistonSpec(
                new GridPoint(0, 0), Direction.EAST, 2, InstructionTape.once("fws"));
        OrthogonalSimulator simulator = motionSimulator(List.of(piston), List.of(),
                InputSpec.of(AtomPlacement.of(2, 0, grabbed), AtomPlacement.of(2, 1, attached)));

        simulator.step();
        assertTrue(simulator.mechanism(0).grabbing());
        List<Long> held = simulator.heldEntities(0);
        assertEquals(2, held.size());
        assertEquals(java.util.Set.of(
                        EntityIntent.move(simulator.entityAt(new GridPoint(2, 0)), 0, 1, 0),
                        EntityIntent.move(simulator.entityAt(new GridPoint(2, 1)), 0, 1, 0)),
                java.util.Set.copyOf(simulator.motionIntents(0)));

        simulator.step();
        assertEquals(3, simulator.mechanism(0).length());
        assertNull(simulator.atomAt(new GridPoint(2, 0)));
        assertNull(simulator.atomAt(new GridPoint(2, 1)));
        assertEquals(grabbed, simulator.atomAt(new GridPoint(3, 0)));
        assertEquals(attached, simulator.atomAt(new GridPoint(3, 1)));
        assertEquals(held, simulator.heldEntities(0));
        assertEquals(java.util.Set.of(
                        EntityIntent.move(simulator.entityAt(new GridPoint(3, 0)), 0, -1, 0),
                        EntityIntent.move(simulator.entityAt(new GridPoint(3, 1)), 0, -1, 0)),
                java.util.Set.copyOf(simulator.motionIntents(0)));

        simulator.step();
        assertEquals(2, simulator.mechanism(0).length());
        assertEquals(grabbed, simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(attached, simulator.atomAt(new GridPoint(2, 1)));
        assertNull(simulator.atomAt(new GridPoint(3, 0)));
        assertNull(simulator.atomAt(new GridPoint(3, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 1)));
        assertEquals(held, simulator.heldEntities(0));
        assertFalse(simulator.isHalted());

        // 多臂能力归静态机械；长度2并未触及边界，两条伸缩指令也只能提交静止约束。
        MechanismSpec stationary = new StationarySpec(new GridPoint(0, 0), Direction.EAST,
                2, new ArmCombination(0x11), InstructionTape.once("fwsr"));
        OrthogonalSimulator fixed = motionSimulator(List.of(stationary), List.of(),
                InputSpec.single(new GridPoint(2, 0), sampleAtom()),
                InputSpec.single(new GridPoint(2, 2), blockerAtom()));
        fixed.step();
        List<Long> fixedHeld = fixed.heldEntities(0);
        for (int ignoredInstruction = 0; ignoredInstruction < 2; ignoredInstruction++) {
            long checksum = fixed.checksum();
            assertEquals(2, fixed.motionIntents(0).size());
            assertTrue(fixed.motionIntents(0).stream()
                    .allMatch(intent -> intent.kind() == EntityIntent.Kind.STAY));
            assertEquals(checksum, fixed.checksum());
            fixed.step();
            assertEquals(2, fixed.mechanism(0).length());
            assertEquals(sampleAtom(), fixed.atomAt(new GridPoint(2, 0)));
            assertEquals(blockerAtom(), fixed.atomAt(new GridPoint(2, 2)));
            assertEquals(1, fixed.grabsAt(new GridPoint(2, 0)));
            assertEquals(1, fixed.grabsAt(new GridPoint(2, 2)));
            assertEquals(fixedHeld, fixed.heldEntities(0));
            assertFalse(fixed.isHalted());
        }
        assertEquals(3, fixed.cycle());
        assertTrue(fixed.mechanism(0).grabbing());
        assertTrue(fixed.motionIntents(0).isEmpty());
        fixed.step();
        assertTrue(fixed.heldEntities(0).isEmpty());
        assertEquals(0, fixed.grabsAt(new GridPoint(2, 0)));
        assertEquals(0, fixed.grabsAt(new GridPoint(2, 2)));
    }

    @Test
    void baseRotationCarriesEveryBranchAroundOneCommonCenter() {
        // a/d 的共同旋转中心是基座，与 q/e 的逐分支抓点中心不同；往返旋转后应还原。
        MechanismSpec arm = new StationarySpec(new GridPoint(0, 0), Direction.EAST, 2,
                new ArmCombination(0x11), InstructionTape.once("fda"));
        OrthogonalSimulator simulator = motionSimulator(List.of(arm), List.of(),
                InputSpec.single(new GridPoint(2, 0), sampleAtom()),
                InputSpec.single(new GridPoint(2, 2), blockerAtom()),
                InputSpec.single(new GridPoint(10, 10), Atom.of(9)));
        simulator.run(2);
        assertFalse(simulator.isHalted());
        assertEquals(Direction.SOUTH, simulator.mechanism(0).direction());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 2)));
        assertEquals(blockerAtom(), simulator.atomAt(new GridPoint(-2, 2)));
        assertNull(simulator.atomAt(new GridPoint(2, 0)));
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(Direction.EAST, simulator.mechanism(0).direction());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(blockerAtom(), simulator.atomAt(new GridPoint(2, 2)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 2)));
    }

    @Test
    void motionIntentQueriesAreImmutableSideEffectFreeSnapshotsOfTheNextInstruction() {
        // 定长多臂以轨道平移验证共享运动，再验证各抓点的枢轴中心；读接口不抓取、不推进。
        MechanismSpec arm = new StationarySpec(new GridPoint(0, 0), Direction.EAST, 1,
                new ArmCombination(0x11), InstructionTape.once("f.gger"));
        TrackSpec track = TrackBuilder.start(0, 0).north().north().noLoop();
        OrthogonalSimulator simulator = motionSimulator(List.of(arm), List.of(track),
                InputSpec.single(new GridPoint(1, 0), sampleAtom()),
                InputSpec.single(new GridPoint(1, 1), blockerAtom()),
                InputSpec.single(new GridPoint(10, 10), Atom.of(9)));
        assertTrue(simulator.motionIntents(0).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> simulator.motionIntents(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> simulator.motionIntents(1));
        simulator.step();
        long checksum = simulator.checksum();
        List<EntityIntent> stays = simulator.motionIntents(0);
        assertEquals(2, stays.size());
        assertTrue(stays.stream().allMatch(intent -> intent.kind() == EntityIntent.Kind.STAY));
        assertEquals(stays, simulator.motionIntents(0));
        assertEquals(checksum, simulator.checksum());
        assertEquals(1, simulator.cycle());
        assertEquals(1, simulator.mechanism(0).length());
        assertThrows(UnsupportedOperationException.class, stays::clear);
        simulator.step();
        List<EntityIntent> moves = simulator.motionIntents(0);
        assertEquals(List.of(EntityIntent.move(simulator.entityAt(new GridPoint(1, 0)), 0, 0, -1),
                        EntityIntent.move(simulator.entityAt(new GridPoint(1, 1)), 0, 0, -1)), moves);
        assertTrue(stays.stream().allMatch(intent -> intent.kind() == EntityIntent.Kind.STAY));
        assertThrows(UnsupportedOperationException.class, moves::clear);
        simulator.run(2);
        List<EntityIntent> rotations = simulator.motionIntents(0);
        assertEquals(List.of(EntityIntent.rotate(simulator.entityAt(new GridPoint(1, -2)), 0,
                                new GridPoint(1, -2), 1),
                        EntityIntent.rotate(simulator.entityAt(new GridPoint(1, -1)), 0,
                                new GridPoint(1, -1), 1)), rotations);
        assertEquals(1, simulator.mechanism(0).length());
        simulator.step();
        assertTrue(simulator.motionIntents(0).isEmpty());
        assertEquals(2, simulator.heldEntities(0).size());
        simulator.step();
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertTrue(simulator.motionIntents(0).isEmpty());
        assertEquals(0, simulator.grabsAt(new GridPoint(1, -1)));
        assertFalse(simulator.isHalted());
    }

    @Test
    void mixedTrackTurnsCarryTheGrabbedMoleculeByTheSameOffset() {
        // 基座走正交再走对角轨道；不同分支的分子都只接受相同平移，不随轨道拐弯转向。
        Atom grabbed = sampleAtom().withBond(Direction.WEST, 2);
        Atom attached = blockerAtom().withBond(Direction.EAST, 2);
        MechanismSpec arm = new StationarySpec(
                new GridPoint(0, 1),
                Direction.NORTH,
                new ArmCombination(0x11),
                InstructionTape.once("fgg"));
        // (0,1) >> (1,1) >> (2,0)
        TrackSpec track = TrackBuilder.start(0, 1).east().eastNorth().noLoop();
        OrthogonalSimulator simulator = motionSimulator(List.of(arm), List.of(track),
                InputSpec.of(AtomPlacement.of(0, 0, grabbed), AtomPlacement.of(-1, 0, attached)),
                InputSpec.single(new GridPoint(1, 0), Atom.of(9)));

        simulator.run(2);
        assertEquals(new GridPoint(1, 1), simulator.mechanism(0).position());
        assertEquals(grabbed, simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(attached, simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(Direction.NORTH, simulator.mechanism(0).direction());
        simulator.step();

        assertEquals(new GridPoint(2, 0), simulator.mechanism(0).position());
        assertEquals(Direction.NORTH, simulator.mechanism(0).direction());
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(2, -1)).bondType(Direction.WEST));
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(1, -1)).bondType(Direction.EAST));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, -1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(1, -1)));
        assertEquals(Atom.of(9), simulator.atomAt(new GridPoint(3, -1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(3, -1)));
        assertEquals(3, simulator.heldEntities(0).size());
        assertFalse(simulator.isHalted());
    }

    @Test
    void overflowingHeldAtomTrackMotionDoesNotPartiallyCommit() {
        // 一支移动仍合法、另一支越界时，也不能提交混合轨道位移或任一分子的局部结果。
        GridPoint base = new GridPoint(Long.MAX_VALUE - 1, 1);
        GridPoint atomPosition = new GridPoint(Long.MAX_VALUE, 1);
        GridPoint otherPosition = new GridPoint(Long.MAX_VALUE - 2, 1);
        MechanismSpec arm = new StationarySpec(
                base, Direction.EAST, new ArmCombination(0x05), InstructionTape.once("fg"));
        TrackSpec track = new TrackSpec(base, new GridPoint(Long.MAX_VALUE, 0));
        OrthogonalSimulator simulator = motionSimulator(List.of(arm), List.of(track),
                InputSpec.single(atomPosition, sampleAtom()),
                InputSpec.single(otherPosition, blockerAtom()));
        simulator.step();

        assertThrows(ArithmeticException.class, simulator::step);

        assertEquals(1, simulator.cycle());
        assertEquals(base, simulator.mechanism(0).position());
        assertTrue(simulator.mechanism(0).grabbing());
        assertEquals(sampleAtom(), simulator.atomAt(atomPosition));
        assertEquals(1, simulator.grabsAt(atomPosition));
        assertEquals(blockerAtom(), simulator.atomAt(otherPosition));
        assertEquals(1, simulator.grabsAt(otherPosition));
        assertEquals(2, simulator.heldEntities(0).size());
    }

    @Test
    void pistonBoundaryInstructionsDoNotMoveHeldMolecules() {
        // 活塞在统一长度上下界都不能移动单臂持有的连通分子，抓握计数和完整键结构保持不变。
        OrthogonalSimulator maximum = boundarySimulator(3, 'w');
        OrthogonalSimulator minimum = boundarySimulator(1, 's');

        assertEquals(3, maximum.mechanism(0).length());
        assertEquals(sampleAtom().withBond(Direction.SOUTH, 2), maximum.atomAt(new GridPoint(3, 0)));
        assertEquals(1, maximum.grabsAt(new GridPoint(3, 0)));
        assertEquals(blockerAtom().withBond(Direction.NORTH, 2), maximum.atomAt(new GridPoint(3, 1)));
        assertEquals(1, maximum.grabsAt(new GridPoint(3, 1)));
        assertFalse(maximum.isHalted());

        assertEquals(1, minimum.mechanism(0).length());
        assertEquals(sampleAtom().withBond(Direction.SOUTH, 2), minimum.atomAt(new GridPoint(1, 0)));
        assertEquals(1, minimum.grabsAt(new GridPoint(1, 0)));
        assertEquals(blockerAtom().withBond(Direction.NORTH, 2), minimum.atomAt(new GridPoint(1, 1)));
        assertEquals(1, minimum.grabsAt(new GridPoint(1, 1)));
        assertFalse(minimum.isHalted());
    }

    @Test
    void blockedExtensionDoesNotCommitLengthOrMoleculeMotion() {
        // 被抓原子和挡路原子是两个合法独立分子；目标占用应使长度与坐标一起回滚。
        MechanismSpec piston = new PistonSpec(
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fw"));
        OrthogonalSimulator simulator = motionSimulator(List.of(piston), List.of(),
                InputSpec.single(new GridPoint(1, 0), sampleAtom()),
                InputSpec.single(new GridPoint(2, 0), blockerAtom()));
        // 第一个周期只执行抓取，活塞长度保持不变。
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertTrue(simulator.mechanism(0).grabbing());

        // 第二个周期：目标格点仍被挡，运动冲突让整周期回滚并停机。
        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("already occupied by entity"));
        assertEquals(1, simulator.mechanism(0).length());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(blockerAtom(), simulator.atomAt(new GridPoint(2, 0)));
    }

    @Test
    void conflictingPistonMovesDoNotCommitEitherLength() {
        // 两个默认单臂活塞共持同一实体，相向伸展冲突必须同时回滚两者长度和实体位置。
        MechanismSpec east = new PistonSpec(
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fw"));
        MechanismSpec west = new PistonSpec(
                new GridPoint(2, 0),
                Direction.WEST,
                1,
                InstructionTape.once("fw"));
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(east, west),
                List.of(InputSpec.single(new GridPoint(1, 0), sampleAtom())),
                List.of(),
                1));
        simulator.init();
        // 第一个周期：两个机构都在第一半周期抓住同一个原子。
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));

        // 第二个周期：两个机构在第二半周期把同一个实体推向相反方向，冲突回滚并停机。
        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertEquals(1, simulator.mechanism(0).length());
        assertEquals(1, simulator.mechanism(1).length());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));
    }

    @Test
    void clockwisePivotRotatesTheMoleculeAroundTheGrabPosition() {
        // 顺时针自转以各分支的抓点为中心，不得统一错用第一个分支的枢轴。
        OrthogonalSimulator simulator = pivotSimulator('e');

        assertPivotedMolecule(simulator, Direction.SOUTH, new GridPoint(0, 1), Direction.NORTH);
    }

    @Test
    void counterClockwisePivotRotatesTheMoleculeAroundTheGrabPosition() {
        // 逆时针采用与顺时针相反的键向和附属原子落点，两个独立分子均应正确旋转。
        OrthogonalSimulator simulator = pivotSimulator('q');

        assertPivotedMolecule(simulator, Direction.NORTH, new GridPoint(0, -1), Direction.SOUTH);
    }

    @Test
    void pivotAfterExtensionUsesTheCurrentLength() {
        // 一次性放置连通双原子分子，排除补料后验证自转使用伸长后的抓点而不是初始长度。
        Atom grabbed = sampleAtom().withBond(Direction.SOUTH, 2);
        Atom attached = blockerAtom().withBond(Direction.NORTH, 2);
        MechanismSpec piston = new PistonSpec(
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fwe"));
        OrthogonalSimulator simulator = motionSimulator(List.of(piston), List.of(),
                InputSpec.of(AtomPlacement.of(1, 0, grabbed), AtomPlacement.of(1, 1, attached)),
                InputSpec.single(new GridPoint(10, 10), Atom.of(9)));

        simulator.run(3);

        MechanismView view = simulator.mechanism(0);
        assertEquals(2, view.length());
        assertEquals(new GridPoint(2, 0), view.grabPosition());
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(2, 0)).bondType(Direction.WEST));
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(1, 0)).bondType(Direction.EAST));
        assertNull(simulator.atomAt(new GridPoint(2, 1)));
        assertFalse(simulator.isHalted());
    }

    @Test
    void pivotWithoutAGrabbedMoleculeIsANoOp() {
        // 静态机械仍支持枢轴旋转，但没有持有分子时不得改变基座、朝向或固定臂长。
        OrthogonalSimulator simulator = simulator(new StationarySpec(
                new GridPoint(7, -4),
                Direction.SOUTH,
                3,
                InstructionTape.once("eq")));

        simulator.run(2);

        assertEquals(
                new MechanismView(
                        new GridPoint(7, -4), Direction.SOUTH, 3, false),
                simulator.mechanism(0));
    }

    private static OrthogonalSimulator pivotSimulator(char pivotInstruction) {
        // 分别校验两条双原子分子后一次性播种，区分分支枢轴且不让补料掩盖坐标和键向旋转。
        Atom grabbed = sampleAtom().withBond(Direction.EAST, 2);
        Atom attached = blockerAtom().withBond(Direction.WEST, 2);
        MechanismSpec arm = new StationarySpec(
                new GridPoint(0, 2),
                Direction.NORTH,
                2,
                new ArmCombination(0x11),
                InstructionTape.once("f" + pivotInstruction));
        OrthogonalSimulator simulator = motionSimulator(List.of(arm), List.of(),
                InputSpec.of(AtomPlacement.of(0, 0, grabbed), AtomPlacement.of(1, 0, attached)),
                InputSpec.of(AtomPlacement.of(2, 0, grabbed), AtomPlacement.of(3, 0, attached)));
        simulator.run(2);
        return simulator;
    }

    private static OrthogonalSimulator boundarySimulator(int length, char instruction) {
        // 单臂持有连通双原子分子，边界指令对两实体都必须只产生静止约束。
        MechanismSpec piston = new PistonSpec(new GridPoint(0, 0), Direction.EAST,
                length, InstructionTape.once("f" + instruction));
        OrthogonalSimulator simulator = motionSimulator(List.of(piston), List.of(),
                InputSpec.of(
                        AtomPlacement.of(length, 0, sampleAtom().withBond(Direction.SOUTH, 2)),
                        AtomPlacement.of(length, 1, blockerAtom().withBond(Direction.NORTH, 2))),
                InputSpec.single(new GridPoint(10, 10), Atom.of(9)));
        simulator.step();
        assertEquals(2, simulator.motionIntents(0).size());
        assertTrue(simulator.motionIntents(0).stream()
                .allMatch(intent -> intent.kind() == EntityIntent.Kind.STAY));
        simulator.step();
        return simulator;
    }

    private static void assertPivotedMolecule(
            OrthogonalSimulator simulator,
            Direction grabbedBond,
            GridPoint attachedPosition,
            Direction attachedBond) {
        // 两个枢轴保持各自抓取原子的位置，附属原子的期望落点直接按手写样本平移。
        MechanismView arm = simulator.mechanism(0);
        assertEquals(new GridPoint(0, 2), arm.position());
        assertEquals(Direction.NORTH, arm.direction());
        assertEquals(2, arm.length());
        assertTrue(arm.grabbing());

        Atom grabbed = simulator.atomAt(new GridPoint(0, 0));
        Atom attached = simulator.atomAt(attachedPosition);
        assertEquals((byte) 1, grabbed.type());
        assertEquals((byte) 2, grabbed.bondType(grabbedBond));
        assertEquals((byte) 5, attached.type());
        assertEquals((byte) 2, attached.bondType(attachedBond));
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(grabbed, simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(attached, simulator.atomAt(new GridPoint(2, attachedPosition.y())));
        assertNull(simulator.atomAt(new GridPoint(3, 0)));
        assertEquals(4, simulator.heldEntities(0).size());
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));
        assertFalse(simulator.isHalted());
    }

    private static void assertMechanism(
            OrthogonalSimulator simulator,
            int length,
            GridPoint position,
            Direction direction) {
        MechanismView mechanism = simulator.mechanism(0);
        assertEquals(length, mechanism.length());
        assertEquals(position, mechanism.position());
        assertEquals(direction, mechanism.direction());
    }

    @SuppressWarnings("unchecked")
    private static OrthogonalSimulator motionSimulator(
            List<MechanismSpec> arms, List<TrackSpec> tracks, InputSpec... molecules) {
        // 每个参数仍经生产校验为合法单分子，但这里只描述一次性棋盘初态，不声明持续生产的 I/O。
        // 同步棋盘、双向实体索引和编号；连通关系直接读取当前棋盘，避免补料干扰往返运动与空格断言。
        new SimulationPlan(arms, tracks, List.of(molecules), List.of(), 1);
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(arms, tracks));
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
            throw new AssertionError("无法建立机械运动测试初态", failure);
        }
        return simulator;
    }

    private static OrthogonalSimulator simulator(MechanismSpec mechanism) {
        return new OrthogonalSimulator(SimulationPlan.of(mechanism));
    }

    private static Atom sampleAtom() {
        return Atom.of(1);
    }

    private static Atom blockerAtom() {
        return Atom.of(5);
    }
}
