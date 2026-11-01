package io.dev.aae;

import java.util.Objects;

/**
 * 计划内一个符文的运行实例：声明、解析到的定义，以及当前的实例状态。
 *
 * <p>可变状态只存在于 {@link #state()} 里，并且只属于这一个模拟器实例。</p>
 *
 * @param spec       计划里的声明
 * @param definition 计划构造时快照下来的定义
 * @param state      当前状态值
 * @param <S>        状态类型
 */
public record GlyphInstance<S>(
        GlyphSpec spec, GlyphDefinition<S> definition, S state) {

    public GlyphInstance {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(definition, "definition");
    }

    /** 该实例朝向下的局部坐标换算。 */
    public GridPoint worldPoint(GridPoint localOffset) {
        return spec.worldPoint(localOffset);
    }
}
