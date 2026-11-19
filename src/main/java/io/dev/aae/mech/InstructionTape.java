package io.dev.aae.mech;

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
        return instructionAt(cycle, instructions.length);
    }

    /**
     * 以指定的有效长度读取指令，供模拟器把不同长度的指令带同步到统一节拍。
     *
     * <p>{@code effectiveLength} 只改变读取视图，不修改原始内容、{@link #length()}、播放模式或
     * {@link #startCycle()}。原始内容之后的填充槽位读作 {@link Instruction#WAIT}；
     * {@link Playback#LOOPING} 按有效长度取模，因此较短的指令带会在统一周期内先等待再回到首条
     * 指令，而 {@link Playback#ONCE} 仍在原始内容结束后保持等待。</p>
     *
     * @param cycle           当前周期，必须非负
     * @param effectiveLength 有效长度，必须不小于原始指令带长度；原始指令带为空时忽略并通过
     * @return 该周期读取到的指令；尚未到达 {@code startCycle} 或处于填充槽位时返回等待
     * @throws IllegalArgumentException 周期为负，或有效长度小于原始指令带长度
     */
    public Instruction instructionAt(long cycle, int effectiveLength) {
        if (cycle < 0) {
            throw new IllegalArgumentException("cycle must be non-negative");
        }
        if (instructions.length == 0) {
            return Instruction.WAIT;
        }
        if (effectiveLength < instructions.length) {
            throw new IllegalArgumentException(
                    "effectiveLength must not be shorter than the tape length");
        }
        if (cycle < startCycle) {
            return Instruction.WAIT;
        }

        long elapsed = cycle - startCycle;
        // 单次播放以原始内容结束为准，统一长度只影响循环播放的周期长度。
        if (playback == Playback.ONCE && elapsed >= instructions.length) {
            return Instruction.WAIT;
        }
        int index = (int) (elapsed % effectiveLength);
        return index < instructions.length ? instructions[index] : Instruction.WAIT;
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
