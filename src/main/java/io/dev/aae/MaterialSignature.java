package io.dev.aae;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 库存物料的不可变签名：将按 x、y 字典序最小的模板格点平移到原点，再按格点排序。
 *
 * <p>签名只描述“物料种类”，不包含模板在世界坐标上的位置：两个原子类型、键位和相对
 * 排布完全相同、只是整体平移的模板得到相等的签名，因此在 {@link Inventory} 里共享同一份
 * 余量。签名不做旋转归一化，原子和键的方向按模板原样保留。</p>
 *
 * <p>签名按完整 {@link Atom}（类型与四方向键）比较，所以原子类型相同但键不同的分子
 * 不会互相顶替。</p>
 *
 * @param atoms 以锚点为原点规范化后的原子，按格点、原子类型、键位排序
 */
public record MaterialSignature(List<AtomPlacement> atoms) {

    private static final Comparator<GridPoint> GRID_ORDER =
            Comparator.comparingLong(GridPoint::x).thenComparingLong(GridPoint::y);

    /** 规范化排序：先格点，再原子类型与键位，保证声明顺序不同的相同形状相等。 */
    private static final Comparator<AtomPlacement> NORMALIZED_ORDER =
            Comparator.comparing(AtomPlacement::position, GRID_ORDER)
                    .thenComparingInt(placement -> placement.atom().type())
                    .thenComparingInt(placement -> placement.atom().bondCodes());

    public MaterialSignature {
        Objects.requireNonNull(atoms, "atoms");
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("material signature must define at least one atom");
        }
        atoms = List.copyOf(atoms);
    }

    /**
     * 由模板原子建立签名：使用按 x、y 字典序最小的格点作为锚点，与声明顺序无关。
     *
     * @param atoms 模板原子，不能为空
     * @throws IllegalArgumentException 原子列表为空，或坐标差超出 {@code long} 范围
     */
    public static MaterialSignature of(List<AtomPlacement> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("material signature must define at least one atom");
        }
        GridPoint anchor = atoms.stream()
                .map(AtomPlacement::position)
                .min(GRID_ORDER)
                .orElseThrow();
        List<AtomPlacement> normalized = new ArrayList<>(atoms.size());
        for (AtomPlacement placement : atoms) {
            GridPoint position = placement.position();
            long x;
            long y;
            try {
                x = Math.subtractExact(position.x(), anchor.x());
                y = Math.subtractExact(position.y(), anchor.y());
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(
                        "atom at " + position + " is too far from anchor " + anchor
                                + " to normalize into a material signature");
            }
            normalized.add(new AtomPlacement(new GridPoint(x, y), placement.atom()));
        }
        normalized.sort(NORMALIZED_ORDER);
        return new MaterialSignature(normalized);
    }

    /** 签名包含的原子数量。 */
    public int size() {
        return atoms.size();
    }
}
