package io.dev.aae;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (this.atoms.isEmpty()) {
            throw new IllegalArgumentException("output must define at least one atom");
        }
        Map<GridPoint, Atom> atomsAt = new LinkedHashMap<>();
        for (AtomPlacement placement : this.atoms) {
            if (atomsAt.putIfAbsent(placement.position(), placement.atom()) != null) {
                throw new IllegalArgumentException(
                        "duplicate output atom position: " + placement.position());
            }
        }
        validateMolecule(atomsAt);
    }

    private static void validateMolecule(Map<GridPoint, Atom> atomsAt) {
        AtomGrid grid = new AtomGrid();
        for (var entry : atomsAt.entrySet()) {
            GridPoint position = entry.getKey();
            Atom atom = entry.getValue();
            grid.put(position, atom);
            byte[] bondTypes = atom.bondTypes();
            for (Direction direction : Direction.values()) {
                byte bond = bondTypes[direction.ordinal()];
                if (bond == 0) {
                    continue;
                }
                if (bond == Atom.TEMPORARY_BOND) {
                    throw invalidBond(position, direction, "temporary bonds are not allowed");
                }
                GridPoint neighbour;
                try {
                    neighbour = position.translate(direction);
                } catch (ArithmeticException overflow) {
                    throw invalidBond(position, direction, "bond position overflows grid coordinates");
                }
                Atom neighbourAtom = atomsAt.get(neighbour);
                if (neighbourAtom == null) {
                    throw invalidBond(position, direction, "bond points outside the output template");
                }
                if (neighbourAtom.bondType(direction.opposite()) != bond) {
                    throw invalidBond(position, direction, "bond is not reciprocal with " + neighbour);
                }
            }
        }

        GridPoint start = atomsAt.keySet().iterator().next();
        Molecule molecule = MoleculeBfs.moleculeAt(grid, start);
        if (molecule.size() != atomsAt.size()) {
            GridPoint disconnected = atomsAt.keySet().stream()
                    .filter(position -> !molecule.contains(position)).findFirst().orElseThrow();
            throw new IllegalArgumentException(
                    "output atom at " + disconnected + " is not connected to " + start);
        }
    }

    private static IllegalArgumentException invalidBond(
            GridPoint position, Direction direction, String reason) {
        return new IllegalArgumentException(
                "invalid output bond at " + position + " toward " + direction + ": " + reason);
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
