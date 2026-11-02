package io.dev.aae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MechanismMotionTest {

    @Test
    void lengthIsValidatedAndLegacyConstructorsDefaultToOne() {
        InstructionTape tape = InstructionTape.empty();
        MechanismSpec legacySpec = new MechanismSpec(
                "legacy", new GridPoint(0, 0), Direction.NORTH, tape);
        MechanismView legacyView = new MechanismView(
                "legacy", new GridPoint(0, 0), Direction.NORTH, false);

        assertEquals(1, legacySpec.length());
        assertEquals(1, legacyView.length());
        assertEquals(2, new MechanismSpec(
                "two", new GridPoint(0, 0), Direction.NORTH, tape, 2).length());
        assertEquals(3, new MechanismView(
                "three", new GridPoint(0, 0), Direction.NORTH, true, 3).length());

        assertThrows(IllegalArgumentException.class, () -> new MechanismSpec(
                "short", new GridPoint(0, 0), Direction.NORTH, 0, tape));
        assertThrows(IllegalArgumentException.class, () -> new MechanismSpec(
                "long", new GridPoint(0, 0), Direction.NORTH, 4, tape));
        assertThrows(IllegalArgumentException.class, () -> new MechanismView(
                "short", new GridPoint(0, 0), Direction.NORTH, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new MechanismView(
                "long", new GridPoint(0, 0), Direction.NORTH, 4, false));
    }

    @Test
    void pistonLengthChangesWithinBoundsAndResetRestoresIt() {
        OrthogonalSimulator simulator = simulator(new MechanismSpec(
                "piston",
                new GridPoint(4, 5),
                Direction.WEST,
                2,
                InstructionTape.once("wwsss")));

        simulator.step();
        assertMechanism(simulator, 3, new GridPoint(4, 5), Direction.WEST);
        simulator.step();
        assertMechanism(simulator, 3, new GridPoint(4, 5), Direction.WEST);
        simulator.step();
        assertMechanism(simulator, 2, new GridPoint(4, 5), Direction.WEST);
        simulator.step();
        assertMechanism(simulator, 1, new GridPoint(4, 5), Direction.WEST);
        simulator.step();
        assertMechanism(simulator, 1, new GridPoint(4, 5), Direction.WEST);

        simulator.reset();
        assertMechanism(simulator, 2, new GridPoint(4, 5), Direction.WEST);
    }

    @Test
    void mechanismQueriesAndChecksumsIncludeLength() {
        MechanismSpec extended = new MechanismSpec(
                "arm",
                new GridPoint(0, 0),
                Direction.NORTH,
                2,
                InstructionTape.empty());
        OrthogonalSimulator simulator = simulator(extended);
        OrthogonalSimulator legacyLength = simulator(new MechanismSpec(
                "arm", new GridPoint(0, 0), Direction.NORTH, InstructionTape.empty()));

        assertEquals(2, simulator.mechanism("arm").length());
        assertEquals(2, simulator.mechanisms().get(0).length());
        assertNotEquals(legacyLength.checksum(), simulator.checksum());
    }

    @Test
    void pistonExtensionAndRetractionCarryTheGrabbedMolecule() {
        MechanismSpec piston = new MechanismSpec(
                "piston",
                new GridPoint(0, 0),
                Direction.EAST,
                2,
                InstructionTape.once("fws"));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(2, 0), sampleAtom()),
                new AtomPlacement(new GridPoint(10, 10), blockerAtom()));
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(piston), List.of(input), List.of(), 1));
        simulator.init();

        simulator.step();
        assertTrue(simulator.mechanism("piston").grabbing());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(2, 0)));

        simulator.step();
        assertEquals(3, simulator.mechanism("piston").length());
        assertNull(simulator.atomAt(new GridPoint(2, 0)));
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(3, 0)));

        simulator.step();
        assertEquals(2, simulator.mechanism("piston").length());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(2, 0)));
        assertNull(simulator.atomAt(new GridPoint(3, 0)));
        assertFalse(simulator.isHalted());
    }

    @Test
    void diagonalTrackTurnsCarryTheGrabbedMoleculeByTheSameOffset() {
        Atom grabbed = sampleAtom().withBond(Direction.WEST, (byte) 2);
        Atom attached = blockerAtom().withBond(Direction.EAST, (byte) 2);
        MechanismSpec arm = new MechanismSpec(
                "arm",
                new GridPoint(0, 1),
                Direction.NORTH,
                InstructionTape.once("fgg"));
        TrackSpec track = TrackSpec.diagonal(
                new GridPoint(0, 1),
                new GridPoint(1, 0),
                new GridPoint(0, -1));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(0, 0), grabbed),
                new AtomPlacement(new GridPoint(-1, 0), attached));
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(arm), List.of(track), List.of(input), List.of(), 1));
        simulator.init();

        simulator.run(3);

        assertEquals(new GridPoint(0, -1), simulator.mechanism("arm").position());
        assertEquals(Direction.NORTH, simulator.mechanism("arm").direction());
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(0, -2)).bondType(Direction.WEST));
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(-1, -2)).bondType(Direction.EAST));
        assertEquals(1, simulator.grabsAt(new GridPoint(0, -2)));
        assertEquals(1, simulator.grabsAt(new GridPoint(-1, -2)));
        assertFalse(simulator.isHalted());
    }

    @Test
    void overflowingHeldAtomTrackMotionDoesNotPartiallyCommit() {
        GridPoint base = new GridPoint(Long.MAX_VALUE - 1, 1);
        GridPoint atomPosition = new GridPoint(Long.MAX_VALUE, 1);
        MechanismSpec arm = new MechanismSpec(
                "arm", base, Direction.EAST, InstructionTape.once("fg"));
        TrackSpec track = TrackSpec.diagonal(
                base, new GridPoint(Long.MAX_VALUE, 0));
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(arm),
                List.of(track),
                List.of(InputSpec.single("in", atomPosition, sampleAtom())),
                List.of(),
                1));
        simulator.init();
        simulator.step();

        assertThrows(ArithmeticException.class, simulator::step);

        assertEquals(1, simulator.cycle());
        assertEquals(base, simulator.mechanism("arm").position());
        assertTrue(simulator.mechanism("arm").grabbing());
        assertEquals(sampleAtom(), simulator.atomAt(atomPosition));
        assertEquals(1, simulator.grabsAt(atomPosition));
    }

    @Test
    void pistonBoundaryInstructionsDoNotMoveHeldMolecules() {
        OrthogonalSimulator maximum = boundarySimulator(3, 'w');
        OrthogonalSimulator minimum = boundarySimulator(1, 's');

        assertEquals(3, maximum.mechanism("piston").length());
        assertEquals(sampleAtom(), maximum.atomAt(new GridPoint(3, 0)));
        assertEquals(1, maximum.grabsAt(new GridPoint(3, 0)));
        assertFalse(maximum.isHalted());

        assertEquals(1, minimum.mechanism("piston").length());
        assertEquals(sampleAtom(), minimum.atomAt(new GridPoint(1, 0)));
        assertEquals(1, minimum.grabsAt(new GridPoint(1, 0)));
        assertFalse(minimum.isHalted());
    }

    @Test
    void blockedExtensionDoesNotCommitLengthOrMoleculeMotion() {
        MechanismSpec piston = new MechanismSpec(
                "piston",
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fw"));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(1, 0), sampleAtom()),
                new AtomPlacement(new GridPoint(2, 0), blockerAtom()));
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(piston), List.of(input), List.of(), 1));
        simulator.init();
        // 第一个周期只执行抓取，活塞长度保持不变。
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertTrue(simulator.mechanism("piston").grabbing());

        // 第二个周期：目标格点仍被挡，运动冲突让整周期回滚并停机。
        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("already occupied by entity"));
        assertEquals(1, simulator.mechanism("piston").length());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(blockerAtom(), simulator.atomAt(new GridPoint(2, 0)));
    }

    @Test
    void conflictingPistonMovesDoNotCommitEitherLength() {
        MechanismSpec east = new MechanismSpec(
                "east",
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fw"));
        MechanismSpec west = new MechanismSpec(
                "west",
                new GridPoint(2, 0),
                Direction.WEST,
                1,
                InstructionTape.once("fw"));
        OrthogonalSimulator simulator = new OrthogonalSimulator(new SimulationPlan(
                List.of(east, west),
                List.of(InputSpec.single("in", new GridPoint(1, 0), sampleAtom())),
                List.of(),
                1));
        simulator.init();
        // 第一个周期：两个机构都在第一半周期抓住同一个原子。
        simulator.step();
        assertFalse(simulator.isHalted());
        assertEquals(1, simulator.cycle());
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));

        // 第二个周期：两个机构在第二半周期把同一个实体推向相反方向，冲突回滚并停机。
        simulator.step();

        assertTrue(simulator.isHalted());
        assertEquals(2, simulator.cycle());
        assertTrue(simulator.warnings().get(0).contains("incompatible intents"));
        assertEquals(1, simulator.mechanism("east").length());
        assertEquals(1, simulator.mechanism("west").length());
        assertEquals(sampleAtom(), simulator.atomAt(new GridPoint(1, 0)));
        assertEquals(2, simulator.grabsAt(new GridPoint(1, 0)));
    }

    @Test
    void clockwisePivotRotatesTheMoleculeAroundTheGrabPosition() {
        OrthogonalSimulator simulator = pivotSimulator('e');

        assertPivotedMolecule(simulator, Direction.SOUTH, new GridPoint(0, 1), Direction.NORTH);
    }

    @Test
    void counterClockwisePivotRotatesTheMoleculeAroundTheGrabPosition() {
        OrthogonalSimulator simulator = pivotSimulator('q');

        assertPivotedMolecule(simulator, Direction.NORTH, new GridPoint(0, -1), Direction.SOUTH);
    }

    @Test
    void pivotAfterExtensionUsesTheCurrentLength() {
        Atom grabbed = sampleAtom().withBond(Direction.SOUTH, (byte) 2);
        Atom attached = blockerAtom().withBond(Direction.NORTH, (byte) 2);
        MechanismSpec piston = new MechanismSpec(
                "piston",
                new GridPoint(0, 0),
                Direction.EAST,
                1,
                InstructionTape.once("fwe"));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(1, 0), grabbed),
                new AtomPlacement(new GridPoint(1, 1), attached),
                new AtomPlacement(new GridPoint(10, 10), Atom.of((byte) 9)));
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(piston), List.of(input), List.of(), 1));
        simulator.init();

        simulator.run(3);

        MechanismView view = simulator.mechanism("piston");
        assertEquals(2, view.length());
        assertEquals(new GridPoint(2, 0), view.grabPosition());
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(2, 0)).bondType(Direction.WEST));
        assertEquals((byte) 2, simulator.atomAt(new GridPoint(1, 0)).bondType(Direction.EAST));
        assertNull(simulator.atomAt(new GridPoint(2, 1)));
        assertFalse(simulator.isHalted());
    }

    @Test
    void pivotWithoutAGrabbedMoleculeIsANoOp() {
        OrthogonalSimulator simulator = simulator(new MechanismSpec(
                "arm",
                new GridPoint(7, -4),
                Direction.SOUTH,
                3,
                InstructionTape.once("eq")));

        simulator.run(2);

        assertEquals(
                new MechanismView(
                        "arm", new GridPoint(7, -4), Direction.SOUTH, 3, false),
                simulator.mechanism("arm"));
    }

    private static OrthogonalSimulator pivotSimulator(char pivotInstruction) {
        Atom grabbed = sampleAtom().withBond(Direction.EAST, (byte) 2);
        Atom attached = blockerAtom().withBond(Direction.WEST, (byte) 2);
        MechanismSpec arm = new MechanismSpec(
                "arm",
                new GridPoint(0, 2),
                Direction.NORTH,
                2,
                InstructionTape.once("f" + pivotInstruction));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(0, 0), grabbed),
                new AtomPlacement(new GridPoint(1, 0), attached));
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(arm), List.of(input), List.of(), 1));
        simulator.init();
        simulator.run(2);
        return simulator;
    }

    private static OrthogonalSimulator boundarySimulator(int length, char instruction) {
        MechanismSpec piston = new MechanismSpec(
                "piston",
                new GridPoint(0, 0),
                Direction.EAST,
                length,
                InstructionTape.once("f" + instruction));
        InputSpec input = InputSpec.of(
                "in",
                new AtomPlacement(new GridPoint(length, 0), sampleAtom()),
                new AtomPlacement(new GridPoint(10, 10), blockerAtom()));
        OrthogonalSimulator simulator = new OrthogonalSimulator(
                new SimulationPlan(List.of(piston), List.of(input), List.of(), 1));
        simulator.init();
        simulator.run(2);
        return simulator;
    }

    private static void assertPivotedMolecule(
            OrthogonalSimulator simulator,
            Direction grabbedBond,
            GridPoint attachedPosition,
            Direction attachedBond) {
        MechanismView arm = simulator.mechanism("arm");
        assertEquals(new GridPoint(0, 2), arm.position());
        assertEquals(Direction.NORTH, arm.direction());
        assertEquals(2, arm.length());
        assertTrue(arm.grabbing());

        Atom grabbed = simulator.atomAt(new GridPoint(0, 0));
        Atom attached = simulator.atomAt(attachedPosition);
        assertEquals((byte) 1, grabbed.type());
        assertEquals((byte) 2, grabbed.bondType(grabbedBond));
        assertEquals((byte) 5, attached.type());
        assertEquals((byte) 2, attached.bondType(attachedBond));
        assertNull(simulator.atomAt(new GridPoint(1, 0)));
        assertFalse(simulator.isHalted());
    }

    private static void assertMechanism(
            OrthogonalSimulator simulator,
            int length,
            GridPoint position,
            Direction direction) {
        MechanismView mechanism = simulator.mechanism("piston");
        assertEquals(length, mechanism.length());
        assertEquals(position, mechanism.position());
        assertEquals(direction, mechanism.direction());
    }

    private static OrthogonalSimulator simulator(MechanismSpec mechanism) {
        return new OrthogonalSimulator(SimulationPlan.of(mechanism));
    }

    private static Atom sampleAtom() {
        return Atom.of((byte) 1);
    }

    private static Atom blockerAtom() {
        return Atom.of((byte) 5);
    }
}
