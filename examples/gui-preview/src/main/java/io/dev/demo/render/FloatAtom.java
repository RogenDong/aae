package io.dev.demo.render;

import io.dev.aae.Atom;

import java.util.Objects;

/**
 * 原子的连续浮点直角坐标渲染投影。
 */
public record FloatAtom(
        double x,
        double y,
        double rotationAngle, // 弧度制，原子自转角
        byte atomType,
        byte[] bondCodes,     // 四个正交方向 (NORTH, EAST, SOUTH, WEST) 的键编码
        float alpha           // 透明度 [0.0, 1.0]，用于渲染常规实体(1.0)或模板半透明分子(<1.0)
) {
    public FloatAtom {
        Objects.requireNonNull(bondCodes, "bondCodes");
        if (bondCodes.length != 4) {
            throw new IllegalArgumentException("bondCodes length must be 4");
        }
    }

    public static FloatAtom fromAtom(double x, double y, Atom atom, float alpha) {
        return new FloatAtom(x, y, 0.0, atom.type(), atom.bondTypes(), alpha);
    }
}
