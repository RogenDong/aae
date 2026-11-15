package io.dev.demo.render;

import io.dev.aae.Direction;

/**
 * 机械臂的连续浮点直角坐标渲染投影。
 */
public record FloatArm(
        int mechanismIndex,
        int armBit,  // 组合编码中的真实位下标，不随启用分支数量重排
        double x,
        double y,
        double angle,    // 弧度制，机械臂当前绝对指向角
        double length,   // 分支实际杆长，轴向为机构长度，对角为机构长度乘sqrt(2)
        boolean grabbing  // 共享机构抓握状态，不是独立分支状态
) {
    /** 保留单臂投影的旧构造入口，其分支身份为默认 bit 0。 */
    public FloatArm(int mechanismIndex, double x, double y,
                    double angle, double length, boolean grabbing) {
        this(mechanismIndex, 0, x, y, angle, length, grabbing);
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
