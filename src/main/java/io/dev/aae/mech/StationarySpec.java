package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 固定臂长的不可变机构定义，支持多个共享状态的机械臂分支。
 *
 * <p>静态仅指运行期间臂长固定，不限制轨道移动或旋转。伸缩指令不受支持，
 * 读取有效指令时按等待处理，仍占用原周期并维持持有约束。</p>
 */
public final class StationarySpec extends MechanismSpec {

    private final ArmCombination combination;

    public StationarySpec(GridPoint position, Direction direction, int length,
                          ArmCombination combination, InstructionTape tape) {
        super(position, direction, length, tape);
        this.combination = Objects.requireNonNull(combination, "combination");
    }

    /** 显式指定固定长度，使用默认单臂。 */
    public StationarySpec(GridPoint position, Direction direction, int length, InstructionTape tape) {
        this(position, direction, length, ArmCombination.DEFAULT, tape);
    }

    public StationarySpec(GridPoint position, Direction direction,
                          ArmCombination combination, InstructionTape tape) {
        this(position, direction, MIN_LENGTH, combination, tape);
    }

    public StationarySpec(GridPoint position, Direction direction, int length,
                          ArmCombination combination, String tape) {
        this(position, direction, length, combination,
                InstructionTape.parse(tape, 0, InstructionTape.Playback.LOOPING));
    }

    /** 第三个参数是组合编码；固定长度默认为一格。 */
    public StationarySpec(GridPoint position, Direction direction, int combination, String tape) {
        this(position, direction, MIN_LENGTH, new ArmCombination(combination), tape);
    }

    public StationarySpec(GridPoint position, Direction direction,
                          ArmCombination combination, String tape) {
        this(position, direction, MIN_LENGTH, combination, tape);
    }

    /** 使用默认长度一格与默认单臂。 */
    public StationarySpec(GridPoint position, Direction direction, InstructionTape tape) {
        this(position, direction, MIN_LENGTH, tape);
    }

    public StationarySpec(GridPoint position, Direction direction, InstructionTape tape, int length) {
        this(position, direction, length, tape);
    }

    /** 使用默认长度一格与默认单臂，字符串指令带循环播放。 */
    public StationarySpec(GridPoint position, Direction direction, String tape) {
        this(position, direction, tape, MIN_LENGTH);
    }

    public StationarySpec(GridPoint position, Direction direction, String tape, int length) {
        this(position, direction, length, ArmCombination.DEFAULT, tape);
    }

    @Override
    public ArmCombination combination() {
        return combination;
    }

    @Override
    public boolean supportsInstruction(Instruction instruction) {
        Objects.requireNonNull(instruction, "instruction");
        return instruction != Instruction.EXTEND_PISTON && instruction != Instruction.RETRACT_PISTON;
    }
}
