package io.dev.aae;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一个分子：由化学键连通的原子集合，按 {@link MoleculeBfs} 的发现顺序保存。
 *
 * <p>{@code omsim} 把分子数组挂在 {@code board} 上复用，
 * 这里改成不可变值对象，避免调用方看到下一次搜索覆盖后的内容。</p>
 */
public final class Molecule {

    private static final Molecule EMPTY = new Molecule(List.of());

    private final List<AtomPlacement> atoms;
    private final Map<GridPoint, Atom> byPosition;

    Molecule(List<AtomPlacement> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        this.atoms = List.copyOf(atoms);
        Map<GridPoint, Atom> index = new LinkedHashMap<>();
        for (AtomPlacement placement : this.atoms) {
            index.put(placement.position(), placement.atom());
        }
        this.byPosition = Collections.unmodifiableMap(index);
    }

    /** 空分子：起点格点上没有原子，或起点不属于任何键。 */
    public static Molecule empty() {
        return EMPTY;
    }

    /** 按搜索发现顺序返回分子内的全部原子。 */
    public List<AtomPlacement> atoms() {
        return atoms;
    }

    /** 分子内的全部格点。 */
    public Set<GridPoint> positions() {
        return byPosition.keySet();
    }

    public int size() {
        return atoms.size();
    }

    public boolean isEmpty() {
        return atoms.isEmpty();
    }

    public boolean contains(GridPoint position) {
        return byPosition.containsKey(Objects.requireNonNull(position, "position"));
    }

    /** 返回该格点在分子内的原子；不属于本分子时返回 {@code null}。 */
    public Atom atomAt(GridPoint position) {
        return byPosition.get(Objects.requireNonNull(position, "position"));
    }

    /** 统计类型字节等于 {@code type} 的原子数量；类型编码由调用方定义。 */
    public int countOf(byte type) {
        int count = 0;
        for (AtomPlacement placement : atoms) {
            if (placement.type() == type) {
                count++;
            }
        }
        return count;
    }

    /** 分子占据的闭区间边界；空分子返回空 {@link Optional}。 */
    public Optional<GridBounds> bounds() {
        if (atoms.isEmpty()) {
            return Optional.empty();
        }
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;
        for (AtomPlacement placement : atoms) {
            GridPoint position = placement.position();
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
        }
        return Optional.of(new GridBounds(minX, minY, maxX, maxY));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Molecule molecule && atoms.equals(molecule.atoms);
    }

    @Override
    public int hashCode() {
        return atoms.hashCode();
    }

    @Override
    public String toString() {
        return "Molecule[size=" + atoms.size() + ", positions=" + positions() + "]";
    }
}
