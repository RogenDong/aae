package io.dev.aae;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 不可变原子：一个原子类型字节加上四个方向的化学键类型字节。
 *
 * <p>把原子类型、状态和方向键位全部压进一个 {@code int}。
 * 正交网格每个格点最多四条边，因此这里改成“每方向一个键类型字节”，4 字节正好：
 * {@code bondCodes} 的第 {@code 8 * direction.ordinal()} 位起存放该方向的键类型。</p>
 *
 * <p>本项目不解释原子类型和键类型的名字：类型字节由调用方按自己的编码体系给出，
 * {@link #of(byte)}、{@link #withBond}、{@link AtomGrid#bond} 只原样保存和返回数值，
 * 不校验取值范围。内部只依赖两条数值约定：</p>
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
public final class Atom {

    /** 键字节 {@code 0}：该方向没有键。 */
    private static final byte NO_BOND = 0;

    /** 键字节 {@code 0xFF}：本周期刚建立、下一周期会被清除的临时键。 */
    public static final byte TEMPORARY_BOND = (byte) 0xFF;

    private static final int BITS_PER_DIRECTION = Byte.SIZE;
    private static final int DIRECTION_MASK = 0xFF;

    private final byte type;
    private final int bondCodes;

    private Atom(byte type, int bondCodes) {
        this.type = type;
        this.bondCodes = bondCodes;
    }

    /** 新建一个没有任何化学键的原子；类型字节原样保存。 */
    public static Atom of(byte type) {
        return new Atom(type, NO_BOND);
    }

    /**
     * 由类型字节和打包后的键字节还原原子，用于序列化往返。
     *
     * @param bondCodes 每个方向一个字节，低位方向是 {@link Direction#NORTH}
     */
    public static Atom of(byte type, int bondCodes) {
        return new Atom(type, bondCodes);
    }

    /** 原子类型字节，原样返回调用方设置的数值。 */
    public byte type() {
        return type;
    }

    /** 原始键字节，每方向一个。 */
    public int bondCodes() {
        return bondCodes;
    }

    /** 该方向上的键类型字节；没有键时返回 {@code 0}。 */
    public byte bondType(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return bondByte(bondCodes, direction);
    }

    /** 原始键字节数组，{@code [北, 东, 南, 西]}。 */
    public byte[] bondTypes() {
        return new byte[]{
                (byte) (bondCodes & 0xFF),
                (byte) (bondCodes >>> 8),
                (byte) (bondCodes >>> 16),
                (byte) (bondCodes >>> 24),
        };
    }

    /** 该方向上是否存在任何键，包括临时键。 */
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

    /** 该方向上的键是否是“刚解除键”的临时标记。 */
    public boolean isTemporaryBond(Direction direction) {
        return bondType(direction) == TEMPORARY_BOND;
    }

    /** 该原子是否存在任意方向的“刚解除键”临时标记。 */
    public boolean hasTemporaryBond() {
        return (bondCodes & 0xFF000000) == 0xFF000000
            || (bondCodes & 0x00FF0000) == 0x00FF0000
            || (bondCodes & 0x0000FF00) == 0x0000FF00
            || (bondCodes & 0x000000FF) == 0x000000FF;
    }

    /** 该方向上是否存在长期保留的键。 */
    public boolean hasRealBond(Direction direction) {
        return isRealBond(bondType(direction));
    }

    /** 按 {@link Direction#values()} 的顺序返回存在长期键的方向。 */
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

    /** 长期键的数量。 */
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

    /** 替换指定方向的键类型；传入的字节原样写入。 */
    public Atom withBond(Direction direction, byte bondType) {
        Objects.requireNonNull(direction, "direction");
        int cleared = bondCodes & ~(DIRECTION_MASK << shift(direction));
        int written = (bondType & DIRECTION_MASK) << shift(direction);
        return new Atom(type, cleared | written);
    }

    /** 清除指定方向的键。 */
    public Atom withoutBond(Direction direction) {
        return withBond(direction, NO_BOND);
    }

    /** 清除所有临时键。 */
    public Atom withoutTemporaryBonds() {
        if (bondCodes == 0xFFFFFFFF) return new Atom(type, NO_BOND);
        int mask = 0;
        if ((bondCodes & 0xFF000000) == 0xFF000000) mask |= 0xFF000000;
        if ((bondCodes & 0x00FF0000) == 0x00FF0000) mask |= 0x00FF0000;
        if ((bondCodes & 0x0000FF00) == 0x0000FF00) mask |= 0x0000FF00;
        if ((bondCodes & 0x000000FF) == 0x000000FF) mask |= 0x000000FF;
        return new Atom(type, bondCodes ^ mask);
    }

    /** 清除全部键。 */
    public Atom withoutBonds() {
        return new Atom(type, NO_BOND);
    }

    /** 只替换原子类型，保留键位。 */
    public Atom withType(byte newType) {
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
            case 3 -> (bondCodes >>>  8) | (bondCodes << 24);
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

    @Override
    public int hashCode() {
        return Objects.hash(type, bondCodes);
    }

    /** 形如 {@code type=3 NORTH=2}；只列出非零键位，数值直接用十进制。 */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("type=").append(type);
        for (Direction direction : Direction.values()) {
            byte bondType = bondType(direction);
            if (bondType != NO_BOND) {
                builder.append(' ').append(direction).append('=').append(bondType);
            }
        }
        return builder.toString();
    }

    /**
     * 校验一个原子类型字节：非 {@code 0}。
     *
     * @throws IllegalArgumentException 字节是 {@code 0}
     */
    public static void requireAtomCode(byte code) {
        if (code == 0) {
            throw new IllegalArgumentException("atom type 0 is not a real atom type");
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
