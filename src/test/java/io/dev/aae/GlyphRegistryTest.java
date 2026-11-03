package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.dev.aae.om.OMBonds;
import io.dev.aae.om.OMGlyphs;
import org.junit.jupiter.api.Test;

/**
 * 符文公共 API 的验收测试：全局注册表、注册句柄、计划快照隔离、声明校验、占位几何与化学编码。
 */
class GlyphRegistryTest {

    /** 每个测试用独立的类型 ID，避免全局注册表互相污染。 */
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    // ---------- 全局注册与句柄 ----------

    @Test
    void globalRegistryIsASingleProcessWideInstance() {
        assertSame(GlyphRegistry.global(), GlyphRegistry.global());
    }

    @Test
    void registerReturnsAHandleAndPublishesTheDefinition() {
        String typeId = uniqueTypeId("register");
        GlyphDefinition<Void> definition = noopDefinition(typeId);

        try (GlyphRegistry.GlyphHandle handle = GlyphRegistry.global().register(definition)) {
            assertEquals(typeId, handle.typeId());
            assertFalse(handle.isClosed());
            assertTrue(GlyphRegistry.global().isRegistered(typeId));
            assertSame(definition, GlyphRegistry.global().definition(typeId));
            assertTrue(GlyphRegistry.global().typeIds().contains(typeId));
            assertSame(definition, GlyphRegistry.global().definitions().get(typeId));
        }

        assertFalse(GlyphRegistry.global().isRegistered(typeId));
        assertNull(GlyphRegistry.global().definition(typeId));
    }

    // handle 回收之前，拒绝重复注册
    @Test
    void duplicateRegistrationIsRejectedUntilTheHandleIsClosed() {
        String typeId = uniqueTypeId("duplicate");
        GlyphRegistry.GlyphHandle handle = GlyphRegistry.global().register(noopDefinition(typeId));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> GlyphRegistry.global().register(noopDefinition(typeId)));
        assertTrue(failure.getMessage().contains(typeId));

        handle.close();
        assertFalse(GlyphRegistry.global().isRegistered(typeId));

        // 关闭之后同一个类型 ID 可以重新注册。
        try (var second = GlyphRegistry.global().register(noopDefinition(typeId))) {
            assertFalse(second.isClosed());
            assertTrue(GlyphRegistry.global().isRegistered(typeId));
        }
        assertFalse(GlyphRegistry.global().isRegistered(typeId));
    }

    // 回收 handle 是幂等操作，且不会影响其他类型
    @Test
    void closingAHandleIsIdempotentAndDoesNotAffectOtherTypes() {
        String closedType = uniqueTypeId("closed");
        String keptType = uniqueTypeId("kept");
        GlyphDefinition<Void> keptDefinition = noopDefinition(keptType);

        try (GlyphRegistry.GlyphHandle kept = GlyphRegistry.global().register(keptDefinition)) {
            GlyphRegistry.GlyphHandle closed = GlyphRegistry.global().register(
                    noopDefinition(closedType));

            closed.close();
            closed.close();
            closed.close();

            assertTrue(closed.isClosed());
            assertFalse(GlyphRegistry.global().isRegistered(closedType));
            assertTrue(GlyphRegistry.global().isRegistered(keptType));
            assertSame(keptDefinition, GlyphRegistry.global().definition(keptType));
        }
    }

    // 拒绝注册非法 typeId
    @Test
    void registryRejectsMalformedTypeIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphRegistry.global().register(noopDefinition("noNamespace")));
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphRegistry.global().register(noopDefinition("name:")));
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphRegistry.global().register(noopDefinition(":name")));
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphRegistry.global().register(noopDefinition("a:b:c")));
    }

    // 并发注册不同类型，检查每个定义的完整性
    @Test
    void concurrentRegistrationOfDistinctTypesKeepsEveryDefinition() throws Exception {
        int count = 16;
        List<String> typeIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            typeIds.add(uniqueTypeId("concurrent"));
        }

        CyclicBarrier barrier = new CyclicBarrier(count);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        List<Future<GlyphRegistry.GlyphHandle>> futures = new ArrayList<>(count);
        try {
            for (String typeId : typeIds) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return GlyphRegistry.global().register(noopDefinition(typeId));
                }));
            }
            List<GlyphRegistry.GlyphHandle> handles = new ArrayList<>(count);
            for (Future<GlyphRegistry.GlyphHandle> future : futures) {
                handles.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(count, handles.size());
            for (String typeId : typeIds) {
                assertTrue(GlyphRegistry.global().isRegistered(typeId), typeId);
            }
            for (GlyphRegistry.GlyphHandle handle : handles) {
                handle.close();
            }
            for (String typeId : typeIds) {
                assertFalse(GlyphRegistry.global().isRegistered(typeId), typeId);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRegistrationOfTheSameTypeAdmitsExactlyOneWinner() throws Exception {
        String typeId = uniqueTypeId("race");
        int count = 12;
        CyclicBarrier barrier = new CyclicBarrier(count);
        ExecutorService pool = Executors.newFixedThreadPool(count);
        List<Future<GlyphRegistry.GlyphHandle>> futures = new ArrayList<>(count);
        GlyphRegistry.GlyphHandle winner = null;
        try {
            for (int index = 0; index < count; index++) {
                futures.add(pool.submit((Callable<GlyphRegistry.GlyphHandle>) () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        return GlyphRegistry.global().register(noopDefinition(typeId));
                    } catch (IllegalArgumentException rejected) {
                        return null;
                    }
                }));
            }
            // 所有线程同时发起注册且都不关闭句柄，因此只允许一个成功者。
            int winners = 0;
            for (Future<GlyphRegistry.GlyphHandle> future : futures) {
                GlyphRegistry.GlyphHandle handle = future.get(30, TimeUnit.SECONDS);
                if (handle != null) {
                    winners++;
                    winner = handle;
                }
            }
            assertEquals(1, winners, "并发的同类型注册必须恰好成功一次");
            assertTrue(GlyphRegistry.global().isRegistered(typeId));
        } finally {
            pool.shutdownNow();
            if (winner != null) {
                winner.close();
            }
            assertFalse(GlyphRegistry.global().isRegistered(typeId));
        }
    }

    // SimulationPlan 快照会保存它们的 GlyphDefinitions，以便后续的注册表更改不会显示出来
    @Test
    void plansSnapshotTheirGlyphDefinitionsSoLaterRegistryChangesAreInvisible() {
        String typeId = uniqueTypeId("snapshot");
        GlyphDefinition<Void> original = noopDefinition(typeId);
        GlyphRegistry.GlyphHandle handle = GlyphRegistry.global().register(original);
        try {
            SimulationPlan plan = glyphPlan(List.of(glyph(typeId)));

            assertSame(original, plan.glyphDefinition(typeId));
            assertTrue(plan.hasGlyphs());

            handle.close();

            // 已构造的计划持有注册当时的快照，句柄关闭不影响它。
            assertFalse(GlyphRegistry.global().isRegistered(typeId));
            assertSame(original, plan.glyphDefinition(typeId));
            assertSame(original, plan.glyphDefinitions().get(typeId));
            assertEquals(1, plan.glyphs().size());

            OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
            simulator.step();
            assertFalse(simulator.isHalted());
        } finally {
            handle.close();
        }
    }

    @Test
    void plansSnapshotTheDefinitionThatWasRegisteredAtConstructionTime() {
        String typeId = uniqueTypeId("snapshot-swap");
        GlyphDefinition<Void> first = noopDefinition(typeId);
        GlyphDefinition<Void> replacement = noopDefinition(typeId);

        GlyphRegistry.GlyphHandle handle = GlyphRegistry.global().register(first);
        SimulationPlan firstPlan = glyphPlan(List.of(glyph(typeId)));
        assertSame(first, firstPlan.glyphDefinition(typeId));
        handle.close();

        // 关闭之后重新注册同一类型 ID 的另一份定义，新计划拿到新定义，旧计划保持旧定义。
        try (GlyphRegistry.GlyphHandle second = GlyphRegistry.global().register(replacement)) {
            SimulationPlan secondPlan = glyphPlan(List.of(glyph(typeId)));
            assertSame(replacement, secondPlan.glyphDefinition(typeId));
            assertSame(first, firstPlan.glyphDefinition(typeId));
        }
    }

    // ---------- GlyphSpec ----------

    @Test
    void glyphSpecRejectsInvalidDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec("", point(0, 0), Direction.NORTH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec("  ", point(0, 0), Direction.NORTH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec("noNamespace", point(0, 0), Direction.NORTH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec("name:", point(0, 0), Direction.NORTH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec(":name", point(0, 0), Direction.NORTH));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphSpec("a:b:c", point(0, 0), Direction.NORTH));
        assertThrows(NullPointerException.class, () -> new GlyphSpec(null, point(0, 0), Direction.NORTH));
        assertThrows(NullPointerException.class, () -> new GlyphSpec("a:b", null, Direction.NORTH));
        assertThrows(NullPointerException.class, () -> new GlyphSpec("a:b", point(0, 0), null));
    }

    @Test
    void glyphSpecDefaultsToTheUnrotatedEastFacingShape() {
        GlyphSpec spec = new GlyphSpec("custom:probe", point(5, 7));

        assertEquals(Direction.EAST, spec.direction());
        assertEquals(point(2, 3), spec.worldOffset(point(2, 3)));
        assertEquals(point(7, 10), spec.worldPoint(point(2, 3)));
    }

    @Test
    void glyphPlaceholdersLocalOffsetsFollowAllFourDirections() {
        // 朝东时局部 (1,0) 是东，(0,1) 是南；其他朝向绕锚点旋转。
        assertEquals(point(1, 0), GlyphSpec.worldOffset(Direction.EAST, point(1, 0)));
        assertEquals(point(0, 1), GlyphSpec.worldOffset(Direction.EAST, point(0, 1)));
        assertEquals(point(0, 1), GlyphSpec.worldOffset(Direction.SOUTH, point(1, 0)));
        assertEquals(point(-1, 0), GlyphSpec.worldOffset(Direction.SOUTH, point(0, 1)));
        assertEquals(point(-1, 0), GlyphSpec.worldOffset(Direction.WEST, point(1, 0)));
        assertEquals(point(0, -1), GlyphSpec.worldOffset(Direction.WEST, point(0, 1)));
        assertEquals(point(0, -1), GlyphSpec.worldOffset(Direction.NORTH, point(1, 0)));
        assertEquals(point(1, 0), GlyphSpec.worldOffset(Direction.NORTH, point(0, 1)));
    }

    @Test
    void glyphPlaceholderDeclarationsMustBeUniqueAndContainTheAnchor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:empty", List.of(), () -> null, (context, state) -> null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:no-anchor",
                        List.of(point(1, 0)),
                        () -> null,
                        (context, state) -> null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:duplicate",
                        List.of(point(0, 0), point(0, 0)),
                        () -> null,
                        (context, state) -> null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "noNamespace", List.of(point(0, 0)), () -> null, (context, state) -> null));
    }

    // ---------- 计划构造期拒绝 ----------

    @Test
    void planConstructionRejectsUnknownGlyphTypes() {
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationPlan(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(glyph("custom:never-registered")),
                        1));
        assertTrue(unknown.getMessage().contains("custom:never-registered"));
    }

    @Test
    void inputSlotDeclarationsAreValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphDefinition.InputSlot.of(0, (byte) 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> GlyphDefinition.InputSlot.bonded(
                        0, (byte) 1, Direction.EAST, Atom.TEMPORARY_BOND));

    }

    @Test
    void glyphDefinitionRejectsConflictingSlotDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:duplicate-input",
                        List.of(point(0, 0)),
                        List.of(
                                GlyphDefinition.InputSlot.occupied(0),
                                GlyphDefinition.InputSlot.occupied(0)),
                        List.of(),
                        () -> null,
                        (context, state) -> null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:duplicate-output",
                        List.of(point(0, 0)),
                        List.of(),
                        List.of(
                                GlyphDefinition.OutputSlot.of(0),
                                GlyphDefinition.OutputSlot.of(0)),
                        () -> null,
                        (context, state) -> null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GlyphDefinition<>(
                        "custom:shared-cell",
                        List.of(point(0, 0)),
                        List.of(GlyphDefinition.InputSlot.occupied(0)),
                        List.of(GlyphDefinition.OutputSlot.of(0)),
                        () -> null,
                        (context, state) -> null));
    }

    @Test
    void slotIndicesMustBelongToTheDefinition() {
        for (int index : new int[]{-1, 1}) {
            for (boolean input : new boolean[]{true, false}) {
                IndexOutOfBoundsException error = assertThrows(IndexOutOfBoundsException.class,
                        () -> GlyphDefinition.of("custom:index-validation", List.of(point(0, 0)),
                                input ? List.of(GlyphDefinition.InputSlot.occupied(index)) : List.of(),
                                input ? List.of() : List.of(GlyphDefinition.OutputSlot.of(index)),
                                context -> {}));
                assertTrue(error.getMessage().contains(Integer.toString(index)));
                assertTrue(error.getMessage().contains("count 1"));
                assertTrue(error.getMessage().contains("custom:index-validation"));
            }
        }
    }

    @Test
    void inputSlotMatchingChecksTypeAndBond() {
        GlyphDefinition.InputSlot typed = GlyphDefinition.InputSlot.of(0, (byte) 7);
        assertFalse(typed.matches(null));
        assertFalse(typed.matches(Atom.of((byte) 6)));
        assertTrue(typed.matches(Atom.of((byte) 7)));

        GlyphDefinition.InputSlot bonded = GlyphDefinition.InputSlot.bonded(
                0, (byte) 7, Direction.EAST, (byte) 3);
        assertFalse(bonded.matches(Atom.of((byte) 7)));
        assertTrue(bonded.matches(Atom.of((byte) 7).withBond(Direction.EAST, (byte) 3)));
        assertFalse(bonded.matches(Atom.of((byte) 7).withBond(Direction.EAST, (byte) 4)));

        GlyphDefinition.InputSlot any = GlyphDefinition.InputSlot.occupied(0);
        assertTrue(any.matches(Atom.of((byte) 3)));
        assertTrue(any.matches(Atom.of((byte) 3).withBond(Direction.SOUTH, (byte) 9)));
    }

    // ---------- 编码规则 ----------

    @Test
    void codeValidationRejectsZeroOneAndDuplicates() {
        assertThrows(IllegalArgumentException.class, () -> Atom.requireAtomCode((byte) 0));
        Atom.requireAtomCode((byte) 1);
        assertThrows(IllegalArgumentException.class, () -> Atom.requireBondCode((byte) 0));
        assertThrows(IllegalArgumentException.class, () -> Atom.requireBondCode(Atom.TEMPORARY_BOND));
        Atom.requireBondCode((byte) 2);

        BaseEncoding.requireDistinctAtomCodes((byte) 1, (byte) 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> BaseEncoding.requireDistinctAtomCodes((byte) 1, (byte) 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> BaseEncoding.requireDistinctAtomCodes((byte) 0, (byte) 1));
        BaseEncoding.requireDistinctBondCodes((byte) 2, (byte) 3);
        assertThrows(
                IllegalArgumentException.class,
                () -> BaseEncoding.requireDistinctBondCodes((byte) 2, (byte) 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> BaseEncoding.requireDistinctBondCodes(Atom.TEMPORARY_BOND, (byte) 2));
    }

    @Test
    void builtInNormalBondCodeIsUsableAsALongTermBond() {
        byte normal = OMBonds.NORMAL.code;
        Atom.requireBondCode(normal);
        assertTrue(Atom.isRealBond(normal));
        assertFalse(Atom.isRealBond(Atom.TEMPORARY_BOND));
    }

    @Test
    void planSnapshotsOnlyTheTypesItDeclaresAndStaysClosed() {
        String kept = uniqueTypeId("closure-kept");
        String dropped = uniqueTypeId("closure-dropped");
        var gr = GlyphRegistry.global();
        try (var keptHandle = gr.register(noopDefinition(kept));
             var droppedHandle = gr.register(noopDefinition(dropped))) {
            GlyphSpec only = glyph(kept);
            SimulationPlan plan = glyphPlan(List.of(only));

            // 计划只快照用到的类型，声明与定义互相闭合。
            assertEquals(Set.of(kept), plan.glyphDefinitions().keySet());
            assertEquals(Set.of(kept), plan.glyphTypeIds());
            assertEquals(only.typeId(), plan.glyphs().get(0).typeId());
            assertEquals(only.typeId(), plan.glyphDefinition(kept).typeId());

            // 注册表变化（关闭句柄、注册新类型）都不影响已构造计划的快照。
            droppedHandle.close();
            keptHandle.close();
            String extra = uniqueTypeId("closure-extra");
            try (GlyphRegistry.GlyphHandle extraHandle =
                         GlyphRegistry.global().register(noopDefinition(extra))) {
                assertEquals(Set.of(kept), plan.glyphTypeIds());
                assertFalse(GlyphRegistry.global().isRegistered(kept));
                assertTrue(GlyphRegistry.global().isRegistered(extra));
            }
        }
    }

    @Test
    void allPublicGlyphTypeIdsResolveToDefinitions() {
        // 注册表里的每一个句柄类型都能解析到定义。
        for (String typeId : GlyphRegistry.global().typeIds()) {
            assertNotNull(GlyphRegistry.global().definition(typeId), typeId);
        }
        // 内建类型在计划里同样可以解析。
        SimulationPlan plan = glyphPlan(List.of(OMGlyphs.calcificationSpec(0, 0)));
        assertEquals(OMGlyphs.CALCIFICATION_ID,
                plan.glyphDefinition(OMGlyphs.CALCIFICATION_ID).typeId());
        assertTrue(plan.hasGlyphs());
    }

    // ---------- 辅助 ----------

    static GlyphSpec glyph(String typeId) {
        return new GlyphSpec(typeId, point(0, 0), Direction.NORTH);
    }

    static GlyphSpec glyph(String typeId, long x, long y, Direction direction) {
        return new GlyphSpec(typeId, point(x, y), direction);
    }

    static SimulationPlan glyphPlan(List<GlyphSpec> glyphs) {
        GlyphRegistry rig = GlyphRegistry.global();
        for (GlyphSpec glyph : glyphs) {
            if (rig.isRegistered(glyph.typeId())) continue;
            rig.register(new GlyphDefinition<Void>(
                    glyph.typeId(),
                    List.of(new GridPoint(0, 0)),
                    () -> null,
                    (context, state) -> null));
        }
        return new SimulationPlan(
                List.of(), List.of(), List.of(), List.of(), glyphs, 1);
    }

    static GlyphDefinition<Void> noopDefinition(String typeId) {
        return GlyphDefinition.single(typeId, context -> { });
    }

    static GridPoint point(long x, long y) {
        return new GridPoint(x, y);
    }

    private static String uniqueTypeId(String label) {
        return "test." + label + ":" + UNIQUE.incrementAndGet() + "-" + System.nanoTime();
    }
}
