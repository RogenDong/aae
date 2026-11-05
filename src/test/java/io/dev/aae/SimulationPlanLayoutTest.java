package io.dev.aae;

import io.dev.aae.glyph.GlyphDefinition;
import io.dev.aae.glyph.GlyphRegistry;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class SimulationPlanLayoutTest {
    private static final GridPoint ORIGIN = new GridPoint(0, 0);
    private static final Atom ATOM = Atom.of(1);
    private static final AtomicInteger UNIQUE = new AtomicInteger();

    @ParameterizedTest
    @CsvSource({"input,mechanism", "output,mechanism", "input,track", "output,track",
            "input,glyph", "output,glyph", "glyph,mechanism", "glyph,track", "glyph,glyph",
            "input,input", "output,output", "output,input"})
    void rejectsEveryForbiddenPair(String firstKind, String secondKind) {
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.single(type, context -> {}))) {
            List<MechanismSpec> arms = secondKind.equals("mechanism")
                    ? List.of(arm(ORIGIN, Direction.EAST, 1)) : List.of();
            List<TrackSpec> tracks = secondKind.equals("track")
                    ? List.of(new TrackSpec(ORIGIN)) : List.of();
            List<GlyphSpec> glyphs = firstKind.equals("glyph")
                    ? (secondKind.equals("glyph")
                        ? List.of(new GlyphSpec(type, ORIGIN), new GlyphSpec(type, ORIGIN))
                        : List.of(new GlyphSpec(type, ORIGIN)))
                    : (secondKind.equals("glyph") ? List.of(new GlyphSpec(type, ORIGIN)) : List.of());
            List<InputSpec> inputs = firstKind.equals("input")
                    ? (secondKind.equals("input")
                        ? List.of(InputSpec.single(ORIGIN, ATOM), InputSpec.single(ORIGIN, ATOM))
                        : List.of(InputSpec.single(ORIGIN, ATOM)))
                    : (secondKind.equals("input") ? List.of(InputSpec.single(ORIGIN, ATOM)) : List.of());
            List<OutputSpec> outputs = firstKind.equals("output")
                    ? (secondKind.equals("output")
                        ? List.of(OutputSpec.single(ORIGIN, ATOM), OutputSpec.single(ORIGIN, ATOM))
                        : List.of(OutputSpec.single(ORIGIN, ATOM))) : List.of();
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new SimulationPlan(arms, tracks, inputs, outputs, glyphs, 1));
            assertTrue(failure.getMessage().contains(firstKind));
            assertTrue(failure.getMessage().contains(secondKind));
            assertTrue(failure.getMessage().contains(ORIGIN.toString()));
            String firstOwner = firstKind + "[0]";
            String secondOwner = secondKind + "[" + (firstKind.equals(secondKind) ? 1 : 0) + "]";
            assertTrue(failure.getMessage().contains(firstOwner));
            assertTrue(failure.getMessage().contains(secondOwner));
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void checksRotatedNonAnchorCellsAndNonFirstTemplateAtoms(Direction direction) {
        String type = typeId();
        GridPoint anchor = new GridPoint(-7, -5);
        GridPoint offset = new GridPoint(2, 1);
        GlyphSpec glyph = new GlyphSpec(type, anchor, direction);
        GridPoint collision = glyph.worldPoint(offset);
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.of(type,
                List.of(ORIGIN, offset), context -> {}))) {
            InputSpec input = InputSpec.of(AtomPlacement.of(50, 50, ATOM),
                    new AtomPlacement(collision, ATOM));
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> new SimulationPlan(List.of(), List.of(), List.of(input), List.of(), List.of(glyph), 1));
            assertTrue(failure.getMessage().contains(collision.toString()));

            assertThrows(IllegalArgumentException.class,
                    () -> new SimulationPlan(List.of(arm(collision, Direction.EAST, 1)), List.of(glyph), 1));
        }
    }

    @Test
    void detectsPartialIOAndGlyphOverlaps() {
        InputSpec input = InputSpec.of(AtomPlacement.of(-1, 0, ATOM),
                new AtomPlacement(ORIGIN, ATOM));
        OutputSpec output = OutputSpec.of(
                AtomPlacement.of(1, 0, ATOM.withBond(Direction.WEST, 1)),
                new AtomPlacement(ORIGIN, ATOM.withBond(Direction.EAST, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationPlan(List.of(), List.of(input), List.of(output), 1));
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.of(type,
                List.of(ORIGIN, new GridPoint(1, 0)), context -> {}))) {
            assertThrows(IllegalArgumentException.class, () -> new SimulationPlan(List.of(),
                    List.of(new GlyphSpec(type, ORIGIN),
                            new GlyphSpec(type, new GridPoint(2, 0), Direction.WEST)), 1));
        }
    }

    @Test
    void allowsMixedTrackAndDistinctArmBasesOnItsNodes() {
        var plan = assertDoesNotThrow(() -> new SimulationPlan(List.of(arm(ORIGIN, Direction.EAST, 1),
                arm(new GridPoint(1, 1), Direction.WEST, 1)), List.of(
                        new TrackSpec(new GridPoint(-1, 0), ORIGIN, new GridPoint(1, 1)))));
        var simulator = assertDoesNotThrow(() -> new OrthogonalSimulator(plan));
        assertEquals(ORIGIN, simulator.mechanism(0).position());
        assertEquals(new GridPoint(1, 1), simulator.mechanism(1).position());
    }

    @Test
    void rejectsSharedInitialMechanismBases() {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> SimulationPlan.of(arm(ORIGIN, Direction.EAST, 1),
                        arm(ORIGIN, Direction.WEST, 2)));
        assertEquals("mechanism[1] overlaps mechanism[0] at " + ORIGIN, failure.getMessage());
    }

    @Test
    void allowsIOAndGlyphsUnderArmShaftsAndGrippers() {
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.of(type,
                List.of(ORIGIN, new GridPoint(1, 0)), context -> {}))) {
            var plan = assertDoesNotThrow(() -> new SimulationPlan(List.of(arm(ORIGIN, Direction.EAST, 3),
                    arm(new GridPoint(0, 5), Direction.EAST, 3)), List.of(),
                    List.of(InputSpec.single(new GridPoint(1, 0), ATOM)),
                    List.of(OutputSpec.single(new GridPoint(3, 0), ATOM)),
                    List.of(new GlyphSpec(type, new GridPoint(2, 5))), 1));
            assertDoesNotThrow(() -> new OrthogonalSimulator(plan));
        }
    }

    @Test
    void usesActualSparseCellsRatherThanBoundingBoxesOrTrackSegments() {
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.of(type,
                List.of(ORIGIN, new GridPoint(2, 2)), context -> {}))) {
            var plan = assertDoesNotThrow(() -> new SimulationPlan(
                    List.of(),
                    List.of(TrackBuilder.start(-2, -2).eastSouth().noLoop()),
                    List.of(InputSpec.of(
                            AtomPlacement.of(-2, -1, ATOM),
                            AtomPlacement.of(1, 1, ATOM))),
                    List.of(OutputSpec.single(new GridPoint(1, 0), ATOM)),
                    List.of(new GlyphSpec(type, ORIGIN)), 1));
            assertDoesNotThrow(() -> new OrthogonalSimulator(plan));
        }
    }

    @Test
    void validatesBeforeStateFactoriesAndCallbacksAndReportsFirstConflictConsistently() {
        AtomicInteger factories = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(new GlyphDefinition<>(type, List.of(ORIGIN),
                factories::incrementAndGet, (context, state) -> { callbacks.incrementAndGet(); return state; }))) {
            Executable construct = () -> new SimulationPlan(
                    List.of(arm(ORIGIN, Direction.EAST, 1)),
                    List.of(new TrackSpec(ORIGIN)),
                    List.of(InputSpec.single(ORIGIN, ATOM)),
                    List.of(),
                    List.of(new GlyphSpec(type, ORIGIN)),
                    1);
            String message = assertThrows(IllegalArgumentException.class, construct).getMessage();
            assertEquals("input[0] overlaps mechanism[0] at " + ORIGIN, message);
            assertEquals(message, assertThrows(IllegalArgumentException.class, construct).getMessage());
            assertEquals(0, factories.get());
            assertEquals(0, callbacks.get());
        }
    }

    @Test
    void validPlanDoesNotInitializeGlyphRuntime() {
        AtomicInteger factories = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        String type = typeId();
        try (var handle = GlyphRegistry.global().register(new GlyphDefinition<>(type, List.of(ORIGIN),
                factories::incrementAndGet, (context, state) -> { callbacks.incrementAndGet(); return state; }))) {
            var plan = assertDoesNotThrow(() -> new SimulationPlan(List.of(),
                    List.of(new GlyphSpec(type, ORIGIN)), 1));
            assertEquals(0, factories.get());
            assertEquals(0, callbacks.get());

            assertDoesNotThrow(() -> new OrthogonalSimulator(plan));
            assertTrue(factories.get() > 0);
            assertEquals(0, callbacks.get());
        }
    }

    @Test
    void validatesUsingDefinitionSnapshotAndSimulatorRetainsItAfterRegistrationCloses() {
        String type = typeId();
        SimulationPlan valid;
        try (var handle = GlyphRegistry.global().register(GlyphDefinition.of(type,
                List.of(ORIGIN, new GridPoint(2, 0)), context -> {}))) {
            valid = new SimulationPlan(List.of(), List.of(new GlyphSpec(type, ORIGIN)), 1);
            assertThrows(IllegalArgumentException.class, () -> new SimulationPlan(List.of(), List.of(),
                    List.of(InputSpec.single(new GridPoint(2, 0), ATOM)), List.of(), valid.glyphs(), 1));
        }
        assertDoesNotThrow(() -> new OrthogonalSimulator(valid));
        assertEquals(List.of(ORIGIN, new GridPoint(2, 0)), valid.glyphPlaceholderCells());
    }

    private static MechanismSpec arm(GridPoint position, Direction direction, int length) {
        return new MechanismSpec(position, direction, length, InstructionTape.once("."));
    }

    private static String typeId() {
        return "test.layout:type-" + UNIQUE.incrementAndGet();
    }
}
