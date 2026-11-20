package io.dev.aae;

import java.util.List;

/**
 * 输入的不可变定义：一个按绝对格点排列的原子模板，以及它从库存取料的原料配额。
 *
 * <p>每个周期结束时，若模板占位上的全部格点都没有原子、且库存里还有该模板对应
 * {@link MaterialSignature} 的原料，输入就从库存扣减一份原料并把模板原子生产到棋盘上；
 * 占位被占用或原料不足时该周期跳过，且不扣库存。</p>
 *
 * <p>模板原子的键位直接写在 {@link Atom} 字节里，因此带键的多原子分子
 * 可以原样生产；键必须成对互惠，否则分子搜索不会把它们连成一个分子。</p>
 *
 * <p>{@link #stock()} 是声明在计划里的初始原料配额，模拟器构造时注入库存；未声明配额
 * （默认 {@code 0}）的输入不会生产，必须由调用方在运行前通过
 * {@link Inventory#replenish(InputSpec, long)} 补货。</p>
 */
public final class InputSpec extends IOSpec {

    /** 未声明初始配额：构造后必须外部补货，否则不会生产。 */
    public static final long NO_STOCK = 0;

    private final long stock;

    public InputSpec(List<AtomPlacement> atoms) {
        this(NO_STOCK, atoms);
    }

    /**
     * @param stock 初始原料配额，模拟器构造时注入库存；必须是 {@code 0} 或正数
     * @param atoms 模板原子
     * @throws IllegalArgumentException 原子列表为空，或配额为负
     */
    public InputSpec(long stock, List<AtomPlacement> atoms) {
        super(atoms);
        if (stock < 0) {
            throw new IllegalArgumentException("input stock must be non-negative: " + stock);
        }
        this.stock = stock;
    }

    /** 声明在计划里的初始原料配额；运行期余量由 {@link Inventory#rawCount(InputSpec)} 查询。 */
    public long stock() {
        return stock;
    }

    /** 由若干原子模板建立输入，初始配额为 {@link #NO_STOCK}。 */
    public static InputSpec of(AtomPlacement... atoms) {
        return new InputSpec(List.of(atoms));
    }

    /** 由若干原子模板建立带初始原料配额的输入。 */
    public static InputSpec of(long stock, AtomPlacement... atoms) {
        return new InputSpec(stock, List.of(atoms));
    }

    /** 只生产一个原子的输入，初始配额为 {@link #NO_STOCK}。 */
    public static InputSpec single(GridPoint position, Atom atom) {
        return of(new AtomPlacement(position, atom));
    }

    /** 只生产一个原子的输入，并声明初始原料配额。 */
    public static InputSpec single(long stock, GridPoint position, Atom atom) {
        return of(stock, new AtomPlacement(position, atom));
    }
}
