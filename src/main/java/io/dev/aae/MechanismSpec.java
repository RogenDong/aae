package io.dev.aae;

import java.util.Objects;

/** 单个指令带驱动机构的不可变定义。 */
public record MechanismSpec(
        String id,
        GridPoint initialPosition,
        Direction initialDirection,
        int length,
        InstructionTape tape) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 3;

    /** 兼容原有固定一格机构的构造方式。 */
    public MechanismSpec(
            String id,
            GridPoint initialPosition,
            Direction initialDirection,
            InstructionTape tape) {
        this(id, initialPosition, initialDirection, MIN_LENGTH, tape);
    }

    /** 允许调用方在原有参数顺序末尾补充长度。 */
    public MechanismSpec(
            String id,
            GridPoint initialPosition,
            Direction initialDirection,
            InstructionTape tape,
            int length) {
        this(id, initialPosition, initialDirection, length, tape);
    }

    public MechanismSpec {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(initialPosition, "initialPosition");
        Objects.requireNonNull(initialDirection, "initialDirection");
        validateLength(length);
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
