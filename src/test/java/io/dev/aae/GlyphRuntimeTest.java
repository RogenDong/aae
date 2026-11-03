package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.dev.aae.om.OMBonds;
import io.dev.aae.om.OMGlyphs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 符文运行时的验收测试：内建符文、双半周期可见性、延迟生成、预留交接、临时键、事务回滚与
 * 前置条件。
 */
class GlyphRuntimeTest {

    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @Test
    void transportedAtomsAreBondedOnAGlyphInAValidLayout() {
        try (var handle = GlyphRegistry.global().register(OMGlyphs.BONDING)) {
            OrthogonalSimulator simulator = simulatorOf(
                    List.of(new MechanismSpec(new GridPoint(-2, 1), Direction.NORTH,
                                    InstructionTape.once("fggr")),
                            new MechanismSpec(new GridPoint(-1, -1), Direction.SOUTH,
                                    InstructionTape.once("fggr"))),
                    List.of(track(-2, 1, -1, 1, 0, 1), track(-1, -1, 0, -1, 1, -1)),
                    List.of(InputSpec.of("in",
                            new AtomPlacement(new GridPoint(-2, 0), sampleAtom()),
                            new AtomPlacement(new GridPoint(-1, 0), otherAtom()))), List.of(),
                    List.of(OMGlyphs.bondingSpec(0, 0, Direction.EAST)));
            simulator.init();
            simulator.run(3);
            assertFalse(simulator.isHalted());
            assertEquals(OMBonds.NORMAL.code, simulator.atomAt(new GridPoint(0, 0)).bondType(Direction.EAST));
            assertEquals(OMBonds.NORMAL.code, simulator.atomAt(new GridPoint(1, 0)).bondType(Direction.WEST));
            assertEquals(2, simulator.moleculeAt(new GridPoint(0, 0)).size());
            simulator.step();
            assertEquals(0, simulator.grabsAt(new GridPoint(0, 0)));
            assertEquals(0, simulator.grabsAt(new GridPoint(1, 0)));
        }
    }

    // ---------- 内建符文：Bonding / Unbonding ----------

    @ParameterizedTest
    @EnumSource(Direction.class)
    void builtInBondingGlyphBondsTwoAdjacentAtoms(Direction direction) {
        GridPoint anchor = new GridPoint(0, 0);
        GridPoint neighbor = anchor.translate(direction);
        try (var handle = GlyphRegistry.global().register(OMGlyphs.BONDING)) {
            GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                    List.of(InputSpec.of(
                            "in",
                            new AtomPlacement(anchor, sampleAtom()),
                            new AtomPlacement(neighbor, otherAtom()))),
                    List.of(OMGlyphs.bondingSpec(0, 0, direction)));

            simulator.step();

            Atom anchorAtom = simulator.atomAt(anchor);
            Atom neighborAtom = simulator.atomAt(neighbor);
            assertNotNull(anchorAtom);
            assertNotNull(neighborAtom);
            assertEquals(OMBonds.NORMAL.code, anchorAtom.bondType(direction));
            assertEquals(OMBonds.NORMAL.code, neighborAtom.bondType(direction.opposite()));
            assertEquals(2, simulator.moleculeAt(anchor).size());

            // 第二个周期里键已经存在：符文按“同键静默跳过”处理，不产生任何变化。
            simulator.step();
            assertEquals(OMBonds.NORMAL.code,
                    simulator.atomAt(anchor).bondType(direction));
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void builtInUnbondingGlyphClearsTheBondAndWritesTemporaryMarkers(Direction direction) {
        GridPoint anchor = new GridPoint(0, 0);
        GridPoint neighbor = anchor.translate(direction);
        try (var handle = GlyphRegistry.global().register(OMGlyphs.UNBONDING)) {
            GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                    List.of(InputSpec.of(
                            "in",
                            new AtomPlacement(anchor,
                                    sampleAtom().withBond(direction, OMBonds.NORMAL.code)),
                            new AtomPlacement(neighbor,
                                    otherAtom().withBond(direction.opposite(), OMBonds.NORMAL.code)))),
                    List.of(OMGlyphs.unbondingSpec(0, 0, direction)));

            // 第一个半周期拆键并写下临时标记；第二个半周期还属于同一个周期，标记仍在。
            simulator.step();

            Atom anchorAtom = simulator.atomAt(anchor);
            Atom neighborAtom = simulator.atomAt(neighbor);
            assertEquals(Atom.TEMPORARY_BOND, anchorAtom.bondType(direction));
            assertEquals(Atom.TEMPORARY_BOND, neighborAtom.bondType(direction.opposite()));
            // 临时标记不构成分子连通。
            assertEquals(1, simulator.moleculeAt(anchor).size());

            // 下一个周期开始时临时标记被清除，但不会恢复成长期键。
            simulator.step();
            assertEquals((byte) 0, simulator.atomAt(anchor).bondType(direction));
            assertEquals((byte) 0, simulator.atomAt(neighbor).bondType(direction.opposite()));
        }
    }

    @Test
    void temporaryBondMarkersBlockRemovalForTheWholeCycle() {
        List<TryResult> attempts = new ArrayList<>();
        String removalType = uniqueTypeId("temporary-removal");
        GlyphDefinition<Void> removal = GlyphDefinition.of(
                removalType,
                OMGlyphs.UNBONDING.placeholder(),
                context -> {
                    OMGlyphs.UNBONDING.behavior().apply(context, null);
                    attempts.add(context.board().remove(0));
                });

        // 在同一符文回调内先拆键再删除，避免声明两个占位重叠的符文。
        try (var handle = GlyphRegistry.global().register(removal)) {
            GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                    List.of(InputSpec.of(
                            "in",
                            new AtomPlacement(new GridPoint(0, 0), eastBonded()),
                            new AtomPlacement(new GridPoint(1, 0), westBonded()))),
                    List.of(new GlyphSpec("removal", removalType, new GridPoint(0, 0), Direction.EAST)));

            simulator.step();

            // 两个半周期各尝试一次删除；第一个半周期刚拆完键，第二个半周期还在同一周期内：
            // “刚解除键”的临时标记必须让两次删除都被跳过。
            assertEquals(List.of(TryResult.SKIPPED, TryResult.SKIPPED), attempts);
            assertEquals(2, simulator.boardSize());
        }
    }

    // ---------- 延迟生成 ----------

    @Test
    void queuedProductReservesItsCellUntilTheEndOfTheCycle() {
        String type = uniqueTypeId("reservation-lifetime");
        GridPoint target = new GridPoint(11, 8);
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.single(type, context -> {
            if (context.phase().isFirstHalf()) {
                context.board().queueSpawn(0, Atom.of((byte) 9));
            }
        }))) {
            GlyphRuntimeFixture fixture = new GlyphRuntimeFixture(
                    List.of(InputSpec.single("initial", target, sampleAtom())),
                    List.of(new GlyphSpec("producer", type, target)));
            fixture.runtime.beginCycle(0);
            fixture.half(GlyphPhase.FIRST_HALF);
            assertEquals(java.util.Set.of(target), fixture.runtime.reservedCells());
            assertEquals(1, fixture.pendingGlyphSpawnCount());
            assertEquals(sampleAtom(), fixture.atomAt(target));
            fixture.half(GlyphPhase.SECOND_HALF);
            assertTrue(fixture.runtime.reservedCells().isEmpty());
            assertEquals(0, fixture.pendingGlyphSpawnCount());
            assertEquals(sampleAtom(), fixture.atomAt(target));
        }
    }

    @Test
    void spawnOnAnOccupiedCellIsDeferredInsteadOfOverwriting() {
        Atom occupant = Atom.of((byte) 7);
        Atom product = Atom.of((byte) 9);
        List<TryResult> results = new ArrayList<>();
        String spawnerType = uniqueTypeId("deferred-spawn");
        GlyphRegistry.global().register(GlyphDefinition.single(spawnerType, context -> {
            results.add(context.board().spawn(0, product));
            context.board().spawn(0, product);
        }));

        GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(0, 0), occupant)),
                glyphs(new GlyphSpec("spawner", spawnerType,
                        new GridPoint(0, 0), Direction.NORTH)));

        simulator.step();

        // 占用格保留原原子；两次调用都返回 SKIPPED。第二半周期结束后，未落盘产物不跨整数周期。
        assertEquals(occupant, simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(0, simulator.pendingGlyphSpawnCount());
        assertEquals(List.of(TryResult.SKIPPED, TryResult.SKIPPED), results);
    }

    @Test
    void deferredProductsExpireBeforeTheCellFreesInTheNextCycle() {
        Atom occupant = Atom.of((byte) 7);
        Atom product = Atom.of((byte) 9);
        String producerType = uniqueTypeId("queued-spawn");
        GlyphRegistry.global().register(new GlyphDefinition<Void>(
                producerType,
                List.of(new GridPoint(0, 0)),
                () -> null,
                (context, state) -> {
                    if (context.cycle() == 0 && context.phase().isFirstHalf()) {
                        context.board().queueSpawn(0, product);
                    }
                    if (context.cycle() == 1 && context.phase().isFirstHalf()) {
                        context.board().remove(0);
                    }
                    return null;
                }));

        GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(4, 4), occupant)),
                List.of(new GlyphSpec("producer", producerType, new GridPoint(4, 4), Direction.NORTH)));

        // 周期 0：棋盘初态占着目标格，产物只在本周期内等待。
        simulator.step();
        assertEquals(occupant, simulator.atomAt(new GridPoint(4, 4)));
        assertEquals(0, simulator.pendingGlyphSpawnCount());

        // 删除发生在下一周期；上一周期的产物已过期，腾空后不会复活。
        simulator.step();
        assertNull(simulator.atomAt(new GridPoint(4, 4)));
        assertEquals(0, simulator.pendingGlyphSpawnCount());
        simulator.step();
        assertNull(simulator.atomAt(new GridPoint(4, 4)));
        assertEquals(0, simulator.pendingGlyphSpawnCount());
    }

    @Test
    void reservedGlyphProductConflictingWithTransportRollsBackTheWholeCycle() {
        String type = uniqueTypeId("reserved-transport");
        Atom product = Atom.of((byte) 9);
        try (var handle = GlyphRegistry.global().register(new GlyphDefinition<Integer>(
                type, List.of(new GridPoint(0, 0)), () -> 0, (context, state) -> {
                    if (context.cycle() == 1 && context.phase().isFirstHalf()) {
                        context.board().queueSpawn(0, product);
                    }
                    return state + 1;
                }))) {
            OrthogonalSimulator simulator = simulatorOf(
                    List.of(new MechanismSpec(new GridPoint(0, 1), Direction.NORTH,
                            InstructionTape.once("fg"))),
                    List.of(track(0, 1, 1, 1)),
                    List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())), List.of(),
                    List.of(new GlyphSpec("producer", type, new GridPoint(1, 0))));
            simulator.init();
            simulator.step();
            long held = simulator.entityAt(new GridPoint(0, 0));
            Object state = simulator.glyphState("producer");

            assertThrows(GlyphExecutionException.class, simulator::step);
            assertTrue(simulator.isHalted());
            assertEquals(1, simulator.cycle());
            assertEquals(new GridPoint(0, 1), simulator.mechanism(0).position());
            assertEquals(held, simulator.entityAt(new GridPoint(0, 0)));
            assertEquals(1, simulator.grabsAt(new GridPoint(0, 0)));
            assertNull(simulator.atomAt(new GridPoint(1, 0)));
            assertEquals(0, simulator.pendingGlyphSpawnCount());
            assertEquals(state, simulator.glyphState("producer"));
        }
    }

    @Test
    void immediateSpawnCancelsTheQueuedProductOnTheSameCell() {
        Atom product = Atom.of((byte) 4);
        Atom replacement = Atom.of((byte) 6);
        String glyphType = uniqueTypeId("queue-then-spawn");
        GlyphRegistry.global().register(new GlyphDefinition<Void>(
                glyphType,
                List.of(new GridPoint(0, 0)),
                () -> null,
                (context, state) -> {
                    if (context.cycle() == 0 && context.phase().isFirstHalf()) {
                        context.board().queueSpawn(0, product);
                        context.board().spawn(0, replacement);
                    }
                    return null;
                }));

        OrthogonalSimulator simulator = simulatorOf(
                List.of(), List.of(), List.of(),
                glyphs(new GlyphSpec("glyph", glyphType, new GridPoint(6, 6), Direction.NORTH)));

        simulator.step();
        assertEquals(0, simulator.pendingGlyphSpawnCount());
        assertEquals(replacement, simulator.atomAt(new GridPoint(6, 6)));
    }

    // ---------- 前置条件 ----------

    @Test
    void glyphIsSkippedWhenItsDeclaredInputDoesNotMatch() {
        List<String> calls = new ArrayList<>();
        String glyphType = uniqueTypeId("checked-input");
        GlyphRegistry.global().register(GlyphDefinition.of(
                glyphType,
                List.of(new GridPoint(0, 0)),
                // 声明的是锚点格，也就是符文锚点 (2,2) 本身。
                List.of(GlyphDefinition.InputSlot.of(0, (byte) 5)),
                List.of(),
                context -> calls.add("cycle=" + context.cycle())));

        GlyphRuntimeFixture wrongType = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(2, 2), sampleAtom())),
                glyphs(new GlyphSpec("checked", glyphType,
                        new GridPoint(2, 2), Direction.NORTH)));
        wrongType.step();
        assertTrue(calls.isEmpty(), "输入类型不匹配时符文必须整条跳过");

        GlyphRuntimeFixture matching = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(2, 2), Atom.of((byte) 5))),
                glyphs(new GlyphSpec("checked", glyphType,
                        new GridPoint(2, 2), Direction.NORTH)));
        matching.step();
        assertEquals(2, calls.size(), "类型匹配时两个半周期各执行一次");
    }

    @Test
    void glyphIsSkippedWhenADeclaredOutputCellIsOccupied() {
        List<String> calls = new ArrayList<>();
        String glyphType = uniqueTypeId("checked-output");
        GlyphRegistry.global().register(GlyphDefinition.of(
                glyphType,
                List.of(new GridPoint(0, 0), new GridPoint(1, 0)),
                List.of(),
                List.of(GlyphDefinition.OutputSlot.of(1)),
                context -> calls.add("called")));

        // (4,3) 的棋盘初态占着符文输出格点：整条符文必须跳过。
        GlyphRuntimeFixture blocked = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(4, 3), sampleAtom())),
                glyphs(new GlyphSpec("checked", glyphType,
                        new GridPoint(3, 3), Direction.EAST)));
        blocked.step();
        assertTrue(calls.isEmpty(), "输出格被占用时符文必须整条跳过");
        assertEquals(1, blocked.boardSize());

        // 对照：输出格空闲时两个半周期各执行一次。
        GlyphRuntimeFixture free = new GlyphRuntimeFixture(
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                glyphs(new GlyphSpec("checked", glyphType,
                        new GridPoint(3, 3), Direction.EAST)));
        free.step();
        assertEquals(2, calls.size());
    }

    // ---------- 回滚与重置 ----------

    @Test
    void glyphBusinessExceptionRollsBackTheWholeCycleAndIsRethrown() {
        IllegalStateException failure = new IllegalStateException("boom");
        String glyphType = uniqueTypeId("throwing");
        OrthogonalSimulator[] observed = new OrthogonalSimulator[1];
        try (var handle = GlyphRegistry.global().register(new GlyphDefinition<Integer>(
                glyphType,
                List.of(new GridPoint(0, 0), new GridPoint(1, 0)),
                () -> 0,
                (context, state) -> {
                    if (context.cycle() == 1) {
                        if (context.phase().isFirstHalf()) {
                            assertEquals(TryResult.APPLIED, context.board().spawn(0, otherAtom()));
                            assertEquals(TryResult.APPLIED, context.board().queueSpawn(1, sampleAtom()));
                        } else {
                            // 抛异常前确认机构已运动，第一半周期状态与延迟产物也已经提交。
                            assertEquals(3, state);
                            assertEquals(new GridPoint(0, 1), observed[0].mechanism(0).position());
                            assertEquals(sampleAtom(), observed[0].atomAt(new GridPoint(6, 5)));
                            throw failure;
                        }
                    }
                    return state + 1;
                }))) {

            OrthogonalSimulator simulator = simulatorOf(
                    List.of(new MechanismSpec(new GridPoint(-1, 1), Direction.NORTH,
                            InstructionTape.once("fg"))),
                    List.of(track(-1, 1, 0, 1)),
                    List.of(InputSpec.single("in", new GridPoint(-1, 0), sampleAtom())),
                    List.of(),
                    glyphs(new GlyphSpec("throwing", glyphType,
                            new GridPoint(5, 5), Direction.EAST)));
            observed[0] = simulator;
            simulator.init();

            simulator.step();
            assertEquals(1, simulator.cycle());
            assertEquals(1, simulator.boardSize());
            long entity = simulator.entityAt(new GridPoint(-1, 0));
            assertEquals(2, simulator.glyphState("throwing"));

            IllegalStateException thrown = assertThrows(IllegalStateException.class, simulator::step);
            assertSame(failure, thrown);
            assertTrue(simulator.isHalted());
            assertEquals(1, simulator.cycle());
            assertEquals(1, simulator.warnings().size());
            // 周期开始时的状态被完整还原。
            assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(-1, 0)));
            assertEquals(entity, simulator.entityAt(new GridPoint(-1, 0)));
            assertEquals(List.of(entity), simulator.heldEntities(0));
            assertEquals(1, simulator.grabsAt(new GridPoint(-1, 0)));
            assertEquals(new GridPoint(-1, 1), simulator.mechanism(0).position());
            assertEquals(2, simulator.glyphState("throwing"));
            assertEquals(0, simulator.pendingGlyphSpawnCount());
            assertNull(simulator.atomAt(new GridPoint(0, 0)));
            assertNull(simulator.atomAt(new GridPoint(5, 5)));
            assertNull(simulator.atomAt(new GridPoint(6, 5)));
            assertEquals(1, simulator.boardSize());
        }
    }

    @Test
    void resetRebuildsGlyphStateAndRestartsEntityIds() {
        String glyphType = uniqueTypeId("stateful");
        GlyphRegistry.global().register(new GlyphDefinition<Integer>(
                glyphType,
                List.of(new GridPoint(0, 0)),
                () -> null,
                (context, state) -> state == null ? 1 : state + 1));

        OrthogonalSimulator simulator = simulatorOf(
                List.of(), List.of(), List.of(),
                glyphs(new GlyphSpec("stateful", glyphType,
                        new GridPoint(8, 8), Direction.NORTH)));

        simulator.step();
        assertEquals(2, simulator.glyphState("stateful"));

        simulator.reset();
        assertNull(simulator.glyphState("stateful"));
        assertEquals(0, simulator.pendingGlyphSpawnCount());

        // 重置后第一次输入生产仍然从 1 号实体开始。
        OrthogonalSimulator withInput = simulatorOf(
                List.of(),
                List.of(InputSpec.single("in", new GridPoint(0, 0), sampleAtom())),
                List.of(),
                List.of());
        withInput.init();
        long first = withInput.entityAt(new GridPoint(0, 0));
        assertEquals(1L, first);
        withInput.reset();
        assertEquals(0, withInput.entityAt(new GridPoint(0, 0)));
        withInput.init();
        assertEquals(first, withInput.entityAt(new GridPoint(0, 0)));
    }

    @Test
    void glyphsRunInDeclarationOrderWithinOneHalfCycle() {
        List<String> order = new ArrayList<>();
        String firstType = uniqueTypeId("first");
        GlyphRegistry.global().register(GlyphDefinition.single(
                firstType, context -> order.add(context.spec().id())));
        String secondType = uniqueTypeId("second");
        GlyphRegistry.global().register(GlyphDefinition.single(
                secondType, context -> order.add(context.spec().id())));

        OrthogonalSimulator simulator = simulatorOf(
                List.of(), List.of(), List.of(),
                List.of(
                        new GlyphSpec("second", secondType, new GridPoint(0, 0), Direction.NORTH),
                        new GlyphSpec("first", firstType, new GridPoint(2, 0), Direction.NORTH)));

        simulator.step();
        assertEquals(List.of("second", "first", "second", "first"), order);
    }

    @Test
    void boardViewIsSharedForTheWholeHalfCycleAndRejectedAfterwards() {
        List<GlyphBoard> seen = new ArrayList<>();
        GlyphBoard[] captured = new GlyphBoard[1];
        String firstType = uniqueTypeId("view-first");
        GlyphRegistry.global().register(GlyphDefinition.of(firstType,
                List.of(new GridPoint(0, 0), new GridPoint(1, 0)),
                List.of(GlyphDefinition.InputSlot.of(0, (byte) 1)),
                List.of(GlyphDefinition.OutputSlot.freeWhen(1,
                        reader -> reader.atomAt(0).orElseThrow().type() == 1)), context -> {
            GlyphBoard board = context.board();
            int indexInHalf = seen.size() & 1;
            seen.add(board);
            // 同一半周期内的第一个符文：记录本半周期的视图。
            assertEquals(0, indexInHalf);
            captured[0] = board;
            // 视图在整个半周期里都可用。
            assertTrue(board.isOccupied(0));
            assertEquals((byte) 1, board.atomAt(0).orElseThrow().type());
        }));
        String secondType = uniqueTypeId("view-second");
        GlyphRegistry.global().register(GlyphDefinition.of(secondType,
                List.of(new GridPoint(0, 1), new GridPoint(0, 0)),
                List.of(GlyphDefinition.InputSlot.of(0, (byte) 2)),
                List.of(GlyphDefinition.OutputSlot.freeWhen(1,
                        reader -> reader.atomAt(0).orElseThrow().type() == 2)), context -> {
            GlyphBoard board = context.board();
            int indexInHalf = seen.size() & 1;
            seen.add(board);
            // 后半周期内的第二个符文：拿到的必须与第一个完全相同。
            assertEquals(1, indexInHalf);
            assertSame(captured[0], board);
            // 同一次回调里可以反复调用。
            assertTrue(board.isOccupied(0));
            assertEquals((byte) 2, board.atomAt(0).orElseThrow().type());
        }));

        GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                List.of(InputSpec.of("shared-view-input",
                        new AtomPlacement(new GridPoint(0, 0), Atom.of((byte) 1)),
                        new AtomPlacement(new GridPoint(5, 4), Atom.of((byte) 2)))),
                List.of(
                        new GlyphSpec("first", firstType, new GridPoint(0, 0), Direction.NORTH),
                        new GlyphSpec("second", secondType, new GridPoint(4, 4), Direction.NORTH)));

        simulator.step();

        // 两个半周期各一对回调，同一半周期内两个符文共用同一个视图实例。
        assertEquals(4, seen.size());
        assertSame(seen.get(0), seen.get(1));
        assertSame(seen.get(2), seen.get(3));
        assertNotSame(seen.get(0), seen.get(2));

        // 符文阶段结束后视图失效：窗口之外的任何方法（含只读查询）都拒绝执行。
        assertThrows(GlyphExecutionException.class,
                () -> captured[0].isOccupied(0));
        assertThrows(GlyphExecutionException.class,
                () -> captured[0].atomAt(0));
        assertThrows(GlyphExecutionException.class,
                () -> captured[0].moleculeAt(0));
        assertThrows(GlyphExecutionException.class,
                () -> captured[0].remove(0));
        assertThrows(GlyphExecutionException.class,
                () -> captured[0].bond(0, Direction.EAST,
                        OMBonds.NORMAL.code));
    }

    private static OrthogonalSimulator simulatorOf(
            List<MechanismSpec> mechanisms,
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            List<GlyphSpec> glyphs) {
        return simulatorOf(mechanisms, List.of(), inputs, outputs, glyphs);
    }

    private static OrthogonalSimulator simulatorOf(
            List<MechanismSpec> mechanisms,
            List<TrackSpec> tracks,
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            List<GlyphSpec> glyphs) {
        return new OrthogonalSimulator(new SimulationPlan(
                mechanisms, tracks, inputs, outputs, glyphs, 1));
    }

    private static List<GlyphSpec> glyphs(GlyphSpec... specs) {
        return List.of(specs);
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

    private static GlyphSpec glyph(String typeId, long x, long y, Direction direction) {
        return new GlyphSpec(typeId, typeId, new GridPoint(x, y), direction);
    }

    /** 占位符：{@code glyph()} 用类型 ID 当符文 ID，这里显式换一个可读的 ID。 */
    private static GlyphSpec withId(GlyphSpec spec, String id) {
        return new GlyphSpec(id, spec.typeId(), spec.position(), spec.direction());
    }

    private static InputSpec twoAtomInput(Atom first, Atom second) {
        return InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(0, 0), first),
                new AtomPlacement(new GridPoint(1, 0), second));
    }

    private static Atom sampleAtom() {
        return Atom.of((byte) 1);
    }

    private static Atom otherAtom() {
        return Atom.of((byte) 5);
    }

    private static Atom eastBonded() {
        return Atom.of((byte) 1).withBond(Direction.EAST, OMBonds.NORMAL.code);
    }

    private static Atom westBonded() {
        return Atom.of((byte) 5).withBond(Direction.WEST, OMBonds.NORMAL.code);
    }

    private static String uniqueTypeId(String label) {
        return "test.glyph." + label + ":" + UNIQUE.incrementAndGet() + "-" + System.nanoTime();
    }
}
