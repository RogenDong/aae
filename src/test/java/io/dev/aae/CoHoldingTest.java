package io.dev.aae;

import io.dev.aae.mech.*;

import io.dev.aae.track.TrackBuilder;
import io.dev.aae.track.TrackSpec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoHoldingTest {
    private static final GridPoint ORIGIN = new GridPoint(0, 0);
    private static final Atom ATOM = Atom.of(1);

    @ParameterizedTest
    @CsvSource({"f.,f.", "f.,.f"})
    void distinctBasesCanGrabTheSameMoleculeTogetherOrInSequence(String first, String second) {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, first),
                        arm(0, 1, Direction.NORTH, 1, second)), List.of(),
                InputSpec.single(ORIGIN, ATOM));

        simulator.run(2);

        assertFalse(simulator.isHalted());
        assertTrue(simulator.warnings().isEmpty());
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(simulator.heldEntities(0), simulator.heldEntities(1));
        assertEquals(1, simulator.heldEntities(0).size());
        assertEquals(ORIGIN, simulator.mechanism(0).grabPosition());
        assertEquals(ORIGIN, simulator.mechanism(1).grabPosition());
    }

    @ParameterizedTest
    @CsvSource({"WAIT,.,1", "GRAB,f,1", "ENDPOINT,g,1", "OFF_TRACK,g,1",
            "MAXIMUM,w,3", "MINIMUM,s,1"})
    void stationaryCoHolderBlocksPistonMotion(String scenario, String instruction, int stationaryLength) {
        GridPoint stationaryBase = new GridPoint(0, stationaryLength);
        List<TrackSpec> tracks = scenario.equals("ENDPOINT")
                ? List.of(new TrackSpec(new GridPoint(-1, stationaryLength), stationaryBase))
                : List.of();
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, "fw"),
                        arm(0, stationaryLength, Direction.NORTH, stationaryLength,
                                "f" + instruction)), tracks, InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        assertFalse(simulator.isHalted());
        long entity = simulator.entityAt(ORIGIN);

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertEquals(1, simulator.warnings().size());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertTrue(simulator.warnings().get(0).contains("from mechanism[0]"));
        assertTrue(simulator.warnings().get(0).contains("from mechanism[1]"));
        assertEquals(1, simulator.mechanism(0).length());
        assertEquals(stationaryLength, simulator.mechanism(1).length());
        assertEquals(stationaryBase, simulator.mechanism(1).position());
        assertEquals(entity, simulator.entityAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
    }

    @Test
    void stationaryTrackEndpointBlocksAnotherHoldersTranslation() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(0, 1, Direction.NORTH, 1, "fg"),
                        arm(-1, 0, Direction.EAST, 1, "fg")),
                List.of(TrackBuilder.start(-1, 1).east().noLoop(),
                        TrackBuilder.start(-1, 0).west().noLoop()),
                InputSpec.single(ORIGIN, ATOM));
        simulator.step();

        simulator.step();

        assertTrue(simulator.isHalted());
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertEquals(new GridPoint(-1, 0), simulator.mechanism(1).position());
        assertEquals(new GridPoint(0, 1), simulator.mechanism(0).position());
        assertEquals(ATOM, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    @Test
    void newlyJoinedHolderBlocksRotationAndItsGrabIsRolledBack() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(1, 0, Direction.EAST, 1, ".fd"),
                        arm(3, 0, Direction.WEST, 1, "..f")), List.of(),
                InputSpec.single(new GridPoint(2, 0), ATOM));
        simulator.run(2);
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(3, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("STAY"));
        assertEquals(Direction.EAST, simulator.mechanism(0).direction());
        assertEquals(ATOM, simulator.atomAt(new GridPoint(2, 0)));
        assertNull(simulator.atomAt(new GridPoint(1, 1)));
        assertEquals(1, simulator.grabsAt(new GridPoint(2, 0)));
        assertTrue(simulator.heldEntities(1).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"g,-1,0", "d,1,1"})
    void releasingCoHolderAllowsTranslationOrRotation(String motion, long x, long y) {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(1, 0, Direction.WEST, 1, "fr"),
                        arm(0, 1, Direction.NORTH, 1, "f" + motion)),
                List.of(TrackBuilder.start(0, 1).west().noLoop()),
                InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        long entity = simulator.entityAt(ORIGIN);

        simulator.step();

        GridPoint destination = new GridPoint(x, y);
        assertFalse(simulator.isHalted());
        assertEquals(entity, simulator.entityAt(destination));
        assertEquals(ATOM, simulator.atomAt(destination));
        assertEquals(destination, simulator.mechanism(1).grabPosition());
        assertEquals(1, simulator.grabsAt(destination));
        assertTrue(simulator.heldEntities(0).isEmpty());
        assertEquals(List.of(entity), simulator.heldEntities(1));
    }

    @Test
    void identicalTranslationsMoveACoHeldMoleculeOnlyOnce() {
        Atom left = ATOM.withBond(Direction.EAST, 2);
        Atom right = Atom.of(3).withBond(Direction.WEST, 2);
        OrthogonalSimulator simulator = simulator(
                List.of(arm(0, 1, Direction.NORTH, 1, "fg"),
                        arm(1, -1, Direction.SOUTH, 1, "fg")),
                List.of(
                        TrackBuilder.start(0, 1).west().noLoop(),
                        TrackBuilder.start(1, -1).west().noLoop()),
                InputSpec.of(new AtomPlacement(ORIGIN, left),
                        AtomPlacement.of(1, 0, right)));

        simulator.run(2);

        assertFalse(simulator.isHalted());
        assertEquals(new GridPoint(-1, 1), simulator.mechanism(0).position());
        assertEquals(new GridPoint(0, -1), simulator.mechanism(1).position());
        assertEquals(left, simulator.atomAt(new GridPoint(-1, 0)));
        assertEquals(right, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.boardSize());
        assertEquals(2, simulator.grabsAt(new GridPoint(-1, 0)));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    @Test
    void identicalPivotsFromDistinctBasesRotateTheMoleculeOnlyOnce() {
        OrthogonalSimulator simulator = simulator(
                List.of(arm(-1, 0, Direction.EAST, 1, "fe"),
                        arm(0, 1, Direction.NORTH, 1, "fe")), List.of(),
                InputSpec.of(new AtomPlacement(ORIGIN, ATOM.withBond(Direction.NORTH, 2)),
                        AtomPlacement.of(0, -1,
                                Atom.of(3).withBond(Direction.SOUTH, 2))));

        simulator.run(2);

        assertFalse(simulator.isHalted());
        assertEquals(ATOM.withBond(Direction.EAST, 2), simulator.atomAt(ORIGIN));
        assertEquals(Atom.of(3).withBond(Direction.WEST, 2),
                simulator.atomAt(new GridPoint(1, 0)));
        assertNull(simulator.atomAt(new GridPoint(0, -1)));
        assertEquals(2, simulator.boardSize());
        assertEquals(2, simulator.grabsAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));
        assertEquals(ORIGIN, simulator.mechanism(0).grabPosition());
        assertEquals(ORIGIN, simulator.mechanism(1).grabPosition());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void incompatibleTranslationsOrRotationsRollBackBothHolders(boolean rotation) {
        List<MechanismSpec> arms = rotation
                ? List.of(arm(0, 1, Direction.NORTH, 1, "fd"),
                        arm(0, -1, Direction.SOUTH, 1, "fa"))
                : List.of(arm(1, 0, Direction.WEST, 1, "fg"),
                        arm(0, -1, Direction.SOUTH, 1, "ft"));
        List<TrackSpec> tracks = rotation ? List.of()
                : List.of(TrackBuilder.start(1, 0).north().west().noLoop());
        OrthogonalSimulator simulator = simulator(arms, tracks, InputSpec.single(ORIGIN, ATOM));
        simulator.step();
        List<MechanismView> before = simulator.mechanisms();

        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertEquals(before, simulator.mechanisms());
        assertEquals(ATOM, simulator.atomAt(ORIGIN));
        assertEquals(2, simulator.grabsAt(ORIGIN));
    }

    private static OrthogonalSimulator simulator(
            List<MechanismSpec> arms, List<TrackSpec> tracks, InputSpec input) {
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(arms, tracks, List.of(input), List.of(), 1));
        simulator.init();
        return simulator;
    }

    private static MechanismSpec arm(long x, long y, Direction direction, int length, String program) {
        return new MechanismSpec(new GridPoint(x, y), direction, length, InstructionTape.once(program));
    }
}
