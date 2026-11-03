package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GlyphGeometryTest {

    @ParameterizedTest
    @CsvSource({
            "EAST,  7, 10, 4, 9",
            "SOUTH, 2,  9, 3, 6",
            "WEST,  3,  4, 6, 5",
            "NORTH, 8,  5, 7, 8"
    })
    void rotatedGeometryIsSharedByPlaceholdersSlotsAndCallbacks(
            Direction direction, long inputX, long inputY, long outputX, long outputY) {
        GridPoint anchor = new GridPoint(5, 7);
        GridPoint localInput = new GridPoint(2, 3);
        GridPoint localOutput = new GridPoint(-1, 2);
        GridPoint worldInput = new GridPoint(inputX, inputY);
        GridPoint worldOutput = new GridPoint(outputX, outputY);
        byte bondType = 1;
        AtomicInteger calls = new AtomicInteger();
        String typeId = "test.geometry:" + direction + "-" + System.nanoTime();
        GlyphSpec spec = new GlyphSpec(typeId, anchor, direction);
        GlyphDefinition<Void> definition = GlyphDefinition.of(
                typeId,
                List.of(new GridPoint(0, 0), localInput, localOutput,
                        GlyphSpec.toLocal(direction, new GridPoint(inputX + 1 - anchor.x(), inputY - anchor.y()))),
                List.of(GlyphDefinition.InputSlot.bonded(
                        1, (byte) 1, Direction.EAST, bondType)),
                List.of(GlyphDefinition.OutputSlot.of(2)),
                context -> {
                    calls.incrementAndGet();
                    assertEquals(anchor, context.worldPoint(0));
                    assertEquals(worldInput, context.worldPoint(1));
                    assertEquals(worldOutput, context.worldPoint(2));
                    assertEquals(new GridPoint(0, 0), context.toLocal(anchor));
                    assertEquals(localInput, context.toLocal(worldInput));
                    assertEquals(localOutput, context.toLocal(worldOutput));
                    assertEquals(worldInput.translate(Direction.SOUTH),
                            context.neighbor(1, Direction.SOUTH));
                    assertEquals(TryResult.APPLIED,
                            context.board().spawn(2, Atom.of((byte) 2)));
                });
        AtomPlacement source = new AtomPlacement(worldInput,
                Atom.of((byte) 1).withBond(Direction.EAST, bondType));
        AtomPlacement neighbor = new AtomPlacement(worldInput.translate(Direction.EAST),
                Atom.of((byte) 3).withBond(Direction.WEST, bondType));

        try (var handle = GlyphRegistry.global().register(definition)) {
            GridPoint worldOffset = new GridPoint(inputX - anchor.x(), inputY - anchor.y());
            assertEquals(worldOffset, spec.worldOffset(localInput));
            assertEquals(worldOffset, GlyphSpec.fromLocal(direction, localInput));
            assertEquals(localInput, GlyphSpec.toLocal(direction, worldOffset));
            assertEquals(worldOutput, spec.worldPoint(localOutput));

            SimulationPlan plan = new SimulationPlan(List.of(), List.of(spec), 1);
            assertEquals(List.of(anchor, worldInput, worldOutput, worldInput.translate(Direction.EAST)), plan.glyphPlaceholderCells());

            GlyphRuntimeFixture simulator = new GlyphRuntimeFixture(
                    List.of(InputSpec.of(source, neighbor)), List.of(spec));
            simulator.step();

            assertNotNull(simulator.atomAt(worldOutput));
            assertEquals((byte) 2, simulator.atomAt(worldOutput).type());
            // 第二半周期的输出槽已被占用，行为应跳过。
            assertEquals(1, calls.get());

            GlyphRuntimeFixture missingInput = new GlyphRuntimeFixture(List.of(), List.of(spec));
            missingInput.step();
            assertNull(missingInput.atomAt(worldOutput));
            assertEquals(1, calls.get());

            GlyphRuntimeFixture occupiedOutput = new GlyphRuntimeFixture(
                    List.of(InputSpec.of(source, neighbor,
                            new AtomPlacement(worldOutput, Atom.of((byte) 4)))), List.of(spec));
            occupiedOutput.step();
            assertEquals((byte) 4, occupiedOutput.atomAt(worldOutput).type());
            assertEquals(1, calls.get());
        }
    }
}
