package io.dev.aae;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 稀疏原子网格：{@link GridPoint} 到 {@link Atom} 的映射。
 *
 * <p>“无限网格 + 稀疏存储”语义，但直接用 {@link LinkedHashMap}，把布局优化留给实测。</p>
 *
 * <p>网格不保存空原子：类型字节 {@code 0} 的原子不允许存入，格点不存在即代表没有原子。</p>
 */
public final class AtomGrid {

    private final Map<GridPoint, Atom> atoms = new LinkedHashMap<>();

    /** 放入或替换一个原子。 */
    public void put(GridPoint position, Atom atom) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(atom, "atom");
        if (atom.type() == 0) {
            throw new IllegalArgumentException(
                    "grid stores only real atoms; use remove() to clear " + position);
        }
        atoms.put(position, atom);
    }

    void putAll(Map<GridPoint, Atom> map) {
        Objects.requireNonNull(map, "snapshot atom map");
        atoms.putAll(map);
    }

    /** 返回格点上的原子，没有则返回 {@code null}。 */
    public Atom get(GridPoint position) {
        return atoms.get(Objects.requireNonNull(position, "position"));
    }

    /** 返回格点上的原子，没有则返回空 {@link Optional}。 */
    public Optional<Atom> atomAt(GridPoint position) {
        return Optional.ofNullable(get(position));
    }

    public boolean contains(GridPoint position) {
        return atoms.containsKey(Objects.requireNonNull(position, "position"));
    }

    /** 移除格点上的原子；原本没有原子时返回 {@code null}。 */
    public Atom remove(GridPoint position) {
        return atoms.remove(Objects.requireNonNull(position, "position"));
    }

    /** 移除整个分子包含的全部原子，返回实际移除的数量。 */
    public int remove(Molecule molecule) {
        Objects.requireNonNull(molecule, "molecule");
        int removed = 0;
        for (AtomPlacement placement : molecule.atoms()) {
            if (atoms.remove(placement.position()) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** 清空网格。 */
    public void clear() {
        atoms.clear();
    }

    public int size() {
        return atoms.size();
    }

    public boolean isEmpty() {
        return atoms.isEmpty();
    }

    /**
     * 按插入顺序返回全部占用格点。
     *
     * <p>分子搜索按这个顺序枚举起点，因此同一网格上的结果顺序是可复现的。</p>
     */
    public Set<GridPoint> positions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(atoms.keySet()));
    }

    /** 全部原子及其格点，按插入顺序。 */
    public Map<GridPoint, Atom> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(atoms));
    }

    /** 已占用格点的闭区间边界；网格为空时返回空 {@link Optional}。 */
    public Optional<GridBounds> bounds() {
        if (atoms.isEmpty()) {
            return Optional.empty();
        }
        long minX = Long.MAX_VALUE;
        long minY = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long maxY = Long.MIN_VALUE;
        for (GridPoint position : atoms.keySet()) {
            minX = Math.min(minX, position.x());
            minY = Math.min(minY, position.y());
            maxX = Math.max(maxX, position.x());
            maxY = Math.max(maxY, position.y());
        }
        return Optional.of(new GridBounds(minX, minY, maxX, maxY));
    }

    /**
     * 在相邻两格之间建立互惠化学键。
     *
     * <p>{@code from} 在{@code direction} 上写入键，{@code from}
     * 的邻居在反方向上写入同样的键。任一格点为空时不做任何修改并返回 {@code false}。</p>
     */
    public boolean bond(GridPoint from, Direction direction, byte bondType) {
        return changeBond(from, direction, false, bondType, (byte) 0);
    }

    /**
     * 建立互惠化学键，但当任一侧在该方向上已经存在 {@code unless} 字节的键时放弃。
     *
     * <p>任一侧在该方向上已有的键字节等于 {@code unless}（且 {@code unless} 不为 0），
     * 本次写入就放弃。</p>
     *
     * @param unless 任一侧本次键合方向上已有键此值（且不为 0），本次写入就放弃。
     *
     * @return 是否真的写入了键
     */
    public boolean bond(
            GridPoint from,
            Direction direction,
            byte bondType,
            byte unless) {
        return changeBond(from, direction, false, bondType, unless);
    }

    /**
     * 无条件清除两格之间的互惠键，返回是否发生了变化。
     *
     * @param direction 指定邻居方向
     */
    public boolean unbond(GridPoint from, Direction direction) {
        return changeBond(from, direction, true, (byte) 0, (byte) 0);
    }

    private boolean changeBond(
            GridPoint from,
            Direction direction,
            boolean unbond,
            byte bondType,
            byte unless) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");

        GridPoint to = from.translate(direction);
        Atom source = atoms.get(from);
        Atom target = atoms.get(to);
        if (source == null || target == null) {
            return false;
        }
        Direction opposite = direction.opposite();

        if (unbond) {
            if (source.bondType(direction) == 0 && target.bondType(opposite) == 0) {
                return false;
            }
            atoms.put(from, source.withoutBond(direction));
            atoms.put(to, target.withoutBond(opposite));
            return true;
        }

        if (unless != 0
                && (source.bondType(direction) == unless
                || target.bondType(opposite) == unless)) {
            return false;
        }
        atoms.put(from, source.withBond(direction, bondType));
        atoms.put(to, target.withBond(opposite, bondType));
        return true;
    }

    /**
     * 无条件清掉目标与邻居之间的键。
     */
    public void unbondNeighbor(GridPoint from) {
        Objects.requireNonNull(from, "from");

        Atom source = atoms.get(from);
        if (source != null) {
            atoms.put(from, source.withoutBonds());
        }

        for (Direction direction : Direction.values()) {
            GridPoint to = from.translate(direction);
            Atom target = atoms.get(to);
            if (target != null && target.bondType(direction.opposite()) != 0) {
                atoms.put(to, target.withoutBond(direction.opposite()));
            }
        }
    }

    /**
     * 无条件清掉两格在该方向上的键字节，任一侧非零就算发生了变化。
     *
     * <p>互惠键的正常拆除用 {@link #unbond}；本方法处理单侧残留键
     *
     * @return 是否清掉了至少一个键字节
     */
    public boolean unbondBothEnds(GridPoint from, Direction direction) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");

        GridPoint to = from.translate(direction);
        Atom source = atoms.get(from);
        Atom target = atoms.get(to);
        boolean cleared = false;
        if (source != null && source.bondType(direction) != 0) {
            atoms.put(from, source.withoutBond(direction));
            cleared = true;
        }
        if (target != null && target.bondType(direction.opposite()) != 0) {
            atoms.put(to, target.withoutBond(direction.opposite()));
            cleared = true;
        }
        return cleared;
    }

    /**
     * 判断两格之间是否存在互惠的长期键。
     *
     * <p>两侧必须同时声明对方的反方向键；单侧写入的键不算连通。</p>
     */
    public boolean isBonded(GridPoint from, Direction direction) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");

        Atom source = atoms.get(from);
        if (source == null || !source.hasRealBond(direction)) {
            return false;
        }
        Atom target = atoms.get(from.translate(direction));
        return target != null && target.hasRealBond(direction.opposite());
    }

    /** 与 {@link #isBonded(GridPoint, Direction)} 相同，但可自定义认定哪些键类型算连通。 */
    public boolean isBonded(
            GridPoint from,
            Direction direction,
            BondFilter bondFilter) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(bondFilter, "bondFilter");

        Atom source = atoms.get(from);
        if (source == null || !bondFilter.test(source.bondType(direction))) {
            return false;
        }
        Atom target = atoms.get(from.translate(direction));
        return target != null && bondFilter.test(target.bondType(direction.opposite()));
    }

    @Override
    public String toString() {
        return "AtomGrid[size=" + atoms.size() + "]";
    }
}
