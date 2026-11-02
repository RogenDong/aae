package io.dev.aae;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 输入/输出的不可变定义：一个按绝对格点排列的原子模板。
 */
public sealed abstract class IOSpec permits InputSpec, OutputSpec {
    protected String id;
    protected List<AtomPlacement> atoms;
    public IOSpec(String id, List<AtomPlacement> atoms) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        Objects.requireNonNull(atoms, "atoms");
        this.atoms = List.copyOf(atoms);
    }

    public String id() {
        return id;
    }

    public void id(String id) {
        this.id = id;
    }

    public List<AtomPlacement> atoms() {
        return atoms;
    }

    public void atoms(List<AtomPlacement> atoms) {
        this.atoms = List.copyOf(atoms);
    }

    /** 模板的原子数量。 */
    public int size() {
        return atoms.size();
    }
}
