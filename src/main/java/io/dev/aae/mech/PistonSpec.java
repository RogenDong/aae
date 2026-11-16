package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.Objects;

/**
 * 臂长可在运行期间变化的不可变活塞机构定义。
 *
 * <p>仅支持默认单臂，不接受组合参数；伸缩指令在长度上下界处为空操作。</p>
 */
public final class PistonSpec extends MechanismSpec {

    public PistonSpec(GridPoint position, Direction direction, int length, InstructionTape tape) {
        super(position, direction, length, tape);
    }

    /** 使用默认初始长度一格。 */
    public PistonSpec(GridPoint position, Direction direction, InstructionTape tape) {
        this(position, direction, MIN_LENGTH, tape);
    }

    public PistonSpec(GridPoint position, Direction direction, InstructionTape tape, int length) {
        this(position, direction, length, tape);
    }

    /** 使用默认初始长度一格，字符串指令带循环播放。 */
    public PistonSpec(GridPoint position, Direction direction, String tape) {
        this(position, direction, MIN_LENGTH, tape);
    }

    public PistonSpec(GridPoint position, Direction direction, int length, String tape) {
        this(position, direction, length,
                InstructionTape.parse(tape, 0, InstructionTape.Playback.LOOPING));
    }

    public PistonSpec(GridPoint position, Direction direction, String tape, int length) {
        this(position, direction, length, tape);
    }

    @Override
    public ArmCombination combination() {
        return ArmCombination.DEFAULT;
    }

    @Override
    public boolean supportsInstruction(Instruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        return true;
    }
}
