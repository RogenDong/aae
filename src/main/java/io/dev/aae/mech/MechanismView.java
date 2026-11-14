package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.Objects;

/** 机构的只读状态；{@code position} 表示基座格点。 */
public record MechanismView(
        GridPoint position,
        Direction direction,
        int length,
        boolean grabbing) {

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
    }

    /** 当前抓握点；机构长度是从基座到抓握点的格数。 */
    public GridPoint grabPosition() {
        return position.translate(direction, length);
    }
}
