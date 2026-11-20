package io.dev.aae;

import java.util.*;

/**
 * 输入/输出的不可变定义：一个按绝对格点排列的原子模板。
 * 实例按计划 inputs() 或 outputs() 列表的声明下标定位，不保存实例 ID。
 */
public abstract class IOSpec {
    protected List<AtomPlacement> atoms;
    /** 库存物料签名：与模板在世界坐标上的位置无关，只描述分子形状与键位。 */
    private final MaterialSignature signature;
    protected IOSpec(List<AtomPlacement> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("I/O must define at least one atom");
        }
        this.atoms = List.copyOf(atoms);

        Map<GridPoint, Atom> atomsAt = new LinkedHashMap<>();
        for (AtomPlacement placement : this.atoms) {
            if (atomsAt.putIfAbsent(placement.position(), placement.atom()) != null) {
                throw new IllegalArgumentException(
                        "duplicate atom position: " + placement.position());
            }
        }
        validateMolecule(atomsAt);
        this.signature = MaterialSignature.of(this.atoms);
    }

    public List<AtomPlacement> atoms() {
        return Collections.unmodifiableList(atoms);
    }

    /**
     * 该模板的库存物料签名：以模板锚点为原点规范化后的分子形状。
     *
     * <p>{@link Inventory} 用它识别“同一种物料”，因此整体平移的相同模板共享同一份余量；
     * 模板在棋盘上的实际放置位置仍由 {@link #atoms()} 的绝对格点决定。</p>
     */
    public MaterialSignature signature() {
        return signature;
    }

    /** 模板的原子数量。 */
    public int size() {
        return atoms.size();
    }

    protected static void validateMolecule(Map<GridPoint, Atom> atomsAt) {
        AtomGrid grid = new AtomGrid();
        Direction[] directions = Direction.values();
        for (var entry : atomsAt.entrySet()) {
            GridPoint position = entry.getKey();
            Atom atom = entry.getValue();
            grid.put(position, atom);
            byte[] bondTypes = atom.bondTypes();
            for (Direction direction : directions) {
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
                    "atom at " + disconnected + " is not connected to " + start);
        }
    }

    private static IllegalArgumentException invalidBond(
            GridPoint position, Direction direction, String reason) {
        return new IllegalArgumentException(
                "invalid bond at " + position + " toward " + direction + ": " + reason);
    }
}
