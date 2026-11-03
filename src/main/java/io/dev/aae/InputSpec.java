package io.dev.aae;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 输入的不可变定义：一个按绝对格点排列的原子模板。
 *
 * <p>每个周期结束时，若模板占位上的全部格点都没有原子，输入就把模板原子生产到
 * 棋盘上；任一格点被占用则该周期跳过。</p>
 *
 * <p>模板原子的键位直接写在 {@link Atom} 字节里，因此带键的多原子分子
 * 可以原样生产；键必须成对互惠，否则分子搜索不会把它们连成一个分子。</p>
 */
public final class InputSpec extends IOSpec {

    public InputSpec(List<AtomPlacement> atoms) {
        super(atoms);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("input must produce at least one atom");
        }
        Set<GridPoint> positions = new HashSet<>();
        for (AtomPlacement placement : atoms) {
            Objects.requireNonNull(placement, "atoms must not contain null");
            if (!positions.add(placement.position())) {
                throw new IllegalArgumentException(
                        "duplicate input atom position: " + placement.position());
            }
        }
    }

    /** 由若干原子模板建立输入。 */
    public static InputSpec of(AtomPlacement... atoms) {
        return new InputSpec(List.of(atoms));
    }

    /** 只生产一个原子的输入。 */
    public static InputSpec single(GridPoint position, Atom atom) {
        return of(new AtomPlacement(position, atom));
    }
}
