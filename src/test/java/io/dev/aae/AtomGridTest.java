package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AtomGridTest {

    private static final GridPoint ORIGIN = new GridPoint(3, -2);

    private static AtomGrid twoAtoms() {
        AtomGrid grid = new AtomGrid();
        grid.put(ORIGIN, Atom.of((byte) 1));
        grid.put(ORIGIN.translate(Direction.EAST), Atom.of((byte) 5));
        return grid;
    }

    @Test
    void storesLooksUpAndRemovesAtoms() {
        AtomGrid grid = new AtomGrid();
        assertTrue(grid.isEmpty());
        assertFalse(grid.contains(ORIGIN));
        assertNull(grid.get(ORIGIN));
        assertTrue(grid.atomAt(ORIGIN).isEmpty());

        grid.put(ORIGIN, Atom.of((byte) 7));
        assertEquals(1, grid.size());
        assertTrue(grid.contains(ORIGIN));
        assertEquals((byte) 7, grid.get(ORIGIN).type());
        assertEquals((byte) 7, grid.atomAt(ORIGIN).orElseThrow().type());

        assertEquals((byte) 7, grid.remove(ORIGIN).type());
        assertNull(grid.remove(ORIGIN));
        assertTrue(grid.isEmpty());
    }

    @Test
    void rejectsAtomsWithoutAType() {
        AtomGrid grid = new AtomGrid();
        assertThrows(IllegalArgumentException.class,
                () -> grid.put(ORIGIN, Atom.of((byte) 0)));
        assertTrue(grid.isEmpty());
    }

    @Test
    void positionsKeepInsertionOrder() {
        AtomGrid grid = new AtomGrid();
        GridPoint second = new GridPoint(0, 0);
        GridPoint first = new GridPoint(5, 5);
        grid.put(first, Atom.of((byte) 1));
        grid.put(second, Atom.of((byte) 1));

        assertEquals(List.copyOf(grid.positions()), List.of(first, second));
    }

    @Test
    void boundsFollowTheOccupiedCells() {
        AtomGrid grid = new AtomGrid();
        assertTrue(grid.bounds().isEmpty());

        grid.put(new GridPoint(-4, 7), Atom.of((byte) 1));
        grid.put(new GridPoint(2, -3), Atom.of((byte) 12));

        assertEquals(new GridBounds(-4, -3, 2, 7), grid.bounds().orElseThrow());
    }

    @Test
    void bondingWritesBothSidesMutually() {
        AtomGrid grid = twoAtoms();

        assertTrue(grid.bond(ORIGIN, Direction.EAST, (byte) 2));

        assertEquals((byte) 2, grid.get(ORIGIN).bondType(Direction.EAST));
        assertEquals((byte) 2,
                grid.get(ORIGIN.translate(Direction.EAST)).bondType(Direction.WEST));
        assertTrue(grid.isBonded(ORIGIN, Direction.EAST));
        assertTrue(grid.isBonded(ORIGIN.translate(Direction.EAST), Direction.WEST));
    }

    @Test
    void bondingOutsideTheGridChangesNothing() {
        AtomGrid grid = twoAtoms();

        assertFalse(grid.bond(ORIGIN, Direction.NORTH, (byte) 2));
        assertFalse(grid.bond(ORIGIN.translate(Direction.EAST), Direction.EAST, (byte) 2));
        assertFalse(grid.isBonded(ORIGIN, Direction.NORTH));
        assertFalse(grid.isBonded(ORIGIN, Direction.EAST));
    }

    @Test
    void unlessPreventsOverwritingAnExistingBond() {
        // 对应 add_bond 的 unless 参数：该方向已经是 2 号键时不再写入 3 号键。
        AtomGrid grid = twoAtoms();
        assertTrue(grid.bond(ORIGIN, Direction.EAST, (byte) 2));

        assertFalse(grid.bond(ORIGIN, Direction.EAST, (byte) 3, (byte) 2));
        assertEquals((byte) 2, grid.get(ORIGIN).bondType(Direction.EAST));

        // unless 命中邻居一侧时同样放弃。
        assertFalse(grid.bond(ORIGIN, Direction.EAST, (byte) 5, (byte) 2));
        assertEquals((byte) 2,
                grid.get(ORIGIN.translate(Direction.EAST)).bondType(Direction.WEST));
    }

    @Test
    void unlessNoneNeverBlocksABond() {
        AtomGrid grid = twoAtoms();
        // 默认重载把 unless 设为 0，不能因此拒绝新键。
        assertTrue(grid.bond(ORIGIN, Direction.EAST, (byte) 1, (byte) 0));
        assertEquals((byte) 1, grid.get(ORIGIN).bondType(Direction.EAST));
    }

    @Test
    void bondingCanBeReplacedAndCleared() {
        AtomGrid grid = twoAtoms();
        grid.bond(ORIGIN, Direction.EAST, (byte) 2);
        grid.bond(ORIGIN, Direction.EAST, (byte) 4);
        assertEquals((byte) 4, grid.get(ORIGIN).bondType(Direction.EAST));
        assertEquals((byte) 4,
                grid.get(ORIGIN.translate(Direction.EAST)).bondType(Direction.WEST));

        assertTrue(grid.unbond(ORIGIN, Direction.EAST));
        assertEquals((byte) 0, grid.get(ORIGIN).bondType(Direction.EAST));
        assertEquals((byte) 0,
                grid.get(ORIGIN.translate(Direction.EAST)).bondType(Direction.WEST));
        assertFalse(grid.unbond(ORIGIN, Direction.EAST));
    }

    @Test
    void oneSidedBondsDoNotCountAsConnected() {
        AtomGrid grid = new AtomGrid();
        grid.put(ORIGIN, Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2));
        grid.put(ORIGIN.translate(Direction.EAST), Atom.of((byte) 1));

        assertFalse(grid.isBonded(ORIGIN, Direction.EAST));
        // 判据再宽松也要求两侧同时声明键，否则邻居随时可以单方面“被连上”。
        assertFalse(grid.isBonded(ORIGIN, Direction.EAST,
                bondType -> bondType != 0));
        assertTrue(grid.get(ORIGIN).hasBond(Direction.EAST));
    }

    @Test
    void isBondedCanBeToldToAcceptTemporaryBonds() {
        AtomGrid grid = twoAtoms();
        grid.bond(ORIGIN, Direction.EAST, Atom.TEMPORARY_BOND);

        assertFalse(grid.isBonded(ORIGIN, Direction.EAST));
        assertTrue(grid.isBonded(ORIGIN, Direction.EAST, bondType -> bondType != 0));
    }

    @Test
    void removingAMoleculeClearsEveryAtomItOwns() {
        AtomGrid grid = new AtomGrid();
        for (int index = 0; index < 4; index++) {
            grid.put(new GridPoint(index, 0), Atom.of((byte) 1));
        }
        for (int index = 0; index + 1 < 4; index++) {
            grid.bond(new GridPoint(index, 0), Direction.EAST, (byte) 2);
        }
        grid.put(new GridPoint(0, 9), Atom.of((byte) 14));

        Molecule molecule = MoleculeBfs.moleculeAt(grid, new GridPoint(0, 0));
        assertEquals(4, grid.remove(molecule));

        assertEquals(1, grid.size());
        assertTrue(grid.contains(new GridPoint(0, 9)));
        assertEquals(0, grid.remove(molecule));
    }

    @Test
    void clearEmptiesTheGrid() {
        AtomGrid grid = twoAtoms();
        grid.clear();
        assertTrue(grid.isEmpty());
        assertEquals(0, grid.asMap().size());
    }
}
