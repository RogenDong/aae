package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationPlanTest {

    @Test
    void mechanismsKeepDeclarationOrderInAnImmutableSnapshot() {
        MechanismSpec first = mechanism(0, 0);
        MechanismSpec second = mechanism(1, 0);
        List<MechanismSpec> definitions = new ArrayList<>(List.of(first, second));
        SimulationPlan plan = new SimulationPlan(definitions);
        definitions.clear();

        assertEquals(List.of(first, second), plan.mechanisms());
        assertThrows(UnsupportedOperationException.class, () -> plan.mechanisms().clear());
    }

    @Test
    void inputsOutputsAndGlyphsKeepDeclarationOrderInImmutableListSnapshots() {
        List<InputSpec> inputs = new ArrayList<>(List.of(
                InputSpec.single(new GridPoint(1, 0), sampleAtom()),
                InputSpec.single(new GridPoint(0, 0), sampleAtom())));
        List<OutputSpec> outputs = new ArrayList<>(List.of(
                OutputSpec.single(new GridPoint(3, 0), sampleAtom()),
                OutputSpec.single(new GridPoint(2, 0), sampleAtom())));
        String type = "test.plan:ordered";
        List<GlyphSpec> glyphs = new ArrayList<>(List.of(
                new GlyphSpec(type, new GridPoint(5, 0)),
                new GlyphSpec(type, new GridPoint(4, 0))));
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.single(type, context -> {}))) {
            SimulationPlan plan = new SimulationPlan(List.of(), List.of(), inputs, outputs, glyphs, 1);
            assertEquals(inputs, plan.inputs());
            assertEquals(outputs, plan.outputs());
            assertEquals(glyphs, plan.glyphs());
            inputs.clear();
            outputs.clear();
            glyphs.clear();
            assertEquals(new GridPoint(1, 0), plan.inputs().get(0).atoms().get(0).position());
            assertEquals(new GridPoint(3, 0), plan.outputs().get(0).anchor());
            assertEquals(new GridPoint(5, 0), plan.glyphs().get(0).position());
            assertEquals(2, plan.inputs().size());
            assertEquals(2, plan.outputs().size());
            assertEquals(2, plan.glyphs().size());
            assertThrows(UnsupportedOperationException.class, () -> plan.inputs().clear());
            assertThrows(UnsupportedOperationException.class, () -> plan.outputs().clear());
            assertThrows(UnsupportedOperationException.class, () -> plan.glyphs().clear());
        }
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
        GridPoint second = new GridPoint(1, 0);
        GridPoint last = new GridPoint(2, 1);
        List<TrackSpec> tracks = new ArrayList<>(
                List.of(new TrackSpec(first, second, last)));

        SimulationPlan plan = new SimulationPlan(List.of(), tracks);
        tracks.clear();

        assertEquals(1, plan.tracks().size());
        assertEquals(second, plan.trackDestination(first, TrackDirection.PLUS));
        assertEquals(first, plan.trackDestination(second, TrackDirection.MINUS));
        assertEquals(last, plan.trackDestination(second, TrackDirection.PLUS));
        assertEquals(second, plan.trackDestination(last, TrackDirection.MINUS));
        assertEquals(last, plan.trackDestination(last, TrackDirection.PLUS));
        GridPoint offTrack = new GridPoint(20, 20);
        assertEquals(offTrack, plan.trackDestination(offTrack, TrackDirection.PLUS));
        assertEquals(first, plan.trackDestination(first, TrackDirection.MINUS));
        assertThrows(UnsupportedOperationException.class, () -> plan.tracks().clear());
        var overlap = assertThrows(InvalidTrackOverlapException.class,
                () -> new SimulationPlan(List.of(), List.of(plan.tracks().get(0), new TrackSpec(last))));
        assertEquals("track[1] overlaps track[0] at " + last, overlap.getMessage());
    }

    @Test
    void inputAndOutputTemplatesMustBeNonEmptyWithUniquePositions() {
        assertThrows(IllegalArgumentException.class, () -> InputSpec.of());
        assertThrows(IllegalArgumentException.class, () -> OutputSpec.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> InputSpec.of(new AtomPlacement(new GridPoint(0, 0), sampleAtom()),
                        new AtomPlacement(new GridPoint(0, 0), otherAtom())));
    }

    private static MechanismSpec mechanism(long x, long y) {
        return new MechanismSpec(
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
