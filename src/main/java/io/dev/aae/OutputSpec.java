package io.dev.aae;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 输出的不可变定义：一个按绝对格点排列的原子模板。
 *
 * <p>每个周期结束时，模拟器检查输出锚点格点（模板的第一个原子位置）上的分子：
 * 分子大小必须与模板完全一致，每个原子必须与同格点的模板原子类型相同、长期键相同，
 * 并且没有任何原子被机构持有完全匹配的分子被从棋盘移除，该输出的计数加一。</p>
 *
 * <p>被抓住的分子不会被消费，临时键（{@link Atom#TEMPORARY_BOND}）不参与比较。
 * 模板必须是一个由长期键连通的单个分子，因为分子搜索从锚点出发只沿键扩展。</p>
 */
public final class OutputSpec extends IOSpec {

    public OutputSpec(String id, List<AtomPlacement> atoms) {
        super(id, atoms);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("output must define at least one atom");
        }
        Set<GridPoint> positions = new HashSet<>();
        for (AtomPlacement placement : atoms) {
            Objects.requireNonNull(placement, "atoms must not contain null");
            if (!positions.add(placement.position())) {
                throw new IllegalArgumentException(
                        "duplicate output atom position: " + placement.position());
            }
        }
    }

    /** 由若干原子模板建立输出。 */
    public static OutputSpec of(String id, AtomPlacement... atoms) {
        return new OutputSpec(id, List.of(atoms));
    }

    /** 只匹配一个原子的输出。 */
    public static OutputSpec single(String id, GridPoint position, Atom atom) {
        return of(id, new AtomPlacement(position, atom));
    }

    /** 输出匹配时使用的锚点格点，即模板的第一个原子位置。 */
    public GridPoint anchor() {
        return atoms.get(0).position();
    }

    /** 模板中指定格点上的原子；不存在时返回 {@code null}。 */
    public Atom atomAt(GridPoint position) {
        for (AtomPlacement placement : atoms) {
            if (placement.position().equals(position)) {
                return placement.atom();
            }
        }
        return null;
    }
}
