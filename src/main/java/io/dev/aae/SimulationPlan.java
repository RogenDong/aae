package io.dev.aae;

import java.util.*;
import java.util.function.Function;

/** 不可变的模拟定义，与可变的运行态相互分离。 */
public final class SimulationPlan {

    private final List<MechanismSpec> mechanisms;
    private final Map<String, Integer> indexesById;
    private final List<TrackSpec> tracks;
    private final TrackNetwork trackNetwork;

    private final List<InputSpec> inputs;
    private final Map<String, Integer> inputIndexesById;

    private final List<OutputSpec> outputs;
    private final Map<String, Integer> outputIndexesById;

    private final List<GlyphSpec> glyphs;
    private final Map<String, GlyphDefinition<?>> glyphDefinitions;

    /** 每个输出必须达到的消费次数；全部输出达标时模拟完成。 */
    private final long targetOutputs;

    /** 只包含机构、没有输入输出的计划。 */
    public SimulationPlan(List<MechanismSpec> mechanisms) {
        this(mechanisms, List.of(), List.of(), List.of(), List.of(), 1);
    }

    /** 只包含机构与轨道、没有输入输出的计划。 */
    public SimulationPlan(List<MechanismSpec> mechanisms, List<TrackSpec> tracks) {
        this(mechanisms, tracks, List.of(), List.of(), List.of(), 1);
    }

    public SimulationPlan(
            List<MechanismSpec> mechanisms,
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            long targetOutputs) {
        this(mechanisms, List.of(), inputs, outputs, List.of(), targetOutputs);
    }

    public SimulationPlan(
            List<MechanismSpec> mechanisms,
            List<TrackSpec> tracks,
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            long targetOutputs) {
        this(mechanisms, tracks, inputs, outputs, List.of(), targetOutputs);
    }

    /** 带符文、但使用空轨道和空输入输出的计划。 */
    public SimulationPlan(
            List<MechanismSpec> mechanisms,
            List<GlyphSpec> glyphs,
            long targetOutputs) {
        this(mechanisms, List.of(), List.of(), List.of(), glyphs, targetOutputs);
    }

    /** 完整构造器：机构、轨道、输入、输出、符文 */
    public SimulationPlan(
            List<MechanismSpec> mechanisms,
            List<TrackSpec> tracks,
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            List<GlyphSpec> glyphs,
            long targetOutputs) {
        Objects.requireNonNull(mechanisms, "mechanisms");
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(glyphs, "glyphs");

        this.mechanisms = List.copyOf(mechanisms);
        this.indexesById = indexByIds("mechanism", this.mechanisms, MechanismSpec::id);
        this.tracks = List.copyOf(tracks);
        this.trackNetwork = TrackNetwork.from(this.tracks);

        this.inputs = List.copyOf(inputs);
        this.inputIndexesById = indexByIds("input", this.inputs, InputSpec::id);

        this.outputs = List.copyOf(outputs);
        this.outputIndexesById = indexByIds("output", this.outputs, OutputSpec::id);

        this.glyphs = List.copyOf(glyphs);
        indexByIds("glyph", this.glyphs, GlyphSpec::id);
        if (this.glyphs.isEmpty()) {
            this.glyphDefinitions = Map.of();
        } else {
            this.glyphDefinitions = resolveDefinitions(this.glyphs);
        }

        if (targetOutputs < 1) {
            throw new IllegalArgumentException("targetOutputs must be at least 1");
        }
        this.targetOutputs = targetOutputs;
    }

    /**
     * 把每个符文声明的类型解析成定义并快照下来。
     *
     * <p>快照之后注册表的变化（包括关闭注册句柄）都不再影响这个计划。</p>
     */
    private static Map<String, GlyphDefinition<?>> resolveDefinitions(List<GlyphSpec> glyphs) {
        Map<String, GlyphDefinition<?>> resolved = new LinkedHashMap<>();
        for (GlyphSpec glyph : glyphs) {
            GlyphDefinition<?> definition = GlyphRegistry.global().definition(glyph.typeId());
            if (definition == null) {
                throw new IllegalArgumentException("unknown glyph type: " + glyph.typeId());
            }
            resolved.put(glyph.typeId(), definition);
        }
        return resolved;
    }

    private static <T> Map<String, Integer> indexByIds(
            String kind, List<T> items, Function<T, String> idOf) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            T item = Objects.requireNonNull(items.get(index), kind + "s must not contain null");
            String id = Objects.requireNonNull(idOf.apply(item), "id");
            Integer previous = indexes.put(id, index);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate " + kind + " id: " + id);
            }
        }
        return indexes;
    }

    public static SimulationPlan of(MechanismSpec... mechanisms) {
        return new SimulationPlan(List.of(mechanisms));
    }

    public static SimulationPlan withTracks(
            List<TrackSpec> tracks, MechanismSpec... mechanisms) {
        return new SimulationPlan(List.of(mechanisms), tracks);
    }

    public List<MechanismSpec> mechanisms() {
        return mechanisms;
    }

    public List<TrackSpec> tracks() {
        return tracks;
    }

    /**
     * 返回从指定格点沿轨道方向走一步后的格点。当前位置没有对应出口时返回原格点。
     */
    public GridPoint trackDestination(GridPoint position, TrackDirection trackDirection) {
        return trackNetwork.destination(position, trackDirection);
    }

    public List<InputSpec> inputs() {
        return inputs;
    }

    public List<OutputSpec> outputs() {
        return outputs;
    }

    /** 计划里的符文声明，按声明顺序。 */
    public List<GlyphSpec> glyphs() {
        return glyphs;
    }

    /** 计划快照下来的符文定义，key 为类型 ID。 */
    public Map<String, GlyphDefinition<?>> glyphDefinitions() {
        return glyphDefinitions;
    }

    /**
     * 计划里全部符文声明引用到的类型 ID。
     *
     * <p>它与 {@link #glyphDefinitions()} 的 key 集合恒等：计划只快照自己用到的类型，
     * 因此“声明 ↔ 快照定义”是互相闭合的。注册句柄关闭或注册表新增类型都不会改变这份集合。</p>
     */
    public Set<String> glyphTypeIds() {
        return glyphDefinitions.keySet();
    }

    /**
     * 计划解析到的一个符文定义；计划里没有该类型时抛出
     * {@link IllegalArgumentException}。
     */
    public GlyphDefinition<?> glyphDefinition(String typeId) {
        GlyphDefinition<?> definition = glyphDefinitions.get(Objects.requireNonNull(typeId, "typeId"));
        if (definition == null) {
            throw new IllegalArgumentException("unknown glyph type in plan: " + typeId);
        }
        return definition;
    }

    /** 计划是否声明了任何符文。 */
    public boolean hasGlyphs() {
        return !glyphs.isEmpty();
    }

    public long targetOutputs() {
        return targetOutputs;
    }

    public int size() {
        return mechanisms.size();
    }

    int indexOf(String id) {
        return indexIn(indexesById, "mechanism", id);
    }

    int indexOfInput(String id) {
        return indexIn(inputIndexesById, "input", id);
    }

    int indexOfOutput(String id) {
        return indexIn(outputIndexesById, "output", id);
    }

    private static int indexIn(Map<String, Integer> indexes, String kind, String id) {
        Integer index = indexes.get(Objects.requireNonNull(id, "id"));
        if (index == null) {
            throw new IllegalArgumentException("unknown " + kind + " id: " + id);
        }
        return index;
    }

    /** 计划里全部符文占位格点，按声明顺序。 */
    public List<GridPoint> glyphPlaceholderCells() {
        List<GridPoint> cells = new ArrayList<>();
        for (GlyphSpec glyph : glyphs) {
            for (GridPoint offset : glyphDefinitions.get(glyph.typeId()).placeholder()) {
                cells.add(glyph.worldPoint(offset));
            }
        }
        return cells;
    }
}
