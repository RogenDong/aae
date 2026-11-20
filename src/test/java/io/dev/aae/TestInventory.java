package io.dev.aae;

/**
 * 测试用库存装配：为计划里的每条输入预置充足原料，让不关心库存语义的用例
 * 继续观察“占位空闲就生产”的行为。
 *
 * <p>库存专用语义（补货、配额耗尽、产品提交、事务回滚）由 InventoryTest 与
 * OrthogonalSimulatorLifecycleTest 里的专门用例覆盖，不要用这里的充足库存测试它们。</p>
 */
final class TestInventory {

    /** 充足原料数量：远大于任何用例的周期数，表现为“只要占位空闲就能生产”。 */
    static final long AMPLE_STOCK = 1_000_000L;

    private TestInventory() {
    }

    /** 建立一份按计划输入预置充足原料的库存。 */
    static Inventory ample(SimulationPlan plan) {
        Inventory inventory = new Inventory();
        for (InputSpec input : plan.inputs()) {
            inventory.replenish(input, AMPLE_STOCK);
        }
        return inventory;
    }

    /** 用预置充足原料的库存运行计划。 */
    static OrthogonalSimulator simulator(SimulationPlan plan) {
        return new OrthogonalSimulator(plan, ample(plan));
    }
}
