package io.dev.aae;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 不可变原子：一个 {@code int} 原子类型加上四个方向的 {@code byte} 化学键类型。
 *
 * <p>原子类型的有效范围为 {@code 0..0xFFFF}，其中 {@code 0} 也是有效类型。
 * 四方向键单独打包在 {@code int bondCodes} 中，每方向一个字节，4 字节正好：
 * {@code bondCodes} 的第 {@code 8 * direction.ordinal()} 位起存放该方向的键类型。</p>
 *
 * <p>本项目不解释原子类型和键类型的名字：编码由调用方给出，
 * {@link #of(int)} 校验原子类型范围，键接口原样保存和返回字节。
 * 内部只依赖两条键数值约定：</p>
 *
 * <ul>
 *   <li>键字节 {@code 0} 表示该方向没有键；</li>
 *   <li>键字节 {@code 0xFF} 表示本周期刚建立、下一周期会被清除的临时键，因此
 *       {@link #isRealBond(byte)} 把其余非零字节都算作长期键。</li>
 * </ul>
 *
 * <p>可变操作（{@link #withBond}、{@link #rotateBonds}）都返回新实例，
 * 调用方不需要关心共享与写时复制。</p>
 */
public record Atom(int type, int bondCodes) {

    /**
     * 由原子类型和打包键还原原子，例如 {@code new Atom(atom.type(), atom.bondCodes())}。
     *
     * @param type 原子类型，范围 {@code 0..0xFFFF}
     * @param bondCodes 每方向一个键字节，最低字节对应 {@link Direction#NORTH}
     * @throws IllegalArgumentException 原子类型超出有效范围
     */
    public Atom {
        requireAtomCode(type);
    }

    /** 键字节 {@code 0}：该方向没有键 */
    private static final byte NO_BOND = 0;

    /** 键字节 {@code 0xFF}：本周期刚建立、下一周期会被清除的临时键 */
    public static final byte TEMPORARY_BOND = (byte) 0xFF;

    private static final int BITS_PER_DIRECTION = Byte.SIZE;
    private static final int DIRECTION_MASK = 0xFF;

    /**
     * 新建一个没有任何化学键的原子；类型范围为 {@code 0..0xFFFF}。
     *
     * @param type 原子类型，包含 {@code 0}
     * @return 没有化学键的原子
     * @throws IllegalArgumentException 类型超出 {@code 0..0xFFFF}
     */
    public static Atom of(int type) {
        return new Atom(type, NO_BOND);
    }

    /**
     * 该方向上的键类型字节；没有键时返回 {@code 0}。
     */
    public byte bondType(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return bondByte(bondCodes, direction);
    }

    /**
     * 原始键字节数组，{@code [北, 东, 南, 西]}。
     */
    public byte[] bondTypes() {
        return new byte[]{
                (byte) (bondCodes & 0xFF),
                (byte) (bondCodes >>> 8),
                (byte) (bondCodes >>> 16),
                (byte) (bondCodes >>> 24),
        };
    }

    /**
     * 该方向上是否存在任何键，包括临时键。
     */
    public boolean hasBond(Direction direction) {
        return bondType(direction) != NO_BOND;
    }

    /**
     * 长期键判据：{@code 0} 是无键，{@link Atom#TEMPORARY_BOND} 是临时键，其余非零字节都算长期键。
     *
     * <p>去掉每周期被清除的临时键后剩下的键。这里不列举具体键类型，只按数值判断。</p>
     */
    public static boolean isRealBond(byte bondCode) {
        return bondCode != NO_BOND && bondCode != TEMPORARY_BOND;
    }

    /**
     * 该方向上的键是否是“刚解除键”的临时标记。
     */
    public boolean isTemporaryBond(Direction direction) {
        return bondType(direction) == TEMPORARY_BOND;
    }

    /**
     * 该原子是否存在任意方向的“刚解除键”临时标记。
     */
    public boolean hasTemporaryBond() {
        return (bondCodes & 0xFF000000) == 0xFF000000
                || (bondCodes & 0x00FF0000) == 0x00FF0000
                || (bondCodes & 0x0000FF00) == 0x0000FF00
                || (bondCodes & 0x000000FF) == 0x000000FF;
    }

    /**
     * 该方向上是否存在长期保留的键。
     */
    public boolean hasRealBond(Direction direction) {
        return isRealBond(bondType(direction));
    }

    /**
     * 按 {@link Direction#values()} 的顺序返回存在长期键的方向。
     */
    public List<Direction> bondedDirections() {
        List<Direction> directions = new ArrayList<>(4);
        final int m = TEMPORARY_BOND & DIRECTION_MASK;
        int tmp = bondCodes & DIRECTION_MASK;
        if (tmp != 0 && tmp != m) directions.add(Direction.NORTH);

        tmp = (bondCodes >>> 8) & DIRECTION_MASK;
        if (tmp != 0 && tmp != m) directions.add(Direction.EAST);

        tmp = (bondCodes >>> 16) & DIRECTION_MASK;
        if (tmp != 0 && tmp != m) directions.add(Direction.SOUTH);

        tmp = (bondCodes >>> 24) & DIRECTION_MASK;
        if (tmp != 0 && tmp != m) directions.add(Direction.WEST);
        return directions;
    }

    /**
     * 长期键的数量。
     */
    public int bondCount() {
        if (bondCodes == NO_BOND || bondCodes == -1) return 0;
        int count = 0;
        for (int mask = TEMPORARY_BOND & DIRECTION_MASK; mask != 0; mask <<= 8) {
            int tmp = bondCodes & mask;
            if (tmp != 0 && tmp != mask)
                count++;
        }
        return count;
    }

    /**
     * 替换指定方向的键类型；传入的字节原样写入。
     */
    public Atom withBond(Direction direction, int bondType) {
        return this.withBond(direction, (byte) bondType);
    }

    /**
     * 替换指定方向的键类型；传入的字节原样写入。
     */
    public Atom withBond(Direction direction, byte bondType) {
        Objects.requireNonNull(direction, "direction");
        int cleared = bondCodes & ~(DIRECTION_MASK << shift(direction));
        int written = (bondType & DIRECTION_MASK) << shift(direction);
        return new Atom(type, cleared | written);
    }

    /**
     * 清除指定方向的键。
     */
    public Atom withoutBond(Direction direction) {
        return withBond(direction, NO_BOND);
    }

    /**
     * 清除所有临时键。
     */
    public Atom withoutTemporaryBonds() {
        if (bondCodes == 0xFFFFFFFF) return new Atom(type, NO_BOND);
        int mask = 0;
        if ((bondCodes & 0xFF000000) == 0xFF000000) mask |= 0xFF000000;
        if ((bondCodes & 0x00FF0000) == 0x00FF0000) mask |= 0x00FF0000;
        if ((bondCodes & 0x0000FF00) == 0x0000FF00) mask |= 0x0000FF00;
        if ((bondCodes & 0x000000FF) == 0x000000FF) mask |= 0x000000FF;
        return new Atom(type, bondCodes ^ mask);
    }

    /**
     * 清除全部键。
     */
    public Atom withoutBonds() {
        return new Atom(type, NO_BOND);
    }

    /**
     * 只替换原子类型，保留键位。
     *
     * @param newType 新原子类型，范围 {@code 0..0xFFFF}，包含 {@code 0}
     * @return 类型替换后的原子
     * @throws IllegalArgumentException 类型超出 {@code 0..0xFFFF}
     */
    public Atom withType(int newType) {
        return new Atom(newType, bondCodes);
    }

    /**
     * 顺时针旋转全部键位 {@code quarterTurns} 个 90 度。
     *
     * <p>旋转量先按模 4 归一化，因此旋转 4 次是恒等变换。本质上是移位循环。</p>
     */
    public Atom rotateBonds(int quarterTurns) {
        var rotated = switch (quarterTurns & 3) {
            case 1 -> (bondCodes >>> 24) | (bondCodes << 8);
            case 2 -> (bondCodes >>> 16) | (bondCodes << 16);
            case 3 -> (bondCodes >>> 8) | (bondCodes << 24);
            default -> bondCodes;
        };
        return new Atom(type, rotated);
    }

    private static byte bondByte(int bondCodes, Direction direction) {
        return (byte) ((bondCodes >>> shift(direction)) & DIRECTION_MASK);
    }

    private static int shift(Direction direction) {
        return direction.ordinal() * BITS_PER_DIRECTION;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Atom atom
                && type == atom.type
                && bondCodes == atom.bondCodes;
    }

    /**
     * 形如 {@code type=3 N=2 E=0 S=0 W=0}；类型用十进制，四方向键用十六进制。
     */
    @Override
    public String toString() {
        return "type=" + type +
                " N=" + Integer.toHexString(bondCodes & 0xFF) +
                " E=" + Integer.toHexString((bondCodes >>> 8) & 0xFF) +
                " S=" + Integer.toHexString((bondCodes >>> 16) & 0xFF) +
                " W=" + Integer.toHexString((bondCodes >>> 24) & 0xFF);
    }

    /**
     * 校验一个原子类型：范围 {@code 0..0xFFFF}，允许 {@code 0}。
     *
     * @param code 原子类型编码
     * @throws IllegalArgumentException 类型为负数或大于 {@code 0xFFFF}
     */
    public static void requireAtomCode(int code) {
        if ((code & 0xFFFF0000) != 0) {
            throw new IllegalArgumentException("atom type must be in 0..0xFFFF: " + code);
        }
    }

    /**
     * 校验一个化学键类型字节：既不是 {@code 0}（无键）也不是 {@link Atom#TEMPORARY_BOND}（临时键标记）。
     *
     * @throws IllegalArgumentException 字节是 {@code 0} 或 {@link Atom#TEMPORARY_BOND}
     */
    public static void requireBondCode(byte code) {
        if (code == 0 || code == TEMPORARY_BOND) {
            throw new IllegalArgumentException(
                    "bond type must not be 0 (no bond) or " + TEMPORARY_BOND + " (temporary bond): " + (code & 0xFF));
        }
    }
}
