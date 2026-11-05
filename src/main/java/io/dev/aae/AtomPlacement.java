package io.dev.aae;

import java.util.Objects;

/** 原子及其所在格点 */
public record AtomPlacement(GridPoint position, Atom atom) {

    public AtomPlacement {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(atom, "atom");
    }

    public static AtomPlacement of(long x, long y, Atom atom) {
        return new AtomPlacement(new GridPoint(x, y), atom);
    }

    /** 原子类型编码，原样返回 {@link Atom#type()} 的完整数值。 */
    public int type() {
        return atom.type();
    }

    /** 向指定方位移动到相邻格点，原子本身不变。 */
    public AtomPlacement translate(Direction direction) {
        return new AtomPlacement(position.translate(direction), atom);
    }
}
