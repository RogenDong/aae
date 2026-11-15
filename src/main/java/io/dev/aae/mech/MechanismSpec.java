package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.Objects;

/** 单个指令带驱动机构的不可变定义；按计划中的声明顺序定位。 */
public record MechanismSpec(
        GridPoint initialPosition,
        Direction initialDirection,
        int length,
        ArmCombination combination,
        InstructionTape tape) {

    /** 兼容原有显式长度单臂构造器。 */
    public MechanismSpec(GridPoint position, Direction direction, int length, InstructionTape tape) {
        this(position, direction, length, ArmCombination.DEFAULT, tape);
    }

    public MechanismSpec(GridPoint position, Direction direction,
                         ArmCombination combination, InstructionTape tape) {
        this(position, direction, MIN_LENGTH, combination, tape);
    }

    public MechanismSpec(GridPoint position, Direction direction, int length,
                         ArmCombination combination, String tape) {
        this(position, direction, length, combination,
                InstructionTape.parse(tape, 0, InstructionTape.Playback.LOOPING));
    }

    public MechanismSpec(GridPoint position, Direction direction, int combination, String tape) {
        this(position, direction, MIN_LENGTH, new ArmCombination(combination), tape);
    }

    public MechanismSpec(GridPoint position, Direction direction,
                         ArmCombination combination, String tape) {
        this(position, direction, MIN_LENGTH, combination, tape);
    }

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 3;

    /** 使用默认长度一格。 */
    public MechanismSpec(
            GridPoint initialPosition,
            Direction initialDirection,
            InstructionTape tape) {
        this(initialPosition, initialDirection, MIN_LENGTH, tape);
    }

    /** 允许调用方在参数顺序末尾指定长度。 */
    public MechanismSpec(
            GridPoint initialPosition,
            Direction initialDirection,
            InstructionTape tape,
            int length) {
        this(initialPosition, initialDirection, length, tape);
    }

    /** 使用默认长度一格。 */
    public MechanismSpec(
            GridPoint initialPosition,
            Direction initialDirection,
            String tape) {
        this(initialPosition, initialDirection, tape, MIN_LENGTH);
    }

    /** 使用默认长度一格。 */
    public MechanismSpec(
            GridPoint initialPosition,
            Direction initialDirection,
            String tape,
            int length) {
        this(initialPosition,
                initialDirection,
                InstructionTape.parse(tape, 0, InstructionTape.Playback.LOOPING),
                length);
    }

    public MechanismSpec {
        Objects.requireNonNull(initialPosition, "initialPosition");
        Objects.requireNonNull(initialDirection, "initialDirection");
        validateLength(length);
        Objects.requireNonNull(combination, "combination");
        Objects.requireNonNull(tape, "tape");
    }

    static void validateLength(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "length must be in [" + MIN_LENGTH + ", " + (MAX_LENGTH + 1) + ")");
        }
    }

    /** 初始长度；等价于 {@link #length()}。 */
    public int initialLength() {
        return length;
    }
}
