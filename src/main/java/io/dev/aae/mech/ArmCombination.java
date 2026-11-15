package io.dev.aae.mech;

import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 相对机构基准朝向的8位组合；分支身份固定为 bit 下标。 */
public record ArmCombination(int code) {
    public static final ArmCombination DEFAULT = new ArmCombination(0x01);
    private static final List<GridPoint> OFFSETS = List.of(
            new GridPoint(1, 0), new GridPoint(0, 1),
            new GridPoint(-1, 0), new GridPoint(0, -1),
            new GridPoint(1, 1), new GridPoint(-1, 1),
            new GridPoint(-1, -1), new GridPoint(1, -1));

    public ArmCombination {
        if (code < 1 || code > 0xFF) {
            throw new IllegalArgumentException("arm combination must be in [1, 255]");
        }
    }

    /** 显式解释 Java 有符号 byte 的无符号八位编码。 */
    public static ArmCombination fromByte(byte code) {
        return new ArmCombination(Byte.toUnsignedInt(code));
    }

    public int armCount() {
        return Integer.bitCount(code);
    }

    public List<Integer> enabledBits() {
        var bits = new ArrayList<Integer>(armCount());
        for (int bit = 0; bit < 8; bit++) {
            if ((code & (1 << bit)) != 0) bits.add(bit);
        }
        return List.copyOf(bits);
    }

    /** 未启用的位不是当前机构的分支。 */
    public GridPoint localOffset(int iArm) {
        Objects.checkIndex(iArm, 8);
        if ((code & (1 << iArm)) == 0) {
            throw new IllegalArgumentException("disabled arm bit: " + iArm);
        }
        return OFFSETS.get(iArm);
    }

    /** 未启用的位不是当前机构的分支。 */
    private GridPoint localOffsetByFlag(int flag) {
        return switch (flag) {
            case 1 -> OFFSETS.get(0);
            case 2 -> OFFSETS.get(1);
            case 4 -> OFFSETS.get(2);
            case 8 -> OFFSETS.get(3);
            case 16 -> OFFSETS.get(4);
            case 32 -> OFFSETS.get(5);
            case 64 -> OFFSETS.get(6);
            case 128 -> OFFSETS.get(7);
            default -> throw new IllegalArgumentException("disabled arm bit-flag: " + Integer.toBinaryString(flag));
        };
    }

    public GridPoint worldOffset(Direction direction, int iArm) {
        return direction.localOffsetToWorld(localOffset(iArm));
    }

    public GridPoint grabPosition(GridPoint base, Direction direction, int length, int iArm) {
        MechanismSpec.validateLength(length);
        GridPoint offset = localOffset(iArm);
        return direction.localToWorld(base, new GridPoint(
                offset.x() * length,
                offset.y() * length));
    }

    public List<GridPoint> grabPositions(GridPoint base, Direction direction, int length) {
        List<GridPoint> result = new ArrayList<>(8);
        for (int flag = 1; flag < 0x100; flag <<= 1) {
            if ((code & flag) != 0) {
                GridPoint offset = localOffsetByFlag(flag);
                result.add(direction.localToWorld(base, new GridPoint(
                        offset.x() * length,
                        offset.y() * length)));
            }
        }
        return List.copyOf(result);
    }

    public GridPoint[] grabPositionsArray(GridPoint base, Direction direction, int length) {
        List<GridPoint> result = new ArrayList<>(8);
        for (int flag = 1; flag < 0x100; flag <<= 1) {
            if ((code & flag) == 0) {
                result.add(null);
                continue;
            }
            GridPoint offset = localOffsetByFlag(flag);
            result.add(direction.localToWorld(base, new GridPoint(
                    offset.x() * length,
                    offset.y() * length)));
        }
        return result.toArray(new GridPoint[8]);
    }
}
