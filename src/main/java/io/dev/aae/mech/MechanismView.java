package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.Objects;

/** 机构的只读状态；{@code position} 表示基座格点。 */
public record MechanismView(
        GridPoint position,
        Direction direction,
        int length,
        ArmCombination combination,
        boolean grabbing) {

    /** 兼容原有显式长度单臂构造器。 */
    public MechanismView(GridPoint position, Direction direction, int length, boolean grabbing) {
        this(position, direction, length, ArmCombination.DEFAULT, grabbing);
    }

    public MechanismView(GridPoint position, Direction direction,
                         ArmCombination combination, boolean grabbing) {
        this(position, direction, MechanismSpec.MIN_LENGTH, combination, grabbing);
    }

    /** 使用默认长度一格。 */
    public MechanismView(
            GridPoint position, Direction direction, boolean grabbing) {
        this(position, direction, MechanismSpec.MIN_LENGTH, grabbing);
    }

    /** 允许调用方在参数顺序末尾指定长度。 */
    public MechanismView(
            GridPoint position,
            Direction direction,
            boolean grabbing,
            int length) {
        this(position, direction, length, grabbing);
    }

    public MechanismView {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        MechanismSpec.validateLength(length);
        Objects.requireNonNull(combination, "combination");
    }

    /** 单分支的实际抓点；多分支不隐式选择其中一个。 */
    public GridPoint grabPosition() {
        if (combination.armCount() != 1) {
            throw new IllegalStateException("multiple arms require a bit index");
        }
        return grabPosition(combination.enabledBits().get(0));
    }

    /** 按固定 bit 下标查询启用分支的抓点。 */
    public GridPoint grabPosition(int iArm) {
        return combination.grabPosition(position, direction, length, iArm);
    }

    /** 按启用位升序返回所有抓点的不可变快照。 */
    public java.util.List<GridPoint> grabPositions() {
        return combination.grabPositions(position, direction, length);
    }
}
