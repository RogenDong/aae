package io.dev.aae;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 分子连通分量搜索。
 *
 * <p>从一个原子出发做广度优先搜索，沿着化学键不断把新原子加入分子。</p>
 *
 * <p>只有两侧互为反方向的键才算连通，并且默认排除刚建立、
 * 下个周期会被清除的临时键（{@link Atom#TEMPORARY_BOND}）。</p>
 *
 * <p>搜索顺序是确定的：起点枚举顺序来自 {@link AtomGrid#positions()} 的插入顺序，
 * 邻居按 {@link Direction#values()} 顺序访问，队列为先进先出。</p>
 */
public final class MoleculeBfs {

    /** 默认的连通判据：长期保留的键，即键字节既不为 0（无键）也不为 {@link Atom#TEMPORARY_BOND}。 */
    public static final BondFilter REAL_BONDS = Atom::isRealBond;

    private MoleculeBfs() {
    }

    /**
     * 返回包含 {@code start} 的分子；该格点没有原子时返回 {@link Molecule#empty()}。
     */
    public static Molecule moleculeAt(AtomGrid grid, GridPoint start) {
        return moleculeAt(grid, start, REAL_BONDS);
    }

    /**
     * 返回包含 {@code start} 的分子，并用 {@code bondFilter} 决定哪些键类型算连通。
     *
     * <p>传入 {@code bondType -> bondType != 0} 可以把临时键也算进来，
     * 用于观察同一个半周期内刚建立但还没被清除的分子。</p>
     */
    public static Molecule moleculeAt(
            AtomGrid grid,
            GridPoint start,
            BondFilter bondFilter) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(bondFilter, "bondFilter");

        Atom startAtom = grid.get(start);
        if (startAtom == null) {
            return Molecule.empty();
        }

        List<AtomPlacement> discovered = new ArrayList<>();
        Set<GridPoint> visited = new HashSet<>();
        Deque<AtomPlacement> frontier = new ArrayDeque<>();

        visited.add(start);
        frontier.add(new AtomPlacement(start, startAtom));

        while (!frontier.isEmpty()) {
            AtomPlacement current = frontier.poll();
            discovered.add(current);

            for (Direction direction : Direction.values()) {
                if (!bondFilter.test(current.atom().bondType(direction))) {
                    continue;
                }
                GridPoint neighbourPosition = current.position().translate(direction);
                if (visited.contains(neighbourPosition)) {
                    continue;
                }
                Atom neighbour = grid.get(neighbourPosition);
                if (neighbour == null) {
                    continue;
                }
                // 要求邻居持有反方向键，否则这个原子不属于本分子。
                if (!bondFilter.test(neighbour.bondType(direction.opposite()))) {
                    continue;
                }
                visited.add(neighbourPosition);
                frontier.add(new AtomPlacement(neighbourPosition, neighbour));
            }
        }

        return new Molecule(discovered);
    }

    /**
     * 把网格上的全部原子划分成分子，按首次出现的原子顺序返回。
     *
     * <p>等价于对每个尚未访问的原子调用 {@link #moleculeAt(AtomGrid, GridPoint)}
     * 并把结果合并起来；空网格返回空列表。</p>
     */
    public static List<Molecule> molecules(AtomGrid grid) {
        return molecules(grid, REAL_BONDS);
    }

    /** 与 {@link #molecules(AtomGrid)} 相同，但使用自定义连通判据。 */
    public static List<Molecule> molecules(AtomGrid grid, BondFilter bondFilter) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(bondFilter, "bondFilter");

        List<Molecule> molecules = new ArrayList<>();
        Set<GridPoint> assigned = new HashSet<>();
        for (GridPoint position : grid.positions()) {
            if (assigned.contains(position)) {
                continue;
            }
            Molecule molecule = moleculeAt(grid, position, bondFilter);
            assigned.addAll(molecule.positions());
            molecules.add(molecule);
        }
        return molecules;
    }

    /** 两个格点是否属于同一个分子。 */
    public static boolean areConnected(AtomGrid grid, GridPoint first, GridPoint second) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(second, "second");
        return moleculeAt(grid, first).contains(second);
    }
}
