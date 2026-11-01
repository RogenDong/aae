package io.dev.aae;

import java.util.Objects;

/**
 * 计划里一个静态、带方向的符文实例声明。
 *
 * <p>与可移动的 {@link MechanismSpec} 分离：符文在计划构造时固定位置和朝向，运行期不再
 * 移动。占位用朝东状态下的方格局部坐标表达，{@code (0,0)} 是锚点，此时正 x 向东、正 y 向南。
 * 实例朝向决定绕锚点的旋转，旋转后正 x 沿实例朝向，正 y 沿其右侧；
 * 局部坐标到世界坐标的换算与 {@link Direction#localToWorld(GridPoint, GridPoint)}
 * 完全一致，由 {@link #worldPoint(GridPoint)} 完成。</p>
 *
 * @param id       计划内唯一的符文标识
 * @param typeId   {@code namespace:name} 形式的类型标识
 * @param position 锚点格点
 * @param direction 朝向；决定局部坐标到世界坐标的换算
 */
public record GlyphSpec(String id, String typeId, GridPoint position, Direction direction) {

    public GlyphSpec {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("glyph id must not be blank");
        }
        Objects.requireNonNull(typeId, "typeId");
        int separator = typeId.indexOf(':');
        if (separator <= 0
                || separator == typeId.length() - 1
                || typeId.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "glyph type id must use the namespace:name form: " + typeId);
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
    }

    /** 默认朝东，直接使用定义中的局部形状。 */
    public GlyphSpec(String id, String typeId, GridPoint position) {
        this(id, typeId, position, Direction.EAST);
    }

    /**
     * 把占位局部坐标换算成相对锚点的世界坐标偏移。
     *
     * @param direction   换算使用的朝向
     * @param localOffset 占位局部偏移，按朝东状态定义：正 x 向东、正 y 向南
     */
    public static GridPoint worldOffset(Direction direction, GridPoint localOffset) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(localOffset, "localOffset");
        return direction.localOffsetToWorld(localOffset);
    }

    /**
     * 把局部坐标换算成世界坐标偏移。
     *
     * <p>委托 {@link Direction#localOffsetToWorld(GridPoint)}：
     * 朝东时不旋转，其他朝向绕锚点旋转。整套换算只有那一份实现。</p>
     */
    public static GridPoint fromLocal(Direction direction, GridPoint localOffset) {
        return worldOffset(direction, localOffset);
    }

    /**
     * 把世界坐标偏移换算回局部坐标；{@link #fromLocal} 的逆变换。
     */
    public static GridPoint toLocal(Direction direction, GridPoint worldOffset) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(worldOffset, "worldOffset");
        return direction.worldOffsetToLocal(worldOffset);
    }

    /**
     * 把占位局部坐标按朝向换算成相对锚点的世界坐标偏移。
     *
     * <p>只依赖朝向，与 {@link #position} 无关；需要绝对格点时用
     * {@link #worldPoint(GridPoint)}。</p>
     */
    public GridPoint worldOffset(GridPoint localOffset) {
        return worldOffset(direction, localOffset);
    }

    /** 本声明朝向下的局部坐标换算后的世界格点。 */
    public GridPoint worldPoint(GridPoint localOffset) {
        Objects.requireNonNull(localOffset, "localOffset");
        return direction.localToWorld(position, localOffset);
    }

    @Override
    public String toString() {
        return "GlyphSpec[" + id + " " + typeId + " at " + position + " facing " + direction + "]";
    }
}
