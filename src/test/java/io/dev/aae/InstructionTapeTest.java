package io.dev.aae;

import io.dev.aae.mech.Instruction;
import io.dev.aae.mech.InstructionTape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InstructionTapeTest {

    @Test
    void loopingTapeWaitsForItsStartCycleAndThenWraps() {
        InstructionTape tape = InstructionTape.parse(
                "g d .",
                2,
                InstructionTape.Playback.LOOPING);

        assertEquals(Instruction.WAIT, tape.instructionAt(0));
        assertEquals(Instruction.WAIT, tape.instructionAt(1));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(2));
        assertEquals(Instruction.ROTATE_CW, tape.instructionAt(3));
        assertEquals(Instruction.WAIT, tape.instructionAt(4));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(5));
    }

    @Test
    void oneShotTapeBecomesIdleAfterItsLastInstruction() {
        InstructionTape tape = InstructionTape.once("ga");

        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(0));
        assertEquals(Instruction.ROTATE_CCW, tape.instructionAt(1));
        assertEquals(Instruction.WAIT, tape.instructionAt(2));
        assertEquals(Instruction.WAIT, tape.instructionAt(1_000));
    }

    @Test
    void emptyTapeAlwaysWaits() {
        assertEquals(0, InstructionTape.empty().length());
        assertEquals(Instruction.WAIT, InstructionTape.empty().instructionAt(50));
    }

    @Test
    void parserRejectsUnknownSymbolsAndNegativeCycles() {
        assertThrows(IllegalArgumentException.class, () -> InstructionTape.once("g?"));
        assertThrows(
                IllegalArgumentException.class,
                () -> InstructionTape.parse("g", -1, InstructionTape.Playback.ONCE));
        assertThrows(IllegalArgumentException.class, () -> InstructionTape.once("g").instructionAt(-1));
    }

    @Test
    void grabAndReleaseSymbolsAreCaseSensitive() {
        InstructionTape tape = InstructionTape.once("f r g d");

        assertEquals(Instruction.GRAB, tape.instructionAt(0));
        assertEquals(Instruction.RELEASE, tape.instructionAt(1));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(2));
        assertEquals(Instruction.ROTATE_CW, tape.instructionAt(3));
    }

    @Test
    void everyDeclaredInstructionSymbolRoundTrips() {
        for (Instruction instruction : Instruction.values()) {
            assertEquals(instruction, Instruction.fromSymbol(instruction.symbol()));
        }
    }
}
