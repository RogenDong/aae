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

    @Test
    void effectiveLengthPadsShorterLoopingTapeWithoutRewritingIt() {
        // 模拟器用统一长度读取短指令带：循环播放按统一长度取模，填充槽位读作等待，
        // 因此每三个周期才回到首条指令；原始长度、起始周期与单带读取结果都保持不变。
        InstructionTape tape = InstructionTape.parse("ga", 0, InstructionTape.Playback.LOOPING);

        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(0, 3));
        assertEquals(Instruction.ROTATE_CCW, tape.instructionAt(1, 3));
        assertEquals(Instruction.WAIT, tape.instructionAt(2, 3));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(3, 3));
        assertEquals(Instruction.ROTATE_CCW, tape.instructionAt(4, 3));
        assertEquals(Instruction.WAIT, tape.instructionAt(5, 3));

        assertEquals(2, tape.length());
        assertEquals(0, tape.startCycle());
        assertEquals(InstructionTape.Playback.LOOPING, tape.playback());
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(2));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(60));
        // 有效长度必须覆盖原始内容；不能借统一长度隐式截断指令。
        assertThrows(IllegalArgumentException.class, () -> tape.instructionAt(0, 1));
    }

    @Test
    void effectiveLengthKeepsOneShotTapeIdleAfterItsContent() {
        // 单次播放的填充语义以原始内容结束为准：统一长度只影响循环节拍，不延长只播一遍的指令带。
        InstructionTape tape = InstructionTape.parse("ga", 3, InstructionTape.Playback.ONCE);

        assertEquals(Instruction.WAIT, tape.instructionAt(2, 8));
        assertEquals(Instruction.MOVE_FORWARD, tape.instructionAt(3, 8));
        assertEquals(Instruction.ROTATE_CCW, tape.instructionAt(4, 8));
        assertEquals(Instruction.WAIT, tape.instructionAt(5, 8));
        assertEquals(Instruction.WAIT, tape.instructionAt(100, 8));
        // 起始周期由各指令带自己决定，不参与统一长度：同一内容换起始周期后读取结果整体偏移。
        assertEquals(Instruction.ROTATE_CCW,
                InstructionTape.parse("ga", 0, InstructionTape.Playback.ONCE).instructionAt(1, 8));
        // 空指令带没有内容可播，任何有效长度都只返回等待。
        assertEquals(Instruction.WAIT, InstructionTape.empty().instructionAt(7, 5));
    }
}
