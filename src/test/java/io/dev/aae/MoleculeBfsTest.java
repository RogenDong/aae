package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoleculeBfsTest {

    /** 沿东方向连成一条直线分子。 */
    private static AtomGrid chain(int length) {
        AtomGrid grid = new AtomGrid();
        for (int index = 0; index < length; index++) {
            grid.put(new GridPoint(index, 0), Atom.of((byte) 1));
        }
        for (int index = 0; index + 1 < length; index++) {
            grid.bond(new GridPoint(index, 0), Direction.EAST, (byte) 2);
        }
        return grid;
    }

    private static List<GridPoint> positionsOf(Molecule molecule) {
        return molecule.atoms().stream().map(AtomPlacement::position).toList();
    }

    @Test
    void startingOnAnEmptyCellYieldsAnEmptyMolecule() {
        AtomGrid grid = chain(3);

        Molecule molecule = MoleculeBfs.moleculeAt(grid, new GridPoint(50, 50));

        assertTrue(molecule.isEmpty());
        assertEquals(0, molecule.size());
        assertEquals(List.of(), molecule.atoms());
        assertTrue(molecule.bounds().isEmpty());
        assertEquals(Molecule.empty(), molecule);
        assertFalse(molecule.contains(new GridPoint(0, 0)));
    }

    @Test
    void emptyGridPartitionsIntoNothing() {
        assertEquals(List.of(), MoleculeBfs.molecules(new AtomGrid()));
    }

    @Test
    void isolatedAtomIsAMoleculeOfOne() {
        AtomGrid grid = new AtomGrid();
        GridPoint position = new GridPoint(-7, 4);
        grid.put(position, Atom.of((byte) 16));

        Molecule molecule = MoleculeBfs.moleculeAt(grid, position);

        assertEquals(1, molecule.size());
        assertTrue(molecule.contains(position));
        assertEquals((byte) 16, molecule.atomAt(position).type());
        assertNull(molecule.atomAt(new GridPoint(0, 0)));
        assertEquals(new GridBounds(-7, 4, -7, 4), molecule.bounds().orElseThrow());
    }

    @Test
    void breadthFirstSearchDiscoversAChainInOrder() {
        AtomGrid grid = chain(5);

        Molecule molecule = MoleculeBfs.moleculeAt(grid, new GridPoint(0, 0));

        assertEquals(5, molecule.size());
        assertEquals(
                List.of(
                        new GridPoint(0, 0),
                        new GridPoint(1, 0),
                        new GridPoint(2, 0),
                        new GridPoint(3, 0),
                        new GridPoint(4, 0)),
                positionsOf(molecule));
    }

    @Test
    void everyAtomOfAChainReachesTheWholeChain() {
        AtomGrid grid = chain(6);

        for (int index = 0; index < 6; index++) {
            Molecule molecule = MoleculeBfs.moleculeAt(grid, new GridPoint(index, 0));
            assertEquals(6, molecule.size(), "起点 " + index + " 应该覆盖整条链");
        }
    }

    @Test
    void breadthFirstSearchFollowsBranchingBonds() {
        AtomGrid grid = new AtomGrid();
        GridPoint centre = new GridPoint(0, 0);
        grid.put(centre, Atom.of((byte) 10));
        for (Direction direction : Direction.values()) {
            grid.put(centre.translate(direction), Atom.of((byte) 2));
            grid.bond(centre, direction, (byte) 2);
        }

        Molecule molecule = MoleculeBfs.moleculeAt(grid, centre);

        assertEquals(5, molecule.size());
        assertEquals(1, molecule.countOf((byte) 10));
        assertEquals(4, molecule.countOf((byte) 2));
        assertEquals(
                List.of(
                        centre,
                        centre.translate(Direction.NORTH),
                        centre.translate(Direction.EAST),
                        centre.translate(Direction.SOUTH),
                        centre.translate(Direction.WEST)),
                positionsOf(molecule));
    }

    @Test
    void bondsClosingIntoARingAreTraversedOnce() {
        // (0,0)-(1,0)-(1,1)-(0,1)-(0,0) 构成一个四元环。
        AtomGrid grid = new AtomGrid();
        List<GridPoint> corners = List.of(
                new GridPoint(0, 0), new GridPoint(1, 0),
                new GridPoint(1, 1), new GridPoint(0, 1));
        for (GridPoint corner : corners) {
            grid.put(corner, Atom.of((byte) 3));
        }
        grid.bond(corners.get(0), Direction.EAST, (byte) 2);
        grid.bond(corners.get(1), Direction.SOUTH, (byte) 2);
        grid.bond(corners.get(2), Direction.WEST, (byte) 2);
        grid.bond(corners.get(3), Direction.NORTH, (byte) 2);

        Molecule molecule = MoleculeBfs.moleculeAt(grid, corners.get(0));

        assertEquals(4, molecule.size());
        for (GridPoint corner : corners) {
            assertTrue(molecule.contains(corner), corner + " 应在环内");
        }
        // 环只有一个连通分量：VISITED 语义必须防止绕回起点重复计数。
        List<Molecule> molecules = MoleculeBfs.molecules(grid);
        assertEquals(1, molecules.size());
        assertEquals(4, molecules.get(0).size());
    }

    @Test
    void disconnectedGroupsBecomeSeparateMolecules() {
        AtomGrid grid = new AtomGrid();
        for (int index = 0; index < 2; index++) {
            grid.put(new GridPoint(index, 0), Atom.of((byte) 1));
        }
        grid.bond(new GridPoint(0, 0), Direction.EAST, (byte) 2);
        for (int index = 10; index < 13; index++) {
            grid.put(new GridPoint(index, 0), Atom.of((byte) 9));
        }
        grid.bond(new GridPoint(10, 0), Direction.EAST, (byte) 2);
        grid.bond(new GridPoint(11, 0), Direction.EAST, (byte) 2);
        grid.put(new GridPoint(0, 40), Atom.of((byte) 14));

        List<Molecule> molecules = MoleculeBfs.molecules(grid);

        assertEquals(3, molecules.size());
        assertEquals(List.of(2, 3, 1), molecules.stream().map(Molecule::size).toList());
        assertEquals(new GridPoint(0, 0), molecules.get(0).atoms().get(0).position());
        assertEquals(new GridPoint(10, 0), molecules.get(1).atoms().get(0).position());
        assertEquals(new GridPoint(0, 40), molecules.get(2).atoms().get(0).position());
    }

    @Test
    void cellsWithoutNeighboursAreTheirOwnMolecules() {
        AtomGrid grid = new AtomGrid();
        for (int index = 0; index < 5; index++) {
            grid.put(new GridPoint(index * 3, 0), Atom.of((byte) 11));
        }

        List<Molecule> molecules = MoleculeBfs.molecules(grid);

        assertEquals(5, molecules.size());
        for (Molecule molecule : molecules) {
            assertEquals(1, molecule.size());
        }
    }

    @Test
    void partitioningCoversEveryAtomExactlyOnce() {
        AtomGrid grid = chain(4);
        GridPoint left = new GridPoint(0, 7);
        GridPoint right = new GridPoint(1, 7);
        grid.put(left, Atom.of((byte) 7));
        grid.put(right, Atom.of((byte) 8));
        grid.bond(left, Direction.EAST, (byte) 2);

        int covered = 0;
        Set<GridPoint> seen = new HashSet<>();
        for (Molecule molecule : MoleculeBfs.molecules(grid)) {
            covered += molecule.size();
            for (GridPoint position : molecule.positions()) {
                assertTrue(seen.add(position), position + " 被两个分子同时包含");
            }
        }

        assertEquals(6, grid.size());
        assertEquals(grid.size(), covered);
        assertEquals(grid.positions(), seen);
    }

    @Test
    void aOneSidedBondDoesNotJoinTwoAtoms() {
        AtomGrid grid = new AtomGrid();
        GridPoint left = new GridPoint(0, 0);
        GridPoint right = new GridPoint(1, 0);
        grid.put(left, Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2));
        grid.put(right, Atom.of((byte) 1));

        assertEquals(1, MoleculeBfs.moleculeAt(grid, left).size());
        assertEquals(1, MoleculeBfs.moleculeAt(grid, right).size());
        assertEquals(2, MoleculeBfs.molecules(grid).size());
        assertFalse(MoleculeBfs.areConnected(grid, left, right));
    }

    @Test
    void mismatchedBondBytesStillConnectWhenBothSidesDeclareABond() {
        // get_molecule 只检查邻居是否持有反方向的长期键，不要求两侧声明同一个字节。
        AtomGrid grid = new AtomGrid();
        GridPoint left = new GridPoint(0, 0);
        GridPoint right = new GridPoint(1, 0);
        grid.put(left, Atom.of((byte) 1).withBond(Direction.EAST, (byte) 2));
        grid.put(right, Atom.of((byte) 1).withBond(Direction.WEST, (byte) 5));

        assertEquals(2, MoleculeBfs.moleculeAt(grid, left).size());
        assertTrue(MoleculeBfs.areConnected(grid, left, right));
    }

    @Test
    void recentBondsAreExcludedByDefaultButCanBeTraversedOnRequest() {
        AtomGrid grid = new AtomGrid();
        GridPoint left = new GridPoint(0, 0);
        GridPoint right = new GridPoint(1, 0);
        grid.put(left, Atom.of((byte) 1));
        grid.put(right, Atom.of((byte) 1));
        grid.bond(left, Direction.EAST, Atom.TEMPORARY_BOND);

        // 参考 omsim 的 get_molecule 使用 BOND_LOW_BITS & ~RECENT_BONDS。
        assertEquals(1, MoleculeBfs.moleculeAt(grid, left).size());
        assertEquals(2, MoleculeBfs.molecules(grid).size());

        // 想观察刚建立、尚未清除的临时键时，显式放宽判据。
        assertEquals(2, MoleculeBfs.moleculeAt(
                grid, left, bondType -> bondType != 0).size());
        assertEquals(1, MoleculeBfs.molecules(
                grid, bondType -> bondType != 0).size());
    }

    @Test
    void mixingRecentAndRealBondsOnlySplitsAtTheRecentLink() {
        AtomGrid grid = chain(3);
        grid.put(new GridPoint(3, 0), Atom.of((byte) 1));
        grid.bond(new GridPoint(2, 0), Direction.EAST, Atom.TEMPORARY_BOND);

        assertEquals(3, MoleculeBfs.moleculeAt(grid, new GridPoint(0, 0)).size());
        assertEquals(1, MoleculeBfs.moleculeAt(grid, new GridPoint(3, 0)).size());
        assertEquals(4, MoleculeBfs.moleculeAt(
                grid, new GridPoint(0, 0), bondType -> bondType != 0).size());
    }

    @Test
    void veryLongMoleculesDoNotExhaustTheStack() {
        // 广度优先搜索使用显式队列；递归实现会在这条链上栈溢出。
        int length = 50_000;
        AtomGrid grid = chain(length);

        Molecule molecule = MoleculeBfs.moleculeAt(grid, new GridPoint(0, 0));

        assertEquals(length, molecule.size());
        assertEquals(1, MoleculeBfs.molecules(grid).size());
        assertEquals(new GridBounds(0, 0, length - 1, 0), molecule.bounds().orElseThrow());
    }

    @Test
    void searchDoesNotMutateTheGrid() {
        AtomGrid grid = chain(4);
        Map<GridPoint, Atom> before = grid.asMap();

        MoleculeBfs.molecules(grid);

        // 这里的搜索必须完全不改动原子本身。
        assertEquals(before, grid.asMap());
        for (Direction direction : Direction.values()) {
            assertEquals(direction == Direction.EAST ? (byte) 2 : (byte) 0,
                    grid.get(new GridPoint(0, 0)).bondType(direction));
        }
    }

    @Test
    void moleculeCountsAndBoundsDescribeTheContents() {
        AtomGrid grid = new AtomGrid();
        List<GridPoint> cells = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            GridPoint position = new GridPoint(index, -5);
            cells.add(position);
            grid.put(position, Atom.of(index == 1 ? (byte) 10 : (byte) 1));
        }
        grid.bond(cells.get(0), Direction.EAST, (byte) 2);
        grid.bond(cells.get(1), Direction.EAST, (byte) 2);

        Molecule molecule = MoleculeBfs.moleculeAt(grid, cells.get(0));

        assertEquals(3, molecule.size());
        assertEquals(2, molecule.countOf((byte) 1));
        assertEquals(1, molecule.countOf((byte) 10));
        assertEquals(0, molecule.countOf((byte) 7));
        assertEquals(new GridBounds(0, -5, 2, -5), molecule.bounds().orElseThrow());
        assertEquals(Set.copyOf(cells), molecule.positions());
    }

    @Test
    void placementTranslateKeepsTheAtomAndMovesThePosition() {
        Atom atom = Atom.of((byte) 4).withBond(Direction.NORTH, (byte) 2);
        AtomPlacement placement = new AtomPlacement(new GridPoint(2, 2), atom);

        AtomPlacement moved = placement.translate(Direction.NORTH);

        assertEquals(new GridPoint(2, 1), moved.position());
        assertEquals(atom, moved.atom());
        assertEquals(new GridPoint(2, 2), placement.position());
    }
}
