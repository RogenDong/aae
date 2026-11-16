package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.Objects;

/** 单个指令带驱动机构的不可变基类；按计划中的声明顺序定位。 */
public abstract sealed class MechanismSpec permits StationarySpec, PistonSpec {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 3;

    private final GridPoint initialPosition;
    private final Direction initialDirection;
    private final int length;
    private final InstructionTape tape;

    protected MechanismSpec(
            GridPoint initialPosition,
            Direction initialDirection,
            int length,
            InstructionTape tape) {
        this.initialPosition = Objects.requireNonNull(initialPosition, "initialPosition");
        this.initialDirection = Objects.requireNonNull(initialDirection, "initialDirection");
        validateLength(length);
        this.length = length;
        this.tape = Objects.requireNonNull(tape, "tape");
    }

    public final GridPoint initialPosition() {
        return initialPosition;
    }

    public final Direction initialDirection() {
        return initialDirection;
    }

    public final int length() {
        return length;
    }

    /** 初始长度；等价于 {@link #length()}，运行态长度由模拟器单独维护。 */
    public final int initialLength() {
        return length;
    }

    /** 原始指令带，包含当前机构不支持的指令。 */
    public final InstructionTape tape() {
        return tape;
    }

    /** 由具体机构提供几何组合；基类不保存组合字段。 */
    public abstract ArmCombination combination();

    /** 是否支持指定指令；不支持的指令占用原周期但按等待处理。 */
    public abstract boolean supportsInstruction(Instruction instruction);

    /** 模拟执行及运动预览共用的有效指令；不改变原指令带的播放节拍。 */
    public final Instruction instructionAt(long cycle) {
        Instruction instruction = tape.instructionAt(cycle);
        return supportsInstruction(instruction) ? instruction : Instruction.WAIT;
    }

    static void validateLength(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "length must be in [" + MIN_LENGTH + ", " + (MAX_LENGTH + 1) + ")");
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        MechanismSpec spec = (MechanismSpec) other;
        return length == spec.length
                && initialPosition.equals(spec.initialPosition)
                && initialDirection == spec.initialDirection
                && tape.equals(spec.tape)
                && combination().equals(spec.combination());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), initialPosition, initialDirection, length, combination(), tape);
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[initPos=" + initialPosition
                + ", initDir=" + initialDirection + ", len=" + length
                + ", cmb=" + combination() + ", tape=" + tape + "]";
    }
}
