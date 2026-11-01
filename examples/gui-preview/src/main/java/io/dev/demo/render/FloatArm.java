package io.dev.demo.render;

import io.dev.aae.Direction;
import java.util.Objects;

/**
 * 机械臂的连续浮点直角坐标渲染投影。
 */
public record FloatArm(
        String id,
        double x,
        double y,
        double angle,    // 弧度制，机械臂当前绝对指向角
        double length,   // 机械臂长度（默认1格=1.0）
        boolean grabbing  // 是否抓握
) {
    public FloatArm {
        Objects.requireNonNull(id, "id");
    }

    /**
     * 将正交方向转换为弧度制角度。
     * 在标准直角坐标系中（X向右/东，Y向下/南）：
     * EAST = 0 rad (沿+X)
     * SOUTH = PI/2 rad (沿+Y)
     * WEST = PI rad (沿-X)
     * NORTH = -PI/2 rad (沿-Y)
     */
    public static double directionToAngle(Direction dir) {
        return switch (dir) {
            case EAST -> 0.0;
            case SOUTH -> Math.PI / 2.0;
            case WEST -> Math.PI;
            case NORTH -> -Math.PI / 2.0;
        };
    }
}
