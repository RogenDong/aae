package io.dev.aae;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 机构不可变的、按周期寻址的程序。 */
public final class InstructionTape {

    public enum Playback {
        ONCE,
        LOOPING
    }

    private static final InstructionTape EMPTY =
            new InstructionTape(List.of(), 0, Playback.ONCE);

    private final Instruction[] instructions;
    private final long startCycle;
    private final Playback playback;

    public InstructionTape(
            List<Instruction> instructions,
            long startCycle,
            Playback playback) {
        Objects.requireNonNull(instructions, "instructions");
        if (startCycle < 0) {
            throw new IllegalArgumentException("startCycle must be non-negative");
        }
        this.instructions = instructions.toArray(Instruction[]::new);
        for (Instruction instruction : this.instructions) {
            Objects.requireNonNull(instruction, "instructions must not contain null");
        }
        this.startCycle = startCycle;
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    public static InstructionTape empty() {
        return EMPTY;
    }

    public static InstructionTape once(String program) {
        return parse(program, 0, Playback.ONCE);
    }

    public static InstructionTape parse(String program, long startCycle, Playback playback) {
        Objects.requireNonNull(program, "program");
        List<Instruction> parsed = new ArrayList<>(program.length());
        for (int index = 0; index < program.length(); index++) {
            char symbol = program.charAt(index);
            if (!Character.isWhitespace(symbol)) {
                parsed.add(Instruction.fromSymbol(symbol));
            }
        }
        return new InstructionTape(parsed, startCycle, playback);
    }

    public Instruction instructionAt(long cycle) {
        if (cycle < 0) {
            throw new IllegalArgumentException("cycle must be non-negative");
        }
        if (cycle < startCycle || instructions.length == 0) {
            return Instruction.WAIT;
        }

        long elapsed = cycle - startCycle;
        if (playback == Playback.ONCE && elapsed >= instructions.length) {
            return Instruction.WAIT;
        }
        int index = (int) (elapsed % instructions.length);
        return instructions[index];
    }

    public int length() {
        return instructions.length;
    }

    public long startCycle() {
        return startCycle;
    }

    public Playback playback() {
        return playback;
    }
}
