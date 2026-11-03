package io.dev.demo.om;

import io.dev.aae.*;

import java.util.List;
import java.util.Optional;

/**
 * 本类只固化内建 {@code bond/unbond} 使用的普通键字节 {@code 1}。
 *
 * <p>两个符文都只作用于锚点与局部 {@code (1,0)} 两格：{@code Bonding} 在缺少键时建立普通键，
 * {@code Unbonding} 在存在互惠长期键时拆键并写下临时标记。它们是“双半周期可验收”的最小
 * 内建样例：两个半周期各执行一次，后一个符文能看到前一个符文在同一半周期提交的变更。</p>
 */
public final class OMGlyphs {

    /** {@code bond/unbond} 两个内建符文的占位：锚点加上局部正 x 一格，旋转后沿实例朝向。 */
    private static final List<GridPoint> PAIR_PLACEHOLDER = List.of(
            new GridPoint(0, 0),
            new GridPoint(1, 0));

    /** {@code bond/unbond} 两个内建符文的输入位。 */
    private static final List<GlyphDefinition.InputSlot> PAIR_INPUT_SLOTS = List.of(
            GlyphDefinition.InputSlot.occupied(0),
            GlyphDefinition.InputSlot.occupied(1));

    /** 内建符文：键合 */
    public static final String BONDING_ID = "base:bonding";
    public static final GlyphDefinition<?> BONDING = OMGlyphs.bonding();

    /** 内建符文：拆键*/
    public static final String UNBONDING_ID = "base:unbonding";
    public static final GlyphDefinition<?> UNBONDING = OMGlyphs.unbonding();

    /** 内建符文：钙化*/
    public static final String CALCIFICATION_ID = "base:calcification";
    public static final GlyphDefinition<?> CALCIFICATION = OMGlyphs.calcification();

    private OMGlyphs() {
    }

    /**
     * {@code Bonding}：两格都有原子时建立普通键。
     *
     * <p>输入声明要求两格都有原子——任缺一格就整条符文跳过。冲突规则交给
     * {@link GlyphBoard#bond}：同键、异键静默跳过，单侧残留键直接覆盖。</p>
     */
    private static GlyphDefinition<Void> bonding() {
        return GlyphDefinition.of(
                BONDING_ID,
                PAIR_PLACEHOLDER,
                PAIR_INPUT_SLOTS,
                List.of(),
                OMGlyphs::bondPair);
    }

    public static GlyphSpec bondingSpec(long x, long y, Direction direction) {
        return new GlyphSpec(BONDING_ID, new GridPoint(x, y), direction);
    }

    /**
     * {@code Unbonding}：两格之间存在互惠长期键时拆键并写下临时标记。
     *
     * <p>输入声明要求两格都有原子；是否真的成键由行为里的 {@link GlyphBoard#unbond} 裁决——
     * 两侧不构成互惠长期键时按“条件不满足就跳过”处理，不写临时标记。</p>
     */
    private static GlyphDefinition<Void> unbonding() {
        return GlyphDefinition.of(
                UNBONDING_ID,
                PAIR_PLACEHOLDER,
                PAIR_INPUT_SLOTS,
                List.of(),
                OMGlyphs::unbondPair);
    }

    public static GlyphSpec unbondingSpec(long x, long y, Direction direction) {
        return new GlyphSpec(UNBONDING_ID, new GridPoint(x, y), direction);
    }

    /**
     * 置于其上的{@link Elem}原子转化为{@link Salt}
     */
    private static GlyphDefinition<Void> calcification() {
        return GlyphDefinition.of(
                CALCIFICATION_ID,
                List.of(PAIR_PLACEHOLDER.get(0)),
                List.of(PAIR_INPUT_SLOTS.get(0)),
                List.of(),
                OMGlyphs::calcifies);
    }

    public static GlyphSpec calcificationSpec(long x, long y) {
        return new GlyphSpec(CALCIFICATION_ID, new GridPoint(x, y));
    }

    private static void bondPair(GlyphContext context) {
        GridPoint anchor = context.worldPoint(0);
        GridPoint neighbor = context.worldPoint(1);
        Direction direction = directionBetween(anchor, neighbor);
        if (direction == null) {
            return;
        }
        GlyphBoard board = context.board();
        if (board.atomAt(0).isEmpty() || board.atomAt(1).isEmpty()) {
            return;
        }
        board.bond(0, direction, OMBonds.NORMAL.code);
    }

    private static void unbondPair(GlyphContext context) {
        GridPoint anchor = context.worldPoint(0);
        GridPoint neighbor = context.worldPoint(1);
        Direction direction = directionBetween(anchor, neighbor);
        if (direction != null) {
            context.board().unbond(0, direction);
        }
    }

    private static void calcifies(GlyphContext context) {
        Optional<Atom> o1 = context.board().atomAt(0);
        if (o1.isEmpty()) return;

        Optional<OMAtom> o2 = OMAtom.of(o1.get().type());
        if (o2.isPresent() && o2.get() instanceof Elem) {
            context.board().setType(0, Salt.SALT.code);
        }
    }

    /** 锚点到邻居的世界单位步方向；局部 {@code (1,0)} 在任何朝向下都是单位步。 */
    private static Direction directionBetween(GridPoint from, GridPoint to) {
        long dx = to.x() - from.x();
        long dy = to.y() - from.y();

        if (dx > 0 && dy == 0) return Direction.EAST;
        if (dx < 0 && dy == 0) return Direction.WEST;
        if (dx == 0 && dy > 0) return Direction.SOUTH;
        if (dx == 0 && dy < 0) return Direction.NORTH;
        return null;
    }
}
