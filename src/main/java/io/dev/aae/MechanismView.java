package io.dev.aae;

import java.util.Objects;

/** 机构的只读状态；{@code position} 表示基座格点。 */
public record MechanismView(
        String id,
        GridPoint position,
        Direction direction,
        int length,
        boolean grabbing) {

    /** 兼容原有固定一格机构的观察值构造方式。 */
    public MechanismView(
            String id, GridPoint position, Direction direction, boolean grabbing) {
        this(id, position, direction, MechanismSpec.MIN_LENGTH, grabbing);
    }

    /** 允许调用方在原有参数顺序末尾补充长度。 */
    public MechanismView(
            String id,
            GridPoint position,
            Direction direction,
            boolean grabbing,
            int length) {
        this(id, position, direction, length, grabbing);
    }

    public MechanismView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        MechanismSpec.validateLength(length);
    }

    /** 当前抓握点；机构长度是从基座到抓握点的格数。 */
    public GridPoint grabPosition() {
        return position.translate(direction, length);
    }
}
