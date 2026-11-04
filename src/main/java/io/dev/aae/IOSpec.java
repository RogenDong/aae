package io.dev.aae;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 输入/输出的不可变定义：一个按绝对格点排列的原子模板。
 * 实例按计划 inputs() 或 outputs() 列表的声明下标定位，不保存实例 ID。
 */
public sealed abstract class IOSpec permits InputSpec, OutputSpec {
    protected List<AtomPlacement> atoms;
    public IOSpec(List<AtomPlacement> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        this.atoms = List.copyOf(atoms);
    }

    public List<AtomPlacement> atoms() {
        return Collections.unmodifiableList(atoms);
    }

    /** 模板的原子数量。 */
    public int size() {
        return atoms.size();
    }
}
