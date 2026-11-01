package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationPlanTest {

    @Test
    void mechanismIdsMustBeUnique() {
        MechanismSpec first = mechanism("same", 0, 0);
        MechanismSpec second = mechanism("same", 1, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationPlan.of(first, second));
    }

    @Test
    void inputAndOutputIdsMustBeUniqueWithinTheirKinds() {
        InputSpec input = InputSpec.single("same", new GridPoint(0, 0), sampleAtom());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationPlan(List.of(), List.of(input, input), List.of(), 1));

        OutputSpec output = OutputSpec.single("same", new GridPoint(0, 0), sampleAtom());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationPlan(List.of(), List.of(), List.of(output, output), 1));
    }

    @Test
    void targetOutputsMustBeAtLeastOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationPlan(List.of(), List.of(), List.of(), 0));
    }

    @Test
    void tracksAreImmutableAndExposeCompiledPlusMinusDestinations() {
        GridPoint first = new GridPoint(0, 0);
        GridPoint second = new GridPoint(1, 1);
        List<TrackSpec> tracks = new ArrayList<>(
                List.of(TrackSpec.diagonal(first, second)));

        SimulationPlan plan = new SimulationPlan(List.of(), tracks);
        tracks.clear();

        assertEquals(1, plan.tracks().size());
        assertEquals(second, plan.trackDestination(first, TrackDirection.PLUS));
        assertEquals(first, plan.trackDestination(second, TrackDirection.MINUS));
        assertEquals(first, plan.trackDestination(first, TrackDirection.MINUS));
        assertThrows(UnsupportedOperationException.class, () -> plan.tracks().clear());
    }

    @Test
    void inputAndOutputTemplatesMustBeNonEmptyWithUniquePositions() {
        assertThrows(IllegalArgumentException.class, () -> InputSpec.of("in"));
        assertThrows(IllegalArgumentException.class, () -> OutputSpec.of("out"));
        assertThrows(
                IllegalArgumentException.class,
                () -> InputSpec.of(
                        "in",
                        new AtomPlacement(new GridPoint(0, 0), sampleAtom()),
                        new AtomPlacement(new GridPoint(0, 0), otherAtom())));
    }

    private static MechanismSpec mechanism(String id, long x, long y) {
        return new MechanismSpec(
                id,
                new GridPoint(x, y),
                Direction.NORTH,
                InstructionTape.empty());
    }

    private static Atom sampleAtom() {
        return Atom.of((byte) 1);
    }

    private static Atom otherAtom() {
        return Atom.of((byte) 5);
    }
}
