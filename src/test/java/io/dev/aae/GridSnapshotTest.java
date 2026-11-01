package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GridSnapshotTest {

    @Test
    void reportsInclusiveBoundsAndIsDeeplyImmutable() {
        OrthogonalSimulator simulator = new OrthogonalSimulator(SimulationPlan.of(
                stationary("a", -2, 5),
                stationary("b", 7, -3),
                stationary("c", 7, -3)));

        GridSnapshot snapshot = simulator.snapshot();

        assertEquals(0, snapshot.cycle());
        assertEquals(new GridBounds(-2, -3, 7, 5), snapshot.bounds().orElseThrow());
        assertEquals(10, snapshot.bounds().orElseThrow().width());
        assertEquals(9, snapshot.bounds().orElseThrow().height());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.cells().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.occupantsAt(new GridPoint(7, -3)).add(
                        simulator.mechanism("a")));
    }

    @Test
    void emptyPlanProducesAnEmptySnapshot() {
        GridSnapshot snapshot = new OrthogonalSimulator(SimulationPlan.of()).snapshot();

        assertTrue(snapshot.bounds().isEmpty());
        assertEquals(List.of(), snapshot.occupantsAt(new GridPoint(0, 0)));
    }

    private static MechanismSpec stationary(String id, long x, long y) {
        return new MechanismSpec(
                id,
                new GridPoint(x, y),
                Direction.NORTH,
                InstructionTape.empty());
    }
}
