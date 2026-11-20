package io.dev.aae;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 原料与产品分离的可变库存。
 *
 * <p>库存按 {@link MaterialSignature} 记账：原料由 {@link InputSpec} 的模板签名识别，
 * 产品由 {@link OutputSpec} 的模板签名识别，两个命名空间互不相通——同一种分子既可以是
 * 某条输入的原料，也可以是另一条输出的产品，但余量分别计数。整体平移的相同模板共享同一份
 * 余量，因此库存不区分“从哪个输入端口补的货”。</p>
 *
 * <p>原料只能通过 {@link #replenish(InputSpec, long)} 补货，由 {@link OrthogonalSimulator}
 * 的输入阶段扣减；产品由模拟器的输出阶段提交入库，只能由外部调用方通过
 * {@link #takeProduct(OutputSpec, long)} 取走消费。库存从不出现负余量：取出数量多于余量时
 * 只取走现有部分。</p>
 *
 * <p>库存本身是运行态资源，不属于 {@link SimulationPlan} 的定义内容；模拟器在构造时把
 * 计划声明的初始原料配额注入进来，之后由调用方通过补货与取货接口维护。整个库存
 * 参与模拟器的周期事务：周期回滚会连同库存一起恢复到周期开始时的状态。</p>
 */
public final class Inventory {

    /** 原料余量，按物料签名索引；余量为 0 的条目会被移除。 */
    private final Map<MaterialSignature, Long> rawStocks = new LinkedHashMap<>();

    /** 产品余量，按物料签名索引；余量为 0 的条目会被移除。 */
    private final Map<MaterialSignature, Long> productStocks = new LinkedHashMap<>();

    /**
     * 补货：给指定输入模板对应的原料增加数量。
     *
     * @param input 输入模板，按它的 {@link IOSpec#signature()} 记账
     * @param quantity 增加的数量，必须大于 {@code 0}
     * @throws IllegalArgumentException 数量不为正，或累计余量超出 {@code long} 范围
     */
    public void replenish(InputSpec input, long quantity) {
        Objects.requireNonNull(input, "input");
        add(rawStocks, input.signature(), quantity, "raw");
    }

    /**
     * 指定输入模板对应原料的当前余量。
     */
    public long rawCount(InputSpec input) {
        return count(rawStocks, Objects.requireNonNull(input, "input").signature());
    }

    /**
     * 指定输出模板对应产品的当前余量。
     */
    public long productCount(OutputSpec output) {
        return count(productStocks, Objects.requireNonNull(output, "output").signature());
    }

    /**
     * 从原料库存取出指定数量；返回实际取出的数量，最多取走现有余量。
     *
     * @param quantity 期望取出的数量，不能为负
     * @return 实际取出的数量，余量不足时小于 {@code quantity}
     */
    public long takeRaw(InputSpec input, long quantity) {
        return take(rawStocks, Objects.requireNonNull(input, "input").signature(), quantity);
    }

    /**
     * 从产品库存取走指定数量，供外部系统消费产品。
     *
     * @param quantity 期望取出的数量，不能为负
     * @return 实际取出的数量，余量不足时小于 {@code quantity}，绝不为负
     */
    public long takeProduct(OutputSpec output, long quantity) {
        return take(productStocks, Objects.requireNonNull(output, "output").signature(), quantity);
    }

    /** 全部原料余量之和。 */
    public long rawTotal() {
        return total(rawStocks);
    }

    /** 全部产品余量之和。 */
    public long productTotal() {
        return total(productStocks);
    }

    /** 原料余量的独立快照，key 为物料签名；修改返回的映射不影响库存。 */
    public Map<MaterialSignature, Long> rawStocks() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(rawStocks));
    }

    /** 产品余量的独立快照，key 为物料签名；修改返回的映射不影响库存。 */
    public Map<MaterialSignature, Long> productStocks() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(productStocks));
    }

    /** 清空全部原料与产品。 */
    public void clear() {
        rawStocks.clear();
        productStocks.clear();
    }

    /**
     * 原子注入一组计划初始原料配额。
     *
     * <p>先合并并校验全部配额及累计溢出，再修改库存；任何一条配额无效时库存保持不变，
     * 避免模拟器构造失败留下部分补货。</p>
     */
    void replenishInitialStocks(List<InputSpec> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Map<MaterialSignature, Long> updated = new LinkedHashMap<>();
        for (InputSpec input : inputs) {
            long quantity = input.stock();
            if (quantity == 0) {
                continue;
            }
            MaterialSignature signature = input.signature();
            updated.putIfAbsent(signature, count(rawStocks, signature));
            add(updated, signature, quantity, "raw");
        }
        rawStocks.putAll(updated);
    }

    /**
     * 扣减一份原料；原料不足时不做任何修改并返回 {@code false}。
     *
     * <p>供模拟器输入阶段使用：只有扣减成功才允许把模板放上棋盘。</p>
     */
    boolean consumeRaw(InputSpec input, long quantity) {
        MaterialSignature signature = Objects.requireNonNull(input, "input").signature();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        if (count(rawStocks, signature) < quantity) {
            return false;
        }
        take(rawStocks, signature, quantity);
        return true;
    }

    /** 提交一份产品入库；供模拟器输出阶段在成功匹配并移除棋盘原子之后调用。 */
    void submitProduct(OutputSpec output) {
        add(productStocks, Objects.requireNonNull(output, "output").signature(), 1, "product");
    }

    /** 捕获当前余量，供模拟器周期事务回滚。 */
    Snapshot captureState() {
        return new Snapshot(rawStocks, productStocks);
    }

    /** 恢复到之前捕获的余量。 */
    void restoreState(Snapshot snapshot) {
        rawStocks.clear();
        rawStocks.putAll(snapshot.raw());
        productStocks.clear();
        productStocks.putAll(snapshot.products());
    }

    private static void add(
            Map<MaterialSignature, Long> stocks,
            MaterialSignature signature,
            long quantity,
            String kind) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        long current = stocks.getOrDefault(signature, 0L);
        if (Long.MAX_VALUE - current < quantity) {
            throw new IllegalArgumentException(kind + " stock overflow for signature of size "
                    + signature.size());
        }
        stocks.put(signature, current + quantity);
    }

    private static long count(Map<MaterialSignature, Long> stocks, MaterialSignature signature) {
        return stocks.getOrDefault(signature, 0L);
    }

    private static long take(
            Map<MaterialSignature, Long> stocks,
            MaterialSignature signature,
            long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must be non-negative: " + quantity);
        }
        long current = count(stocks, signature);
        long taken = Math.min(current, quantity);
        if (taken == 0) {
            return 0;
        }
        long remaining = current - taken;
        if (remaining == 0) {
            stocks.remove(signature);
        } else {
            stocks.put(signature, remaining);
        }
        return taken;
    }

    private static long total(Map<MaterialSignature, Long> stocks) {
        long sum = 0;
        for (long value : stocks.values()) {
            sum = Math.addExact(sum, value);
        }
        return sum;
    }

    /**
     * 库存余量快照；不可变，可安全地跨周期保存与恢复。
     */
    record Snapshot(Map<MaterialSignature, Long> raw, Map<MaterialSignature, Long> products) {

        Snapshot {
            raw = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(raw, "raw")));
            products = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(products, "products")));
        }
    }
}
