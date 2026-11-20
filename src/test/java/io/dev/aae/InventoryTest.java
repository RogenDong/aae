package io.dev.aae;

import io.dev.aae.glyph.GlyphDefinition;
import io.dev.aae.glyph.GlyphRegistry;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.mech.MechanismSpec;
import io.dev.aae.mech.StationarySpec;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 库存模型的单元测试与“输入取原料、输出提交产品”的运行态集成测试。
 */
class InventoryTest {

    // ---------- 库存记账 ----------

    @Test
    void replenishAddsRawMaterialAndQueriesReportIt() {
        Inventory inventory = new Inventory();
        InputSpec ore = InputSpec.single(new GridPoint(0, 0), Atom.of(1));

        assertEquals(0, inventory.rawCount(ore));
        inventory.replenish(ore, 3);

        assertEquals(3, inventory.rawCount(ore));
        assertEquals(3, inventory.rawTotal());
        assertEquals(0, inventory.productTotal());
    }

    @Test
    void replenishRejectsNonPositiveQuantities() {
        Inventory inventory = new Inventory();
        InputSpec ore = InputSpec.single(new GridPoint(0, 0), Atom.of(1));

        assertThrows(IllegalArgumentException.class, () -> inventory.replenish(ore, 0));
        assertThrows(IllegalArgumentException.class, () -> inventory.replenish(ore, -2));
        assertThrows(IllegalArgumentException.class, () -> inventory.takeRaw(ore, -1));
        assertEquals(0, inventory.rawTotal());
    }

    @Test
    void takingNeverExceedsTheAvailableStock() {
        Inventory inventory = new Inventory();
        InputSpec ore = InputSpec.single(new GridPoint(0, 0), Atom.of(1));
        OutputSpec ingot = OutputSpec.single(new GridPoint(2, 0), Atom.of(2));
        inventory.replenish(ore, 4);

        assertEquals(2, inventory.takeRaw(ore, 2));
        assertEquals(2, inventory.rawCount(ore));
        assertEquals(2, inventory.takeRaw(ore, 10));
        assertEquals(0, inventory.rawCount(ore));
        assertEquals(0, inventory.takeRaw(ore, 1));
        // 空库存的产品取出同样只返回 0，不会出现负数。
        assertEquals(0, inventory.takeProduct(ingot, 3));
    }

    @Test
    void translatedAndReorderedTemplatesShareOneRawStock() {
        // 同一形状的模板整体平移或改变声明顺序后仍然是同一种原料。
        Atom eastBonded = Atom.of(1).withBond(Direction.EAST, 2);
        Atom westBonded = Atom.of(5).withBond(Direction.WEST, 2);
        InputSpec near = InputSpec.of(
                AtomPlacement.of(0, 0, eastBonded),
                AtomPlacement.of(1, 0, westBonded));
        InputSpec far = InputSpec.of(
                AtomPlacement.of(20, 30, eastBonded),
                AtomPlacement.of(21, 30, westBonded));
        InputSpec reordered = InputSpec.of(
                AtomPlacement.of(1, 0, westBonded),
                AtomPlacement.of(0, 0, eastBonded));

        assertEquals(near.signature(), far.signature());
        assertEquals(near.signature(), reordered.signature());
        Inventory inventory = new Inventory();
        inventory.replenish(near, 5);

        assertEquals(5, inventory.rawCount(far));
        assertEquals(5, inventory.rawCount(reordered));
        assertEquals(5, inventory.rawTotal());
    }

    @Test
    void differentMaterialShapesKeepSeparateStock() {
        InputSpec ore = InputSpec.single(new GridPoint(0, 0), Atom.of(1));
        InputSpec differentType = InputSpec.single(new GridPoint(0, 0), Atom.of(2));
        InputSpec bondedPair = InputSpec.of(
                AtomPlacement.of(0, 0, Atom.of(1).withBond(Direction.EAST, 2)),
                AtomPlacement.of(1, 0, Atom.of(5).withBond(Direction.WEST, 2)));

        Inventory inventory = new Inventory();
        inventory.replenish(ore, 2);

        assertEquals(2, inventory.rawCount(ore));
        assertEquals(0, inventory.rawCount(differentType));
        assertEquals(0, inventory.rawCount(bondedPair));
    }

    @Test
    void rawAndProductNamespacesAreIndependent() {
        // 同一种分子既可以是原料也可以是产品，余量分别记账。
        Atom atom = Atom.of(3);
        InputSpec input = InputSpec.single(new GridPoint(0, 0), atom);
        OutputSpec output = OutputSpec.single(new GridPoint(2, 0), atom);
        Inventory inventory = new Inventory();
        inventory.replenish(input, 1);

        assertEquals(0, inventory.productCount(output));
        assertEquals(1, inventory.rawCount(input));
        assertEquals(0, inventory.takeProduct(output, 1));
        // 取产品不会动用原料，补原料也不会凭空产生产品。
        assertEquals(1, inventory.rawCount(input));
        inventory.replenish(input, 1);
        assertEquals(2, inventory.rawCount(input));
        assertEquals(0, inventory.productCount(output));
    }

    @Test
    void reportedStockSnapshotsDoNotExposeMutableState() {
        Inventory inventory = new Inventory();
        InputSpec ore = InputSpec.single(new GridPoint(0, 0), Atom.of(1));
        inventory.replenish(ore, 2);

        var raw = inventory.rawStocks();
        assertThrows(UnsupportedOperationException.class, () -> raw.put(ore.signature(), 10L));
        assertNotSame(raw, inventory.rawStocks());
        assertEquals(2, inventory.rawCount(ore));
    }

    // ---------- 输入取原料 ----------

    @Test
    void declaredStockIsInjectedIntoTheInventoryAtConstruction() {
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.single(3, new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);

        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        // 计划声明的配额在构造时注入库存，无需外部补货即可生产。
        assertEquals(3, simulator.inventory().rawCount(plan.inputs().get(0)));
        simulator.step();
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(0, 0)));
        assertEquals(2, simulator.inventory().rawCount(plan.inputs().get(0)));
        simulator.reset();
        assertEquals(2, simulator.inventory().rawCount(plan.inputs().get(0)));
    }

    @Test
    void inputWithoutDeclaredStockNeverProduces() {
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);

        simulator.run(3);

        assertEquals(0, simulator.boardSize());
        assertFalse(simulator.isHalted());
        assertEquals(0, simulator.inventory().rawTotal());
    }

    @Test
    void replenishedInputProducesOnceAndThenWaitsForItsPlaceholderToClear() {
        SimulationPlan plan = new SimulationPlan(
                List.of(),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(),
                1);
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        simulator.inventory().replenish(plan.inputs().get(0), 2);

        simulator.step();
        assertEquals(1, simulator.boardSize());
        assertEquals(1, simulator.inventory().rawCount(plan.inputs().get(0)));

        // 第二周期占位仍被占用：整份输入跳过，且不会白扣原料。
        simulator.step();
        assertEquals(1, simulator.boardSize());
        assertEquals(1, simulator.inventory().rawCount(plan.inputs().get(0)));
    }

    @Test
    void exhaustedRawStockStopsProduction() {
        SimulationPlan plan = shuttlePlan(1, List.of());
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan, ampleShuttleStock(plan, 2));

        // 每个周期搬走一格产品并消耗一份原料，因此两份原料只能走完两个周期。
        simulator.run(9);

        assertEquals(0, simulator.inventory().rawCount(plan.inputs().get(0)));
        assertEquals(2, simulator.outputCount(0));
        assertEquals(2, simulator.inventory().productCount(plan.outputs().get(0)));

        simulator.run(5);

        assertEquals(2, simulator.outputCount(0));
        assertEquals(2, simulator.inventory().productCount(plan.outputs().get(0)));
    }

    @Test
    void outputSubmitsAProductAndExternalConsumptionDoesNotRewindOutputCounts() {
        SimulationPlan plan = shuttlePlan(1, List.of());
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan, ampleShuttleStock(plan, 4));
        OutputSpec product = plan.outputs().get(0);

        // 一件产品需要经过抓取、运输、转向与释放几个周期才落到输出格。
        simulator.run(4);
        assertEquals(1, simulator.outputCount(0));
        assertEquals(1, simulator.inventory().productCount(product));

        assertEquals(1, simulator.inventory().takeProduct(product, 5));
        assertEquals(0, simulator.inventory().productCount(product));
        assertEquals(1, simulator.outputCount(0));

        // 产品被取走后不会再次入库：下一份产出才增加余量。
        simulator.run(20);
        assertEquals(4, simulator.outputCount(0));
        assertEquals(3, simulator.inventory().productCount(product));
    }

    @Test
    void glyphFailureRollsBackRawStockOfTheSameCycle() {
        IllegalStateException failure = new IllegalStateException("boom");
        String type = "test.inventory:throwing";
        try (var handle = GlyphRegistry.global().register(new GlyphDefinition<Integer>(
                type,
                List.of(new GridPoint(0, 0)),
                () -> 0,
                (context, state) -> {
                    throw failure;
                }))) {
            SimulationPlan plan = new SimulationPlan(
                    List.<MechanismSpec>of(),
                    List.<TrackSpec>of(),
                    List.of(InputSpec.single(1, new GridPoint(0, 0), sampleAtom())),
                    List.<OutputSpec>of(),
                    List.of(new GlyphSpec(type, new GridPoint(5, 5))),
                    1);
            OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
            assertEquals(1, simulator.inventory().rawCount(plan.inputs().get(0)));

            IllegalStateException thrown = assertThrows(IllegalStateException.class, simulator::step);
            assertEquals(failure, thrown);

            // 本周期已经扣掉的原料随周期回滚一起恢复。
            assertEquals(1, simulator.inventory().rawCount(plan.inputs().get(0)));
            assertEquals(0, simulator.boardSize());
            assertTrue(simulator.isHalted());
        }
    }

    @Test
    void initAndResetPreserveExternalInventory() {
        SimulationPlan plan = shuttlePlan(1, List.of());
        OrthogonalSimulator simulator = new OrthogonalSimulator(plan);
        InputSpec input = plan.inputs().get(0);
        OutputSpec output = plan.outputs().get(0);
        simulator.inventory().replenish(input, 3);

        simulator.init();
        assertEquals(2, simulator.inventory().rawCount(input));
        simulator.run(4);
        assertEquals(1, simulator.inventory().productCount(output));

        simulator.reset();

        // reset 只重置棋盘/机构/符文/计数；剩余原料和已提交产品都归外部库存管理。
        assertEquals(1, simulator.inventory().rawCount(input));
        assertEquals(1, simulator.inventory().productCount(output));
        assertEquals(0, simulator.outputCount(0));

        simulator.inventory().replenish(input, 1);
        simulator.run(4);
        assertEquals(1, simulator.outputCount(0));
        assertEquals(2, simulator.inventory().productCount(output));
    }

    @Test
    void failedConstructionDoesNotPartiallyInjectPlanStock() {
        InputSpec first = InputSpec.single(1, new GridPoint(0, 0), Atom.of(1));
        InputSpec overflowing = InputSpec.single(Long.MAX_VALUE, new GridPoint(2, 0), Atom.of(2));
        SimulationPlan plan = new SimulationPlan(
                List.of(), List.of(first, overflowing), List.of(), 1);
        Inventory inventory = new Inventory();
        inventory.replenish(overflowing, 1);

        assertThrows(IllegalArgumentException.class, () -> new OrthogonalSimulator(plan, inventory));
        assertEquals(0, inventory.rawCount(first));
        assertEquals(1, inventory.rawCount(overflowing));
    }

    // ---------- 夹具 ----------

    /** 每周期从输入搬一格原子到输出位置，消耗一份原料并提交一份产品。 */
    private static SimulationPlan shuttlePlan(long target, List<GlyphSpec> glyphs) {
        return new SimulationPlan(
                List.of(mechanism(0, 1, Direction.NORTH, ".fgrt")),
                List.of(track(0, 1, 1, 1)),
                List.of(InputSpec.single(new GridPoint(0, 0), sampleAtom())),
                List.of(OutputSpec.single(new GridPoint(1, 0), sampleAtom())),
                glyphs,
                target);
    }

    private static Inventory ampleShuttleStock(SimulationPlan plan, long quantity) {
        Inventory inventory = new Inventory();
        inventory.replenish(plan.inputs().get(0), quantity);
        return inventory;
    }

    private static MechanismSpec mechanism(
            long x, long y, Direction direction, String program) {
        return new StationarySpec(new GridPoint(x, y), direction, program);
    }

    private static TrackSpec track(long... coordinates) {
        GridPoint[] points = new GridPoint[coordinates.length / 2];
        for (int index = 0; index < points.length; index++) {
            points[index] = new GridPoint(coordinates[index * 2], coordinates[index * 2 + 1]);
        }
        return new TrackSpec(points);
    }

    private static Atom sampleAtom() {
        return Atom.of(0);
    }
}
