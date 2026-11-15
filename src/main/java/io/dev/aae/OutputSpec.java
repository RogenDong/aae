package io.dev.aae;

import java.util.List;

/**
 * 输出的不可变定义：一个按绝对格点排列的原子模板。
 *
 * <p>模板必须定义一个完整、连通的单个分子：键不能是临时键或指向模板外，且每条键都必须
 * 与相邻原子的反方向键编码相等。每个半周期的符文阶段之后，模拟器逐个检查模板位置上的
 * Atom 值和抓握状态，全部匹配后按模板坐标移除原子，该输出的计数加一。</p>
 *
 * <p>被抓住的分子不会被消费。Atom 按完整值比较，因此临时键不会与无键匹配。</p>
 */
public final class OutputSpec extends IOSpec {

    public OutputSpec(List<AtomPlacement> atoms) {
        super(atoms);
    }

    /** 由若干原子模板建立输出。 */
    public static OutputSpec of(AtomPlacement... atoms) {
        return new OutputSpec(List.of(atoms));
    }

    /** 只匹配一个原子的输出。 */
    public static OutputSpec single(GridPoint position, Atom atom) {
        return of(new AtomPlacement(position, atom));
    }

    /** 模板的第一个原子位置；运行期输出匹配直接遍历全部模板位置。 */
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
