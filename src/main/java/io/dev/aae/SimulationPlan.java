package io.dev.aae;

import io.dev.aae.mech.MechanismSpec;

import io.dev.aae.glyph.GlyphDefinition;
import io.dev.aae.glyph.GlyphRegistry;
import io.dev.aae.glyph.GlyphSpec;
import io.dev.aae.track.TrackDirection;
import io.dev.aae.track.TrackSpec;

import java.util.*;

/**
 * 不可变的模拟定义，与可变的运行态相互分离。
 * 机构、输入、输出、符文各自按列表声明顺序使用从 0 开始的下标；
 * 下标只适用于当前计划，在运行、回滚和重置期间保持稳定。
 */
public final class SimulationPlan {

    private final List<MechanismSpec> mechanisms;
    private final List<TrackSpec> tracks;
    private final TrackNetwork trackNetwork;

    private final List<InputSpec> inputs;

    private final List<OutputSpec> outputs;

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

    /**
     * 完整构造器：机构、轨道、输入、输出、符文。建立定义快照后校验静态布局。
     *
     * @throws IllegalArgumentException I/O、符文与其他部件占位重叠；机构只计初始基座，
     *         机构之间不能共享基座格点，但机构可位于轨道上
     */
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

        // 按部件声明顺序检查实际占位格点。
        Map<GridPoint, String> occupants = new LinkedHashMap<>();
        for (int index = 0; index < mechanisms.size(); index++) {
            MechanismSpec mechanism = mechanisms.get(index);
            claimLayoutCell(occupants, mechanism.initialPosition(), "mechanism[" + index + "]");
        }
        for (int index = 0; index < tracks.size(); index++) {
            for (GridPoint point : tracks.get(index).points()) {
                // 机构可位于轨道上；跨轨道节点的唯一归属由 TrackNetwork 校验。
                occupants.putIfAbsent(point, "track[" + index + "]");
            }
        }
        for (int index = 0; index < inputs.size(); index++) {
            validateIOLayout(occupants, inputs.get(index), "input[" + index + "]");
        }
        for (int index = 0; index < outputs.size(); index++) {
            validateIOLayout(occupants, outputs.get(index), "output[" + index + "]");
        }

        this.mechanisms = List.copyOf(mechanisms);
        this.tracks = List.copyOf(tracks);
        this.trackNetwork = TrackNetwork.from(this.tracks);

        this.inputs = List.copyOf(inputs);

        this.outputs = List.copyOf(outputs);

        this.glyphs = List.copyOf(glyphs);
        if (this.glyphs.isEmpty()) {
            this.glyphDefinitions = Map.of();
        } else {
            this.glyphDefinitions = resolveDefinitions(this.glyphs);
        }

        for (int index = 0; index < this.glyphs.size(); index++) {
            GlyphSpec glyph = this.glyphs.get(index);
            for (GridPoint offset : glyphDefinition(glyph.typeId()).placeholder()) {
                claimLayoutCell(occupants, glyph.worldPoint(offset), "glyph[" + index + "]");
            }
        }

        if (targetOutputs < 1) {
            throw new IllegalArgumentException("targetOutputs must be at least 1");
        }
        this.targetOutputs = targetOutputs;
    }

    private static void validateIOLayout(Map<GridPoint, String> occupants, IOSpec io, String owner) {
        for (AtomPlacement placement : io.atoms()) {
            claimLayoutCell(occupants, placement.position(), owner);
        }
    }

    private static void claimLayoutCell(Map<GridPoint, String> occupants, GridPoint point, String owner) {
        String previous = occupants.putIfAbsent(point, owner);
        if (previous != null) {
            throw new IllegalArgumentException(owner + " overlaps " + previous + " at " + point);
        }
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
