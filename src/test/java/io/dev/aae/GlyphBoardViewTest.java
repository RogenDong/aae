package io.dev.aae;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GlyphBoardViewTest {
    private static final GridPoint ORIGIN = new GridPoint(0, 0);
    private final AtomGrid grid = new AtomGrid();
    private final Map<GridPoint, Long> entities = new HashMap<>();
    private final Map<Long, GridPoint> positions = new HashMap<>();
    private final Map<Long, Integer> grabs = new HashMap<>();
    private final Set<Long> held = new HashSet<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final GlyphBoardView view = new GlyphBoardView(
            grid, entities, positions, grabs, () -> held, nextId::getAndIncrement, Set.of());

    private void bind(List<GridPoint> shape) {
        GlyphDefinition<Void> definition = GlyphDefinition.of("test:view", shape, context -> {});
        view.bind(new GlyphInstance<>(new GlyphSpec("test:view", ORIGIN), definition, null));
    }

    private List<Runnable> operations(int index) {
        return List.of(
                () -> view.atomAt(index), () -> view.isOccupied(index),
                () -> view.entityAt(index), () -> view.moleculeAt(index),
                () -> view.grabCountAt(index), () -> view.isBonded(index, Direction.EAST),
                () -> view.bondedDirections(index), () -> view.wasJustUnbonded(index, Direction.EAST),
                () -> view.setType(index, (byte) 2), () -> view.spawn(index, Atom.of((byte) 1)),
                () -> view.queueSpawn(index, Atom.of((byte) 1)), () -> view.remove(index),
                () -> view.bond(index, Direction.EAST, (byte) 1),
                () -> view.unbond(index, Direction.EAST));
    }

    @Test
    void everyOperationChecksWindowThenBindingThenIndex() {
        operations(-1).forEach(op -> assertThrows(GlyphExecutionException.class, op::run));
        bind(List.of(ORIGIN));
        for (int index : new int[]{-1, 1}) {
            operations(index).forEach(op -> {
                var error = assertThrows(IndexOutOfBoundsException.class, op::run);
                assertTrue(error.getMessage().contains("test:view"));
                assertTrue(error.getMessage().contains("count 1"));
            });
        }
        view.invalidate();
        for (int index : new int[]{-1, 0, 1}) {
            operations(index).forEach(op -> assertThrows(GlyphExecutionException.class, op::run));
        }
    }

    @Test
    void directionalOperationsStayInsideShapeButMoleculeQueryIsComplete() {
        GridPoint east = ORIGIN.translate(Direction.EAST);
        GridPoint west = ORIGIN.translate(Direction.WEST);
        bind(List.of(ORIGIN, east));
        view.spawn(0, Atom.of((byte) 1));
        view.spawn(1, Atom.of((byte) 2));
        grid.put(west, Atom.of((byte) 3));
        grid.bond(ORIGIN, Direction.WEST, (byte) 1);
        assertFalse(view.isBonded(0, Direction.WEST));
        assertEquals(Set.of(), view.bondedDirections(0));
        assertEquals(TryResult.SKIPPED, view.bond(0, Direction.WEST, (byte) 2));
        assertEquals(TryResult.SKIPPED, view.unbond(0, Direction.WEST));
        assertEquals((byte) 1, grid.get(west).bondType(Direction.EAST));
        assertEquals(2, view.moleculeAt(0).size());
        assertEquals(TryResult.APPLIED, view.bond(0, Direction.EAST, (byte) 1));
        assertTrue(view.isBonded(0, Direction.EAST));
        assertEquals(Set.of(Direction.EAST), view.bondedDirections(0));
        assertEquals(3, view.moleculeAt(0).size());
        assertEquals(TryResult.APPLIED, view.unbond(0, Direction.EAST));
        assertTrue(view.wasJustUnbonded(0, Direction.EAST));
        assertEquals(Set.of(), view.bondedDirections(0));
        grid.put(ORIGIN, grid.get(ORIGIN).withBond(Direction.WEST, Atom.TEMPORARY_BOND));
        grid.put(west, grid.get(west).withBond(Direction.EAST, Atom.TEMPORARY_BOND));
        assertFalse(view.wasJustUnbonded(0, Direction.WEST));
        assertEquals(TryResult.SKIPPED, view.unbond(0, Direction.WEST));
    }

    @Test
    void removalChecksGrabsHoldersAndEveryBondAndCleansIndices() {
        bind(List.of(ORIGIN));
        assertEquals(TryResult.SKIPPED, view.setType(0, (byte) 2));
        assertEquals(TryResult.SKIPPED, view.remove(0));
        assertEquals(TryResult.APPLIED, view.spawn(0, Atom.of(0)));
        assertTrue(view.isOccupied(0));
        assertEquals(0, view.atomAt(0).orElseThrow().type());
        long id = view.entityAt(0);
        grabs.put(id, 1);
        assertEquals(TryResult.SKIPPED, view.remove(0));
        assertEquals(TryResult.APPLIED, view.setType(0, 0xFFFF));
        assertEquals(0xFFFF, view.atomAt(0).orElseThrow().type());
        for (int type : new int[]{-1, 0x10000}) {
            assertThrows(IllegalArgumentException.class, () -> view.setType(0, type));
            assertEquals(0xFFFF, view.atomAt(0).orElseThrow().type());
        }
        assertEquals(id, view.entityAt(0));
        assertEquals(1, view.grabCountAt(0));
        grabs.put(id, 0);
        held.add(id);
        assertEquals(TryResult.SKIPPED, view.remove(0));
        held.clear();
        for (byte code : new byte[]{1, Atom.TEMPORARY_BOND}) {
            grid.put(ORIGIN, grid.get(ORIGIN).withBond(Direction.NORTH, code));
            assertEquals(TryResult.SKIPPED, view.remove(0));
            view.setType(0, 0);
            assertEquals(0, grid.get(ORIGIN).type());
            assertEquals(code, grid.get(ORIGIN).bondType(Direction.NORTH));
            assertEquals(id, view.entityAt(0));
        }
        grid.put(ORIGIN, grid.get(ORIGIN).withoutBonds());
        GridPoint outside = ORIGIN.translate(Direction.NORTH);
        grid.put(outside, Atom.of((byte) 4).withBond(Direction.SOUTH, (byte) 1));
        assertEquals(TryResult.APPLIED, view.remove(0));
        assertEquals(TryResult.SKIPPED, view.remove(0));
        assertEquals(0L, view.entityAt(0));
        assertFalse(entities.containsKey(ORIGIN));
        assertFalse(positions.containsKey(id));
        assertFalse(grabs.containsKey(id));
        assertEquals((byte) 0, grid.get(outside).bondType(Direction.SOUTH));
        assertEquals(TryResult.APPLIED, view.spawn(0, Atom.of(0)));
        assertNotEquals(id, view.entityAt(0));
    }

    @ParameterizedTest
    @CsvSource({"0,0,APPLIED", "1,1,SKIPPED", "2,2,SKIPPED", "1,0,SKIPPED",
            "0,1,SKIPPED", "255,0,APPLIED", "0,255,APPLIED", "255,255,APPLIED"})
    void bondingPreservesRealMarksAndReplacesTemporaryMarks(int sourceCode, int targetCode, TryResult expected) {
        GridPoint east = ORIGIN.translate(Direction.EAST);
        bind(List.of(ORIGIN, east));
        // 直接测试半周期视图，避免 beginCycle 提前清除待覆盖的临时键。
        grid.put(ORIGIN, Atom.of((byte) 3).withBond(Direction.EAST, (byte) sourceCode));
        grid.put(east, Atom.of((byte) 4).withBond(Direction.WEST, (byte) targetCode));

        assertEquals(expected, view.bond(0, Direction.EAST, (byte) 2));

        assertEquals((byte) (expected == TryResult.APPLIED ? 2 : sourceCode),
                grid.get(ORIGIN).bondType(Direction.EAST));
        assertEquals((byte) (expected == TryResult.APPLIED ? 2 : targetCode),
                grid.get(east).bondType(Direction.WEST));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void bondingWithEitherAtomMissingLeavesTheOtherAtomUntouched(int occupiedIndex) {
        bind(List.of(ORIGIN, ORIGIN.translate(Direction.EAST)));
        Atom atom = Atom.of((byte) 3);
        view.spawn(occupiedIndex, atom);

        assertEquals(TryResult.SKIPPED, view.bond(0, Direction.EAST, (byte) 2));

        assertEquals(atom, view.atomAt(occupiedIndex).orElseThrow());
        assertTrue(view.atomAt(1 - occupiedIndex).isEmpty());
        assertEquals(1, grid.size());
    }
}
