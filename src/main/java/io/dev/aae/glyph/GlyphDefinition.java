package io.dev.aae.glyph;

import io.dev.aae.Atom;
import io.dev.aae.Direction;
import io.dev.aae.GridPoint;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 一个符文类型的不可变定义：类型 ID、占位、输入/输出格点声明、实例状态工厂和行为。
 *
 * <p>定义对象必须可并发复用：占位与格点声明都是只读列表，工厂与行为必须无状态。任何实例
 * 可变数据只能存在于每个符文的状态值里，由模拟器按符文实例分别持有。</p>
 *
 * <p><b>前置条件由声明表达。</b>符文不需要在行为里反复检查“输入对不对、输出空不空”：
 * 把要求写成 {@link InputSlot} / {@link OutputSlot}，模拟器会在执行行为之前统一检查，
 * 任一项不满足就<b>整条符文跳过</b>（不抛异常、不产生副作用）。</p>
 *
 * <ul>
 *   <li>{@link InputSlot}：该占位索引必须有原子并满足期望；</li>
 *   <li>{@link OutputSlot}：该占位索引在行为执行之前必须空闲。</li>
 * </ul>
 *
 * @param typeId       {@code namespace:name} 形式的类型标识
 * @param placeholder  占位的局部坐标格点，至少一个；{@code (0,0)} 必须包含在内
 * @param inputs       输入格点声明，按声明顺序；没有输入要求时为空列表
 * @param outputs      输出格点声明，按声明顺序；没有输出要求时为空列表
 * @param stateFactory 每个符文实例的状态工厂；无状态实现用 {@code () -> null}
 * @param behavior     行为
 * @param <S>          实例状态类型
 */
public record GlyphDefinition<S>(
        String typeId,
        List<GridPoint> placeholder,
        List<InputSlot> inputs,
        List<OutputSlot> outputs,
        Supplier<S> stateFactory,
        GlyphBehavior<S> behavior) {

    public GlyphDefinition {
        Objects.requireNonNull(typeId, "typeId");
        int separator = typeId.indexOf(':');
        if (separator <= 0
                || separator == typeId.length() - 1
                || typeId.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "glyph type id must use the namespace:name form: " + typeId);
        }
        Objects.requireNonNull(placeholder, "placeholder");
        placeholder = List.copyOf(placeholder);
        if (placeholder.isEmpty()) {
            throw new IllegalArgumentException("glyph placeholder must not be empty: " + typeId);
        }
        Set<GridPoint> unique = new LinkedHashSet<>();
        for (GridPoint offset : placeholder) {
            Objects.requireNonNull(offset, "placeholder must not contain null");
            if (!unique.add(offset)) {
                throw new IllegalArgumentException(
                        "glyph placeholder contains duplicate offset " + offset + ": " + typeId);
            }
        }
        if (!unique.contains(new GridPoint(0, 0))) {
            throw new IllegalArgumentException(
                    "glyph placeholder must contain the origin (0,0): " + typeId);
        }
        inputs = distinctInputs(typeId, placeholder.size(), inputs);
        outputs = distinctOutputs(typeId, placeholder.size(), outputs);
        Set<Integer> inputCells = new LinkedHashSet<>();
        for (InputSlot slot : inputs) {
            inputCells.add(slot.position());
        }
        Set<Integer> outputCells = new LinkedHashSet<>();
        for (OutputSlot slot : outputs) {
            outputCells.add(slot.position());
        }
        if (!Collections.disjoint(inputCells, outputCells)) {
            throw new IllegalArgumentException(
                    "glyph input and output slots must not share a cell: " + typeId);
        }
        Objects.requireNonNull(stateFactory, "stateFactory");
        Objects.requireNonNull(behavior, "behavior");
    }

    /** 兼容构造器：只有占位、没有输入/输出声明的定义。 */
    public GlyphDefinition(
            String typeId,
            List<GridPoint> placeholder,
            Supplier<S> stateFactory,
            GlyphBehavior<S> behavior) {
        this(typeId, placeholder, List.of(), List.of(), stateFactory, behavior);
    }

    /** 无状态定义：给定占位与行为，状态工厂返回 {@code null}。 */
    public static GlyphDefinition<Void> of(
            String typeId, List<GridPoint> placeholder, StatelessBehavior behavior) {
        return of(typeId, placeholder, List.of(), List.of(), behavior);
    }

    /** 无状态定义：给定占位、输入输出声明与行为，状态工厂返回 {@code null}。 */
    public static GlyphDefinition<Void> of(
            String typeId,
            List<GridPoint> placeholder,
            List<InputSlot> inputs,
            List<OutputSlot> outputs,
            StatelessBehavior behavior) {
        Objects.requireNonNull(behavior, "behavior");
        GlyphBehavior<Void> stateful =
                (context, previousState) -> {
                    behavior.apply(context);
                    return null;
                };
        return new GlyphDefinition<>(typeId, placeholder, inputs, outputs, () -> null, stateful);
    }

    /** 无状态定义：占位只有一个锚点。 */
    public static GlyphDefinition<Void> single(String typeId, StatelessBehavior behavior) {
        return of(typeId, List.of(new GridPoint(0, 0)), behavior);
    }

    /** 占位的局部坐标格点，包含 {@code (0,0)}。 */
    @Override
    public List<GridPoint> placeholder() {
        return placeholder;
    }

    /** 输入格点声明，按声明顺序。 */
    @Override
    public List<InputSlot> inputs() {
        return inputs;
    }

    /** 输出格点声明，按声明顺序。 */
    @Override
    public List<OutputSlot> outputs() {
        return outputs;
    }

    /** 创建一份新的实例状态。 */
    public S newState() {
        return stateFactory.get();
    }

    /** 校验并解析当前定义的占位索引。 */
    public GridPoint placeholderAt(int index) {
        checkIndex(typeId, placeholder.size(), index);
        return placeholder.get(index);
    }

    private static void checkIndex(String typeId, int size, int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    "placeholder index " + index + ", count " + size + ", glyph type " + typeId);
        }
    }

    private static List<InputSlot> distinctInputs(String typeId, int size, List<InputSlot> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        List<InputSlot> copy = List.copyOf(inputs);
        Set<Integer> cells = new LinkedHashSet<>();
        for (InputSlot slot : copy) {
            Objects.requireNonNull(slot, "inputs must not contain null");
            checkIndex(typeId, size, slot.position());
            if (!cells.add(slot.position())) {
                throw new IllegalArgumentException(
                        "glyph input slots must not repeat a cell " + slot.position() + ": " + typeId);
            }
        }
        return copy;
    }

    private static List<OutputSlot> distinctOutputs(String typeId, int size, List<OutputSlot> outputs) {
        Objects.requireNonNull(outputs, "outputs");
        List<OutputSlot> copy = List.copyOf(outputs);
        Set<Integer> cells = new LinkedHashSet<>();
        for (OutputSlot slot : copy) {
            Objects.requireNonNull(slot, "outputs must not contain null");
            checkIndex(typeId, size, slot.position());
            if (!cells.add(slot.position())) {
                throw new IllegalArgumentException(
                        "glyph output slots must not repeat a cell " + slot.position() + ": " + typeId);
            }
        }
        return copy;
    }

    /**
     * 一个输入格点的期望值：该格点必须有原子，并满足可选的类型、键及自定义约束。
     *
     * @param position    placeholder 的 0-based 索引
     * @param requiredAtomType 可选的精确原子类型，范围 {@code 0..0xFFFF}；为空表示不限制类型，不能为 {@code null}
     * @param bondType    期望在该方向存在的键类型字节；{@code 0} 表示不作要求
     * @param direction   检查键位时使用的世界方向；{@code bondType == 0} 时忽略
     * @param expectation 额外的原子级判据；{@code null} 表示不作要求
     */
    public record InputSlot(
            int position,
            OptionalInt requiredAtomType,
            byte bondType,
            Direction direction,
            Predicate<Atom> expectation) {

        public InputSlot {
            Objects.requireNonNull(requiredAtomType, "requiredAtomType");
            requiredAtomType.ifPresent(Atom::requireAtomCode);
            if (bondType != 0) {
                Atom.requireBondCode(bondType);
                Objects.requireNonNull(direction, "direction");
            }
        }

        /**
         * 只有原子类型期望的输入格点
         */
        public InputSlot(int position, int atomType) {
            this(position, atomType, (byte) 0, Direction.NORTH, null);
        }

        /**
         * 原子类型 + 键类型期望的输入格点
         */
        public InputSlot(int position, int atomType, byte bondType, Direction direction) {
            this(position, atomType, bondType, direction, null);
        }

        /** 精确匹配原子类型，并附带可选的键要求和自定义判据。 */
        public InputSlot(
                int position, int atomType, byte bondType, Direction direction,
                Predicate<Atom> expectation) {
            this(position, OptionalInt.of(atomType), bondType, direction, expectation);
        }

        /** 精确匹配原子类型 {@code 0..0xFFFF} */
        public static InputSlot of(int position, int atomType) {
            return new InputSlot(position, atomType, (byte) 0, Direction.NORTH);
        }

        /** 精确匹配原子类型 {@code 0..0xFFFF}，并要求该世界方向上存在指定键。 */
        public static InputSlot bonded(
                int position, int atomType, Direction direction, byte bondType) {
            return new InputSlot(position, atomType, bondType, direction);
        }

        /** 只要求该格点有原子，不附加类型、键或自定义约束。 */
        public static InputSlot occupied(int position) {
            return new InputSlot(position, OptionalInt.empty(), (byte) 0, Direction.NORTH, null);
        }

        /** 该格点上的原子是否符合本声明的期望。 */
        public boolean matches(Atom atom) {
            if (atom == null) {
                return false;
            }
            if (requiredAtomType.isPresent() && atom.type() != requiredAtomType.getAsInt()) {
                return false;
            }
            if (expectation != null && !expectation.test(atom)) {
                return false;
            }
            return bondType == 0 || atom.bondType(direction) == bondType;
        }
    }

    /**
     * 一个输出格点的声明：行为执行之前该占位索引必须空闲。
     *
     * @param position placeholder 的 0-based 索引
     * @param checker  额外的“是否仍然空闲”判据；{@code null} 表示只看格点是否被占用
     */
    public record OutputSlot(int position, Predicate<GlyphReader> checker)
            implements Serializable {

        /** 只要求格点空闲。 */
        public static OutputSlot of(int position) {
            return new OutputSlot(position, null);
        }

        /**
         * 要求格点空闲，并且满足额外判据（例如“带键的原子也不能算空闲”）。
         *
         * <p>判据按值语义比较，因此实现应当可跨调用复用。</p>
         */
        public static OutputSlot freeWhen(int position, Predicate<GlyphReader> checker) {
            return new OutputSlot(position, Objects.requireNonNull(checker, "checker"));
        }

        /** 该占位索引当前是否满足“输出可用”。 */
        public boolean isFree(GlyphReader reader) {
            if (reader.isOccupied(position)) {
                return false;
            }
            return checker == null || checker.test(reader);
        }
    }

    /**
     * 只关心棋盘、不需要跨半周期状态的符文行为。
     *
     * <p>需要跨半周期状态的符文（例如转换类）改用 {@link GlyphBehavior} 直接实现。</p>
     */
    @FunctionalInterface
    public interface StatelessBehavior {

        /** 执行一个半周期。 */
        void apply(GlyphContext context) throws GlyphExecutionException;
    }
}
